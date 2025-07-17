package com.monzo;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis-backed implementation of the FrontierQueue abstraction.
 * <p>
 * Uses Redis LPUSH/BRPOP for queue operations and a Redis SET for atomic deduplication.
 * Designed for concurrency and production robustness.
 */
public class RedisFrontierQueue implements FrontierQueue {
    private static final String QUEUE_KEY = "frontier:queue";
    private static final String VISITED_SET_KEY = "frontier:visited";
    private static final int BRPOP_TIMEOUT = 5; // seconds

    private final JedisPool jedisPool;
    private final ExecutorService executor;

    /**
     * Creates a new RedisFrontierQueue with default host and port (localhost:6379).
     */
    public RedisFrontierQueue() {
        this("localhost", 6379);
    }

    /**
     * For testing purposes only - allows providing a pre-configured JedisPool and executor.
     *
     * @param jedisPool The JedisPool to use
     * @param executor The executor for async operations
     */
    RedisFrontierQueue(JedisPool jedisPool, ExecutorService executor) {
        this.jedisPool = jedisPool;
        this.executor = executor;
    }
    public RedisFrontierQueue(String host, int port) {
        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(32);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        this.jedisPool = new JedisPool(poolConfig, host, port);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public boolean push(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try (var jedis = jedisPool.getResource()) {
            // Atomic deduplication and enqueue using Lua script
            String luaScript = "if redis.call('SADD', KEYS[2], ARGV[1]) == 1 then return redis.call('LPUSH', KEYS[1], ARGV[1]) else return 0 end";
            Object result = jedis.eval(luaScript, 2, QUEUE_KEY, VISITED_SET_KEY, url);
            return Long.valueOf(1).equals(result);
        }
    }

    /**
     * Checks if a URL has already been visited.
     *
     * @param url The URL to check
     * @return true if visited, false otherwise
     */
    public boolean hasVisited(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try (var jedis = jedisPool.getResource()) {
            return jedis.sismember(VISITED_SET_KEY, url);
        }
    }

    @Override
    public String pop() {
        try (var jedis = jedisPool.getResource()) {
            return jedis.rpop(QUEUE_KEY);
        }
    }

    @Override
    public CompletableFuture<String> popAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try (var jedis = jedisPool.getResource()) {
                var result = jedis.brpop(BRPOP_TIMEOUT, QUEUE_KEY);
                if (result == null || result.size() < 2) {
                    return null;
                }
                return result.get(1);
            }
        }, executor);
    }

    /**
     * Returns the number of URLs in the visited set.
     *
     * @return The number of visited URLs
     */
    public long visitedCount() {
        try (var jedis = jedisPool.getResource()) {
            Long count = jedis.scard(VISITED_SET_KEY);
            return count != null ? count : 0;
        }
    }

    @Override
    public int size() {
        try (var jedis = jedisPool.getResource()) {
            Long length = jedis.llen(QUEUE_KEY);
            return length != null ? length.intValue() : 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public void clear() {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(QUEUE_KEY);
        }
    }

    /**
     * Clears both the queue and the visited set. Useful for testing or resetting the crawler.
     */
    public void clearAll() {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(QUEUE_KEY, VISITED_SET_KEY);
        }
    }

    /**
     * Closes the Redis connection pool.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}

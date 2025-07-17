package com.monzo;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis-backed implementation of the FrontierQueue abstraction.
 * <p>
 * Uses Redis LPUSH/BRPOP for queue operations and a Redis SET for atomic deduplication.
 * Designed for concurrency and production robustness.
 */
public class RedisFrontierQueue implements FrontierQueue {

    /**
     * Configuration constants, overridable via environment variables, initialised per instance.
     */
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_QUEUE_KEY = "frontier:queue";
    private static final String DEFAULT_VISITED_SET_KEY = "frontier:visited";
    private static final int DEFAULT_BRPOP_TIMEOUT = 5; // seconds
    private static final String LUA_DEDUP_SCRIPT = "if redis.call('SADD', KEYS[2], ARGV[1]) == 1 then return redis.call('LPUSH', KEYS[1], ARGV[1]) else return 0 end";

    private final String queueKey;
    private final String visitedSetKey;
    private final int brpopTimeout;

    private final JedisPool jedisPool;
    private final ExecutorService executor;
    private final AtomicReference<String> dedupScriptSha1 = new AtomicReference<>();

    /**
     * Creates a new RedisFrontierQueue with default configuration (localhost:6379, default keys).
     */
    public RedisFrontierQueue() {
        this(buildConfigFromEnv());
    }

    /**
     * Constructs a RedisFrontierQueue using the provided configuration.
     * @param config Configuration object
     */
    public RedisFrontierQueue(RedisConfig config) {
        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(32);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        this.jedisPool = new JedisPool(poolConfig, config.getHost(), config.getPort());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.queueKey = config.getQueueKey();
        this.visitedSetKey = config.getVisitedSetKey();
        this.brpopTimeout = config.getBrpopTimeout();
        // Load Lua script and cache SHA1
        try (var jedis = jedisPool.getResource()) {
            dedupScriptSha1.set(jedis.scriptLoad(LUA_DEDUP_SCRIPT));
        } catch (Exception e) {
            dedupScriptSha1.set(null);
        }
    }

    /**
     * For testing purposes only - allows providing a pre-configured JedisPool and executor.
     */
    RedisFrontierQueue(JedisPool jedisPool, ExecutorService executor) {
        this.jedisPool = jedisPool;
        this.executor = executor;
        this.queueKey = DEFAULT_QUEUE_KEY;
        this.visitedSetKey = DEFAULT_VISITED_SET_KEY;
        this.brpopTimeout = DEFAULT_BRPOP_TIMEOUT;
    }

    /**
     * Ensures the Lua script is loaded and returns the SHA1.
     * If not loaded, loads it. If Redis lost the script, reloads it.
     * @param jedis Jedis connection
     * @return SHA1 of the loaded script
     */
    private String ensureLuaScriptLoaded(redis.clients.jedis.Jedis jedis) {
        if (dedupScriptSha1.get() == null) {
            dedupScriptSha1.set(jedis.scriptLoad(LUA_DEDUP_SCRIPT));
        }
        return dedupScriptSha1.get();
    }

    private static RedisConfig buildConfigFromEnv() {
        return new RedisConfig()
            .withHost(System.getenv().getOrDefault("MDC_REDIS_HOST", DEFAULT_REDIS_HOST))
            .withPort(parseIntOrDefault(System.getenv("MDC_REDIS_PORT"), DEFAULT_REDIS_PORT))
            .withQueueKey(System.getenv().getOrDefault("MDC_QUEUE_KEY", DEFAULT_QUEUE_KEY))
            .withVisitedSetKey(System.getenv().getOrDefault("MDC_VISITED_SET_KEY", DEFAULT_VISITED_SET_KEY))
            .withBrpopTimeout(parseIntOrDefault(System.getenv("MDC_BRPOP_TIMEOUT"), DEFAULT_BRPOP_TIMEOUT));
    }

    /**
     * Parses an integer from a string, returning a default if parsing fails.
     * @param value The string to parse
     * @param defaultValue The default value to use if parsing fails
     * @return The parsed integer or the default
     */
    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean push(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try (var jedis = jedisPool.getResource()) {
            String sha1 = ensureLuaScriptLoaded(jedis);
            try {
                Object result = jedis.evalsha(sha1, 2, queueKey, visitedSetKey, url);
                return Long.valueOf(1).equals(result);
            } catch (redis.clients.jedis.exceptions.JedisNoScriptException e) {
                // Script was flushed from Redis, reload and retry
                sha1 = jedis.scriptLoad(LUA_DEDUP_SCRIPT);
                dedupScriptSha1.set(sha1);
                Object result = jedis.evalsha(sha1, 2, queueKey, visitedSetKey, url);
                return Long.valueOf(1).equals(result);
            }
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
            return jedis.sismember(visitedSetKey, url);
        }
    }

    @Override
    public String pop() {
        try (var jedis = jedisPool.getResource()) {
            return jedis.rpop(queueKey);
        }
    }

    @Override
    public CompletableFuture<String> popAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try (var jedis = jedisPool.getResource()) {
                var result = jedis.brpop(brpopTimeout, queueKey);
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
            Long count = jedis.scard(visitedSetKey);
            return count != null ? count : 0;
        }
    }

    @Override
    public int size() {
        try (var jedis = jedisPool.getResource()) {
            Long length = jedis.llen(queueKey);
            return length != null ? length.intValue() : 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Clears only the queue, leaving the visited set intact.
     * Useful for resetting the queue while preserving deduplication.
     */
    @Override
    public void clear() {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(queueKey);
        }
    }

    /**
     * Clears both the queue and the visited set.
     * Useful for full reset, including deduplication history.
     */
    public void clearAll() {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(queueKey, visitedSetKey);
        }
    }

    /**
     * Closes the Redis connection pool and shuts down the executor service.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

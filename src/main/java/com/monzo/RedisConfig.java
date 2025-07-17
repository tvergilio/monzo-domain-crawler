package com.monzo;

/**
 * Configuration for RedisFrontierQueue.
 * <p>
 * Allows customisation of Redis connection, queue keys, and blocking timeout.
 * Use builder-style methods for fluent configuration.
 */
public class RedisConfig {
    private String host = "localhost";
    private int port = 6379;
    private String queueKey = "frontier:queue";
    private String visitedSetKey = "frontier:visited";
    private int brpopTimeout = 5;

    public RedisConfig() {
    }

    public RedisConfig(String host, int port) {
        if (host == null) {
            throw new IllegalArgumentException("Redis host cannot be null");
        }
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getQueueKey() {
        return queueKey;
    }

    public String getVisitedSetKey() {
        return visitedSetKey;
    }

    public int getBrpopTimeout() {
        return brpopTimeout;
    }

    public RedisConfig withHost(String host) {
        this.host = host;
        return this;
    }

    public RedisConfig withPort(int port) {
        this.port = port;
        return this;
    }

    public RedisConfig withQueueKey(String key) {
        this.queueKey = key;
        return this;
    }

    public RedisConfig withVisitedSetKey(String key) {
        this.visitedSetKey = key;
        return this;
    }

    public RedisConfig withBrpopTimeout(int timeout) {
        this.brpopTimeout = timeout;
        return this;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}

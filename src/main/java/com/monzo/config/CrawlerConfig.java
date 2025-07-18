package com.monzo.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable configuration for the crawler. */
public final class CrawlerConfig {

    /* ------------------------------------------------------------------ *
     *  Static factories                                                  *
     * ------------------------------------------------------------------ */

    private static final String DEFAULT_YAML = "crawler-config.yaml";

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builderFromYaml() {
        return loadBuilderFromYaml(DEFAULT_YAML);
    }

    public static Builder builderFromYaml(String source) {
        return loadBuilderFromYaml(source);
    }

    /* ------------------------------------------------------------------ *
     *  Accessors                                                         *
     * ------------------------------------------------------------------ */

    public String getStartUrl() {
        return startUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getBackoffBaseMs() {
        return backoffBaseMs;
    }

    public int getBackoffMaxMs() {
        return backoffMaxMs;
    }

    public int getBackoffJitterMs() {
        return backoffJitterMs;
    }

    public int getBackoffRetries() {
        return backoffRetries;
    }

    public int getRobotsTimeoutMs() {
        return robotsTimeoutMs;
    }

    public RedisConfig getRedisConfig() {
        return redisConfig;
    }

    /* ------------------------------------------------------------------ *
     *  Construction                                                      *
     * ------------------------------------------------------------------ */

    private CrawlerConfig(Builder b) {
        startUrl        = b.startUrl;
        timeoutMs       = b.timeoutMs;
        concurrency     = b.concurrency;
        maxDepth        = b.maxDepth;
        backoffBaseMs   = b.backoffBaseMs;
        backoffMaxMs    = b.backoffMaxMs;
        backoffJitterMs = b.backoffJitterMs;
        backoffRetries  = b.backoffRetries;
        robotsTimeoutMs = b.robotsTimeoutMs;
        redisConfig     = b.redisConfig;
    }

    /* ------------------------------------------------------------------ *
     *  Fields                                                            *
     * ------------------------------------------------------------------ */

    private final String      startUrl;
    private final int         timeoutMs;
    private final int         concurrency;
    private final int         maxDepth;
    private final int         backoffBaseMs;
    private final int         backoffMaxMs;
    private final int         backoffJitterMs;
    private final int         backoffRetries;
    private final int         robotsTimeoutMs;
    private final RedisConfig redisConfig;

    /* ------------------------------------------------------------------ *
     *  YAML helpers                                                      *
     * ------------------------------------------------------------------ */

    static Builder loadBuilderFromYaml(String source) {
        var yaml = new Yaml(new Constructor(CrawlerConfigYaml.class, new LoaderOptions()));

        try (InputStream in = open(source)) {
            if (in == null) {
                throw new IllegalStateException("Configuration file not found: " + source);
            }

            CrawlerConfigYaml config = yaml.load(in);
            if (config == null) {
                throw new IllegalStateException("Configuration file is empty or invalid: " + source);
            }

            return new Builder()
                    .setStartUrl(config.startUrl)
                    .setTimeoutMs(config.timeoutMs)
                    .setConcurrency(config.concurrency)
                    .setMaxDepth(config.maxDepth)
                    .setBackoffBaseMs(config.backoffBaseMs)
                    .setBackoffMaxMs(config.backoffMaxMs)
                    .setBackoffJitterMs(config.backoffJitterMs)
                    .setBackoffRetries(config.backoffRetries)
                    .setRobotsTimeoutMs(config.robotsTimeoutMs)
                    .setRedisConfig(new RedisConfig(config.redis.host, config.redis.port));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration: " + e.getMessage(), e);
        }
    }

    private static InputStream open(String source) throws Exception {
        var path = Path.of(source);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        } else {
            return CrawlerConfig.class.getClassLoader().getResourceAsStream(source);
        }
    }

    /* ------------------------------------------------------------------ *
     *  Builder                                                           *
     * ------------------------------------------------------------------ */

    public static final class Builder {
        private String      startUrl;
        private int         timeoutMs = 5000;
        private int         concurrency = 4;
        private int         maxDepth = 3;
        private int         backoffBaseMs = 1000;
        private int         backoffMaxMs = 10000;
        private int         backoffJitterMs = 500;
        private int         backoffRetries = 4;
        private int         robotsTimeoutMs = 5000;
        private RedisConfig redisConfig;

        public Builder setStartUrl(String v) {
            startUrl = v;
            return this;
        }

        public Builder setTimeoutMs(int v) {
            timeoutMs = v;
            return this;
        }

        public Builder setConcurrency(int v) {
            concurrency = v;
            return this;
        }

        public Builder setMaxDepth(int v) {
            maxDepth = v;
            return this;
        }

        public Builder setBackoffBaseMs(int v) {
            backoffBaseMs = v;
            return this;
        }

        public Builder setBackoffMaxMs(int v) {
            backoffMaxMs = v;
            return this;
        }

        public Builder setBackoffJitterMs(int v) {
            backoffJitterMs = v;
            return this;
        }

        public Builder setBackoffRetries(int v) {
            backoffRetries = v;
            return this;
        }

        public Builder setRobotsTimeoutMs(int v) {
            robotsTimeoutMs = v;
            return this;
        }

        public Builder setRedisConfig(RedisConfig v) {
            redisConfig = v;
            return this;
        }

        public CrawlerConfig build() {
            if (startUrl == null) {
                throw new IllegalArgumentException("startUrl must not be null");
            }
            if (redisConfig == null) {
                throw new IllegalArgumentException("redisConfig must not be null");
            }
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            if (concurrency <= 0) {
                throw new IllegalArgumentException("Concurrency must be positive");
            }
            if (maxDepth <= 0) {
                throw new IllegalArgumentException("Max depth must be positive");
            }
            if (backoffBaseMs <= 0) {
                throw new IllegalArgumentException("backoffBaseMs must be positive");
            }
            if (backoffMaxMs <= 0) {
                throw new IllegalArgumentException("backoffMaxMs must be positive");
            }
            if (backoffBaseMs > backoffMaxMs) {
                throw new IllegalArgumentException("backoffBaseMs must be less than or equal to backoffMaxMs");
            }
            if (backoffJitterMs < 0) {
                throw new IllegalArgumentException("backoffJitterMs must be non-negative");
            }
            if (backoffRetries < 1) {
                throw new IllegalArgumentException("backoffRetries must be at least 1");
            }
            if (robotsTimeoutMs <= 0) {
                throw new IllegalArgumentException("robotsTimeoutMs must be positive");
            }
            return new CrawlerConfig(this);
        }
    }

    /* ------------------------------------------------------------------ *
     *  YAML DTO                                                          *
     * ------------------------------------------------------------------ */

    @SuppressWarnings("unused")
    public static final class CrawlerConfigYaml {
        public String startUrl;
        public int    timeoutMs;
        public int    concurrency;
        public int    maxDepth;
        public int    backoffBaseMs;
        public int    backoffMaxMs;
        public int    backoffJitterMs;
        public int    backoffRetries = 5;
        public int    robotsTimeoutMs = 5000;
        public RedisYaml redis;

        public static final class RedisYaml {
            public String host;
            public int    port;
        }
    }

}

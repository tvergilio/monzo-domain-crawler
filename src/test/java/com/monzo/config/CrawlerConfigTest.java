package com.monzo.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class CrawlerConfigTest {
    @Test
    void backoffRetriesMustBePositive() {
        var redis = new RedisConfig("localhost", 6379);
        assertThrows(IllegalArgumentException.class, () ->
            new CrawlerConfig.Builder()
                .setStartUrl("https://example.com")
                .setTimeoutMs(1000)
                .setConcurrency(1)
                .setMaxDepth(1)
                .setRedisConfig(redis)
                .setBackoffBaseMs(1000)
                .setBackoffMaxMs(10000)
                .setBackoffJitterMs(500)
                .setBackoffRetries(0)
                .build(), "Should throw for zero backoffRetries");

        assertThrows(IllegalArgumentException.class, () ->
            new CrawlerConfig.Builder()
                .setStartUrl("https://example.com")
                .setTimeoutMs(1000)
                .setConcurrency(1)
                .setMaxDepth(1)
                .setRedisConfig(redis)
                .setBackoffBaseMs(1000)
                .setBackoffMaxMs(10000)
                .setBackoffJitterMs(500)
                .setBackoffRetries(-1)
                .build(), "Should throw for negative backoffRetries");
    }

    @Test
    void timeoutMustBePositive() {
        var redis = new RedisConfig("localhost", 6379);
        var url   = "https://example.com";

        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builder()
                                   .setStartUrl(url)
                                   .setTimeoutMs(0)
                                   .setConcurrency(2)
                                   .setMaxDepth(3)
                                   .setRedisConfig(redis)
                                   .build());

        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builder()
                                   .setStartUrl(url)
                                   .setTimeoutMs(-1)
                                   .setConcurrency(2)
                                   .setMaxDepth(3)
                                   .setRedisConfig(redis)
                                   .build());
    }

    @Test
    void concurrencyMustBePositive() {
        var redis = new RedisConfig("localhost", 6379);
        var url   = "https://example.com";

        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builder()
                                   .setStartUrl(url)
                                   .setTimeoutMs(1_000)
                                   .setConcurrency(0)
                                   .setMaxDepth(3)
                                   .setRedisConfig(redis)
                                   .build());

        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builder()
                                   .setStartUrl(url)
                                   .setTimeoutMs(1_000)
                                   .setConcurrency(-1)
                                   .setMaxDepth(3)
                                   .setRedisConfig(redis)
                                   .build());
    }

    @Test
    void maxDepthMustBePositive() {
        var redis = new RedisConfig("localhost", 6379);
        var url   = "https://example.com";

        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builder()
                                   .setStartUrl(url)
                                   .setTimeoutMs(1_000)
                                   .setConcurrency(2)
                                   .setMaxDepth(0)
                                   .setRedisConfig(redis)
                                   .build());

        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builder()
                                   .setStartUrl(url)
                                   .setTimeoutMs(1_000)
                                   .setConcurrency(2)
                                   .setMaxDepth(-5)
                                   .setRedisConfig(redis)
                                   .build());
    }

    @Test
    void loadsDefaultsFromYaml() {
        var cfg = CrawlerConfig.builderFromYaml().build();
        // Assert backoff fields loaded from YAML defaults
        assertEquals(100, cfg.getBackoffBaseMs(), "backoffBaseMs should be loaded from YAML defaults");
        assertEquals(10000, cfg.getBackoffMaxMs(), "backoffMaxMs should be loaded from YAML defaults");
        assertEquals(500, cfg.getBackoffJitterMs(), "backoffJitterMs should be loaded from YAML defaults");
        assertEquals(3, cfg.getBackoffRetries(), "backoffRetries should be loaded from YAML defaults");
        assertEquals("https://en.wikipedia.org", cfg.getStartUrl());
        assertEquals(10_000, cfg.getTimeoutMs());
        assertEquals(8,       cfg.getConcurrency());
        assertEquals(5,       cfg.getMaxDepth());
        assertEquals("localhost", cfg.getRedisConfig().getHost());
        assertEquals(6379,        cfg.getRedisConfig().getPort());
    }

    @Test
    void allowsCustomStartUrl() {
        var cfg = CrawlerConfig.builderFromYaml()
                               .setStartUrl("https://example.com")
                               .build();

        assertEquals("https://example.com", cfg.getStartUrl());
    }

    @Test
    void allowsCustomRedisConfig() {
        var cfg = CrawlerConfig.builderFromYaml()
                               .setStartUrl("https://example.com")
                               .setRedisConfig(new RedisConfig("redis-server", 6380))
                               .build();

        assertEquals("redis-server", cfg.getRedisConfig().getHost());
        assertEquals(6380,           cfg.getRedisConfig().getPort());
    }

    @Test
    void rejectsNullStartUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builderFromYaml()
                                   .setStartUrl(null)
                                   .build());
    }

    @Test
    void rejectsNullRedisConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfig.builderFromYaml()
                                   .setRedisConfig(null)
                                   .build());
    }

    @Test
    void failsWhenYamlMissing() {
        var ex = assertThrows(RuntimeException.class, () -> invokeLoad("nonexistent.yaml"));
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    void failsWhenYamlEmpty() throws Exception {
        var temp = Files.createTempFile("empty", ".yaml");
        try {
            var ex = assertThrows(RuntimeException.class, () -> invokeLoad(temp.toString()));
            assertTrue(ex.getCause() instanceof IllegalStateException);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void failsWhenYamlMalformed() throws Exception {
        var temp = Files.createTempFile("malformed", ".yaml");
        Files.writeString(temp, "key1: value1\nkey2 value2\n:bad", StandardCharsets.UTF_8);

        try {
            var ex = assertThrows(RuntimeException.class, () -> invokeLoad(temp.toString()));
            var cause = ex.getCause();
            assertTrue(cause instanceof IllegalStateException
                    || cause instanceof org.yaml.snakeyaml.error.YAMLException
                    || cause instanceof java.io.IOException);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Object invokeLoad(String path) throws Exception {
        Method m = CrawlerConfig.class.getDeclaredMethod("loadBuilderFromYaml", String.class);
        m.setAccessible(true);
        try {
            return m.invoke(null, path);
        } catch (InvocationTargetException ite) {
            throw (Exception) ite.getTargetException();
        }
    }
}

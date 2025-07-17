package com.monzo.crawler;

import org.junit.jupiter.api.*;

import com.monzo.config.CrawlerConfig;
import com.monzo.config.RedisConfig;
import com.monzo.queue.RedisFrontierQueue;

import static org.junit.jupiter.api.Assertions.*;

class DomainCrawlerTest {

    private final String originalRedisHost = System.getenv("REDIS_HOST");
    private final String originalRedisPort = System.getenv("REDIS_PORT");

    @BeforeEach
    void clearEnv() {
        if (originalRedisHost != null) System.clearProperty("REDIS_HOST");
        if (originalRedisPort != null) System.clearProperty("REDIS_PORT");
    }

    @AfterEach
    void restoreEnv() {
        if (originalRedisHost != null) System.setProperty("REDIS_HOST", originalRedisHost);
        if (originalRedisPort != null) System.setProperty("REDIS_PORT", originalRedisPort);
    }

    @Test
    void defaultInitialisation() {
        var config = CrawlerConfig.builderFromYaml().build();
        var redisConfig = config.getRedisConfig();
        var frontier = new RedisFrontierQueue(redisConfig);
        var domainCrawler = new DomainCrawler(config, frontier);
        assertEquals("https://en.wikipedia.org", domainCrawler.getStartUrl());
        assertEquals("localhost", redisConfig.getHost());
        assertEquals(6379, redisConfig.getPort());
    }

    @Test
    void customStartUrl() {
        var url = "https://example.com";
        var config = CrawlerConfig.builderFromYaml().setStartUrl(url).build();
        var redisConfig = config.getRedisConfig();
        var frontier = new RedisFrontierQueue(redisConfig);
        var domainCrawler = new DomainCrawler(config, frontier);
        assertEquals(url, domainCrawler.getStartUrl());
        assertEquals("localhost", redisConfig.getHost());
        assertEquals(6379, redisConfig.getPort());
    }

    @Test
    void customConfiguration() {
        var cfg = CrawlerConfig.builder()
                               .setStartUrl("https://example.com")
                               .setTimeoutMs(10_000)
                               .setConcurrency(8)
                               .setMaxDepth(5)
                               .setRedisConfig(new RedisConfig("redis-server", 6380))
                               .build();
        var frontier = new RedisFrontierQueue(cfg.getRedisConfig());
        var domainCrawler = new DomainCrawler(cfg, frontier);
        assertEquals("https://example.com", domainCrawler.getStartUrl());
        var redis = domainCrawler.getConfig().getRedisConfig();
        assertEquals("redis-server", redis.getHost());
        assertEquals(6380, redis.getPort());
    }
}

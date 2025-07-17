package com.monzo.crawler;

import com.monzo.config.CrawlerConfig;
import com.monzo.config.RedisConfig;
import com.monzo.queue.RedisFrontierQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the DomainCrawler class, focusing on its initial configuration.
 */
@DisplayName("DomainCrawler")
class DomainCrawlerTest {

    @Nested
    @DisplayName("when initialised from YAML config")
    class WhenInitialisedFromYaml {

        @Test
        @DisplayName("loads default values correctly")
        void loadsDefaultValues() {
            // Arrange
            var config = CrawlerConfig.builderFromYaml().build();
            var frontier = new RedisFrontierQueue(config.getRedisConfig());
            var domainCrawler = new DomainCrawler(config, frontier);

            // Assert
            var redisConfig = domainCrawler.getConfig().getRedisConfig();
            assertAll("Default configuration from YAML",
                () -> assertEquals("https://en.wikipedia.org", domainCrawler.getStartUrl(), "Should load default start URL"),
                () -> assertEquals("localhost", redisConfig.getHost(), "Should load default Redis host"),
                () -> assertEquals(6379, redisConfig.getPort(), "Should load default Redis port")
            );
        }

        @Test
        @DisplayName("allows overriding the start URL")
        void allowsOverridingStartUrl() {
            // Arrange
            var customUrl = "https://example.com";
            var config = CrawlerConfig.builderFromYaml().setStartUrl(customUrl).build();
            var frontier = new RedisFrontierQueue(config.getRedisConfig());
            var domainCrawler = new DomainCrawler(config, frontier);

            // Assert
            assertEquals(customUrl, domainCrawler.getStartUrl(), "Start URL should be the overridden value");
        }
    }

    @Nested
    @DisplayName("when initialised with a custom builder")
    class WhenInitialisedWithCustomBuilder {

        @Test
        @DisplayName("uses all provided custom configuration")
        void usesCustomConfiguration() {
            // Arrange
            var config = CrawlerConfig.builder()
                .setStartUrl("https://example.com")
                .setTimeoutMs(10_000)
                .setConcurrency(8)
                .setMaxDepth(5)
                .setRedisConfig(new RedisConfig("redis-server", 6380))
                .build();
            var frontier = new RedisFrontierQueue(config.getRedisConfig());
            var domainCrawler = new DomainCrawler(config, frontier);

            // Assert
            var redisConfig = domainCrawler.getConfig().getRedisConfig();
            assertAll("Custom configuration from builder",
                () -> assertEquals("https://example.com", domainCrawler.getStartUrl()),
                () -> assertEquals("redis-server", redisConfig.getHost()),
                () -> assertEquals(6380, redisConfig.getPort())
            );
        }
    }
}

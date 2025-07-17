package com.monzo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrawlerConfig Tests")
class CrawlerConfigTest {

    private CrawlerConfig.Builder aValidBuilder() {
        return CrawlerConfig.builder()
            .setStartUrl("https://example.com")
            .setTimeoutMs(1000)
            .setConcurrency(1)
            .setMaxDepth(1)
            .setRedisConfig(new RedisConfig("localhost", 6379));
    }

    @Nested
    @DisplayName("Builder Validation")
    class BuilderValidation {

        @ParameterizedTest(name = "rejects non-positive value for {0}")
        @MethodSource("com.monzo.config.CrawlerConfigTest#positiveIntegerConfigProvider")
        void rejectsNonPositiveIntegerValues(String propertyName, BiConsumer<CrawlerConfig.Builder, Integer> setter) {
            // Test with 0
            CrawlerConfig.Builder builderZero = aValidBuilder();
            setter.accept(builderZero, 0);
            assertThrows(IllegalArgumentException.class, builderZero::build,
                "Should throw for " + propertyName + " = 0");

            // Test with -1
            CrawlerConfig.Builder builderNegative = aValidBuilder();
            setter.accept(builderNegative, -1);
            assertThrows(IllegalArgumentException.class, builderNegative::build,
                "Should throw for " + propertyName + " = -1");
        }

        @Test
        @DisplayName("rejects null required objects")
        void rejectsNulls() {
            assertThrows(IllegalArgumentException.class, () -> aValidBuilder().setStartUrl(null).build());
            assertThrows(IllegalArgumentException.class, () -> aValidBuilder().setRedisConfig(null).build());
        }
    }

    @Nested
    @DisplayName("YAML Configuration Loading")
    class YamlLoading {

        protected CrawlerConfig.Builder invokeLoad(String path) {
            return CrawlerConfig.loadBuilderFromYaml(path);
        }

        @Test
        @DisplayName("loads configuration from default YAML")
        void loadsDefaultsFromYaml() {
            CrawlerConfig cfg = CrawlerConfig.builderFromYaml().build();

            assertAll("Default YAML values should be loaded correctly",
                () -> assertEquals("https://en.wikipedia.org", cfg.getStartUrl()),
                () -> assertEquals(10_000, cfg.getTimeoutMs()),
                () -> assertEquals(8, cfg.getConcurrency()),
                () -> assertEquals(5, cfg.getMaxDepth()),
                () -> assertEquals("localhost", cfg.getRedisConfig().getHost()),
                () -> assertEquals(6379, cfg.getRedisConfig().getPort()),
                () -> assertEquals(100, cfg.getBackoffBaseMs()),
                () -> assertEquals(10000, cfg.getBackoffMaxMs()),
                () -> assertEquals(500, cfg.getBackoffJitterMs()),
                () -> assertEquals(3, cfg.getBackoffRetries())
            );
        }

        @Test
        @DisplayName("allows overriding YAML values")
        void allowsOverrides() {
            RedisConfig customRedis = new RedisConfig("redis-server", 6380);
            CrawlerConfig cfg = CrawlerConfig.builderFromYaml()
                .setStartUrl("https://monzo.com")
                .setRedisConfig(customRedis)
                .build();

            assertAll("Values should be overridden",
                () -> assertEquals("https://monzo.com", cfg.getStartUrl()),
                () -> assertEquals("redis-server", cfg.getRedisConfig().getHost()),
                () -> assertEquals(6380, cfg.getRedisConfig().getPort())
            );
        }

        @Test
        @DisplayName("fails when specified YAML is missing")
        void failsWhenYamlMissing() {
            Exception ex = assertThrows(Exception.class, () -> invokeLoad("nonexistent.yaml"));
            assertInstanceOf(IllegalStateException.class, ex.getCause(), "Cause should be IllegalStateException for missing file");
        }
    }

    // Provides arguments for the parameterised validation test
    static Stream<Arguments> positiveIntegerConfigProvider() {
        return Stream.of(
            Arguments.of("timeoutMs", (BiConsumer<CrawlerConfig.Builder, Integer>) CrawlerConfig.Builder::setTimeoutMs),
            Arguments.of("concurrency", (BiConsumer<CrawlerConfig.Builder, Integer>) CrawlerConfig.Builder::setConcurrency),
            Arguments.of("maxDepth", (BiConsumer<CrawlerConfig.Builder, Integer>) CrawlerConfig.Builder::setMaxDepth),
            Arguments.of("backoffRetries", (BiConsumer<CrawlerConfig.Builder, Integer>) CrawlerConfig.Builder::setBackoffRetries)
        );
    }
}
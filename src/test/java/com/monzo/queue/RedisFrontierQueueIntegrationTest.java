package com.monzo.queue;

import com.monzo.config.RedisConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RedisFrontierQueue using Testcontainers.
 */
@Testcontainers
@Tag("integration")
@DisplayName("RedisFrontierQueue Integration Tests")
class RedisFrontierQueueIntegrationTest {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private RedisFrontierQueue queue;
    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig(redis.getHost(), redis.getMappedPort(6379));
        queue = new RedisFrontierQueue(redisConfig);
        queue.clearAll(); // Ensure a clean state before each test
    }

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.close();
        }
    }

    @Nested
    @DisplayName("when handling URLs")
    class UrlHandling {

        @Test
        @DisplayName("should add a new URL and mark it as visited")
        void shouldAddNewUrl() {
            var url = "https://example.com/page1";
            assertTrue(queue.push(url), "First push of a URL should succeed");
            assertAll("After pushing one URL",
                () -> assertEquals(1, queue.size(), "Queue size should be 1"),
                () -> assertEquals(1, queue.visitedCount(), "Visited count should be 1"),
                () -> assertTrue(queue.hasVisited(url), "URL should be marked as visited")
            );
        }

        @Test
        @DisplayName("should not add a duplicate URL")
        void shouldDeduplicateUrls() {
            var url = "https://example.com/page2";
            queue.push(url); // Initial push

            assertFalse(queue.push(url), "Second push of the same URL should be rejected");
            assertAll("After pushing a duplicate URL",
                () -> assertEquals(1, queue.size(), "Queue size should remain 1"),
                () -> assertEquals(1, queue.visitedCount(), "Visited count should remain 1")
            );
        }

        @Test
        @DisplayName("should pop a URL from the queue but keep it in the visited set")
        void popShouldPreserveVisitedStatus() {
            var url = "https://example.com/page3";
            queue.push(url);

            var poppedUrl = queue.pop();

            assertEquals(url, poppedUrl, "Popped URL should match the one pushed");
            assertAll("After popping a URL",
                () -> assertEquals(0, queue.size(), "Queue should be empty after pop"),
                () -> assertTrue(queue.hasVisited(url), "URL should remain in the visited set")
            );
        }

        @Test
        @DisplayName("should ignore null and empty URLs")
        void shouldHandleNullAndEmptyUrls() {
            assertFalse(queue.push(null), "push(null) should return false");
            assertFalse(queue.push(""), "push('') should return false");

            assertAll("After pushing invalid URLs",
                () -> assertEquals(0, queue.size(), "Queue should remain empty"),
                () -> assertEquals(0, queue.visitedCount(), "Visited set should remain empty"),
                () -> assertFalse(queue.hasVisited(null), "hasVisited(null) should be false"),
                () -> assertFalse(queue.hasVisited(""), "hasVisited('') should be false")
            );
        }
    }

    @Nested
    @DisplayName("when managing state")
    class StateManagement {

        @Test
        @DisplayName("clear() should empty the queue but preserve the visited set")
        void shouldClearQueueButKeepVisitedSet() {
            var url = "https://example.com/page4";
            queue.push(url);
            queue.clear();

            assertAll("After clear()",
                () -> assertEquals(0, queue.size(), "Queue should be empty"),
                () -> assertTrue(queue.hasVisited(url), "Visited set should be preserved")
            );
        }

        @Test
        @DisplayName("clearAll() should empty both the queue and the visited set")
        void shouldClearAllData() {
            var url = "https://example.com/page5";
            queue.push(url);
            queue.clearAll();

            assertAll("After clearAll()",
                () -> assertEquals(0, queue.size(), "Queue should be empty"),
                () -> assertFalse(queue.hasVisited(url), "Visited set should be cleared")
            );
        }
    }

    @Nested
    @DisplayName("with custom configuration")
    class CustomConfiguration {
        @Test
        @DisplayName("should respect custom queue and visited set keys")
        void shouldRespectCustomKeys() {
            var customConfig = new RedisConfig(redis.getHost(), redis.getMappedPort(6379))
                .withQueueKey("test:my-queue")
                .withVisitedSetKey("test:my-visited");

            RedisFrontierQueue customQueue = null;
            try {
                customQueue = new RedisFrontierQueue(customConfig);
                customQueue.clearAll();
                var url = "https://example.com/custom";

                assertTrue(customQueue.push(url), "Should accept new URL on custom queue");
                assertEquals(1, customQueue.size(), "Custom queue should have one item");
                assertTrue(customQueue.hasVisited(url), "URL should be in custom visited set");
            } finally {
                if (customQueue != null) {
                    customQueue.close();
                }
            }
        }
    }
}

package com.monzo;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RedisFrontierQueue implementation.
 * <p>
 * Uses Testcontainers to spin up a Redis container for integration tests.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisFrontierQueueTest {
    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(REDIS_PORT);

    private RedisFrontierQueue queue;

    @BeforeEach
    void setUp() {
        var mappedPort = redisContainer.getMappedPort(REDIS_PORT);
        queue = new RedisFrontierQueue(redisContainer.getHost(), mappedPort);
        queue.clearAll();
    }

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.close();
        }
    }

    @Test
    void shouldDeduplicateUrls() {
        var url = "https://www.example.com";
        assertTrue(queue.push(url), "First push should succeed");
        assertEquals(1, queue.size(), "Queue should have one item");
        assertFalse(queue.push(url), "Second push of same URL should be rejected");
        assertEquals(1, queue.size(), "Queue size should still be 1");
        assertTrue(queue.hasVisited(url), "URL should be marked as visited");
        assertEquals(1, queue.visitedCount(), "Visited count should be 1");
    }

    @Test
    void shouldKeepVisitedSetAfterClear() {
        var url = "https://www.example.com";
        queue.push(url);
        queue.clear();
        assertEquals(0, queue.size(), "Queue should be empty");
        assertTrue(queue.hasVisited(url), "URL should still be marked as visited");
        assertFalse(queue.push(url), "URL should be rejected as already visited");
    }

    @Test
    void shouldClearAllData() {
        var url = "https://www.example.com";
        queue.push(url);
        queue.clearAll();
        assertEquals(0, queue.size(), "Queue should be empty");
        assertFalse(queue.hasVisited(url), "URL should not be marked as visited");
        assertEquals(0, queue.visitedCount(), "Visited count should be 0");
        assertTrue(queue.push(url), "URL should be accepted after clearAll");
    }
    
    @Test
    void visitedCountShouldReturnCorrectNumber() {
        queue.push("https://www.example.com/1");
        queue.push("https://www.example.com/2");
        queue.push("https://www.example.com/3");
        assertEquals(3, queue.visitedCount(), "Visited count should be 3");
        queue.push("https://www.example.com/1");
        assertEquals(3, queue.visitedCount(), "Visited count should still be 3");
    }

    @Test
    void popShouldPreserveVisitedStatus() {
        var url = "https://www.example.com";
        queue.push(url);
        var poppedUrl = queue.pop();
        assertEquals(url, poppedUrl, "Popped URL should match pushed URL");
        assertEquals(0, queue.size(), "Queue should be empty after pop");
        assertTrue(queue.hasVisited(url), "URL should still be marked as visited after pop");
        assertFalse(queue.push(url), "URL should be rejected as already visited");
    }
    
    @Test
    void shouldHandleNullAndEmptyUrls() {
        assertFalse(queue.push(null), "push(null) should return false");
        assertFalse(queue.push(""), "push(\"\") should return false");
        assertEquals(0, queue.size(), "Queue should remain empty after push(null) and push(\"\")");
        assertEquals(0, queue.visitedCount(), "Visited set should remain empty after push(null) and push(\"\")");
        assertFalse(queue.hasVisited(null), "hasVisited(null) should return false");
        assertFalse(queue.hasVisited(""), "hasVisited(\"\") should return false");
    }
}

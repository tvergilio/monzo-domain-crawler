package com.monzo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {
    private final String originalRedisHost = System.getenv("REDIS_HOST");
    private final String originalRedisPort = System.getenv("REDIS_PORT");
    
    @BeforeEach
    void setUpEnvironment() {
        // Clear environment variables for testing
        if (originalRedisHost != null) {
            System.clearProperty("REDIS_HOST");
        }
        if (originalRedisPort != null) {
            System.clearProperty("REDIS_PORT");
        }
    }
    
    @AfterEach
    void restoreEnvironment() {
        // Restore environment variables after tests
        if (originalRedisHost != null) {
            System.setProperty("REDIS_HOST", originalRedisHost);
        }
        if (originalRedisPort != null) {
            System.setProperty("REDIS_PORT", originalRedisPort);
        }
    }
    
    @Test
    void configInitialisesWithDefaults() {
        var config = new RedisConfig();
        assertEquals("localhost", config.getHost(), "Default host should be localhost");
        assertEquals(6379, config.getPort(), "Default port should be 6379");
    }
    
    @Test
    void configAcceptsCustomValues() {
        var customHost = "redis-server";
        int customPort = 6380;
        
        var config = new RedisConfig(customHost, customPort);
        assertEquals(customHost, config.getHost(), "Config should accept a custom host");
        assertEquals(customPort, config.getPort(), "Config should accept a custom port");
    }
    
    @Test
    void configThrowsExceptionForNullHost() {
        assertThrows(IllegalArgumentException.class, () -> new RedisConfig(null, 6379), 
            "RedisConfig constructor should throw IllegalArgumentException for null host");
    }
    
    @Test
    void toStringShouldFormatAsHostPort() {
        var config = new RedisConfig("localhost", 6379);
        var actual = config.toString();
        if (!"localhost:6379".equals(actual)) {
            System.err.println("RedisConfig.toString() returned: " + actual);
        }
        assertEquals("localhost:6379", actual, "toString should format as host:port");
    }
}

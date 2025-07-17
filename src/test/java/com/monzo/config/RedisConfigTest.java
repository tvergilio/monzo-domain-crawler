package com.monzo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RedisConfig class.
 */
@DisplayName("RedisConfig")
class RedisConfigTest {

    @Test
    @DisplayName("initialises with default host and port")
    void configInitialisesWithDefaults() {
        // Arrange
        var config = new RedisConfig();

        // Assert
        assertAll("Default configuration should be correct",
            () -> assertEquals("localhost", config.getHost(), "Default host should be 'localhost'"),
            () -> assertEquals(6379, config.getPort(), "Default port should be 6379")
        );
    }

    @Test
    @DisplayName("accepts custom host and port")
    void configAcceptsCustomValues() {
        // Arrange
        var customHost = "redis-server";
        int customPort = 6380;
        var config = new RedisConfig(customHost, customPort);

        // Assert
        assertAll("Custom configuration should be set correctly",
            () -> assertEquals(customHost, config.getHost(), "Should use the provided custom host"),
            () -> assertEquals(customPort, config.getPort(), "Should use the provided custom port")
        );
    }

    @Test
    @DisplayName("throws IllegalArgumentException for a null host")
    void configThrowsExceptionForNullHost() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> new RedisConfig(null, 6379),
            "Constructor should throw an exception for a null host"
        );
    }

    @Test
    @DisplayName("formats toString() as 'host:port'")
    void toStringShouldFormatAsHostPort() {
        // Arrange
        var config = new RedisConfig("my-redis", 12345);

        // Act & Assert
        assertEquals("my-redis:12345", config.toString(), "toString() should be formatted correctly");
    }
}

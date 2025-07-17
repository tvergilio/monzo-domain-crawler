package com.monzo.crawler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for HtmlFetcher.
 * These tests make real network calls and are subject to external conditions.
 */
@DisplayName("HtmlFetcher Integration Tests")
class HtmlFetcherIntegrationTest {

    private final HtmlFetcher fetcher = new HtmlFetcher();

    @Test
    @DisplayName("extracts absolute links from a live Wikipedia page")
    @Disabled("This is an integration test and depends on external network/service availability")
    void extractsAbsoluteLinksFromWikipedia() throws Exception {
        // Act
        var links = fetcher.fetchAndExtractLinks("https://en.wikipedia.org/wiki/Main_Page");

        // Assert
        assertNotNull(links);
        assertFalse(links.isEmpty(), "Should extract at least one link from the page");
        assertTrue(links.stream().allMatch(url -> url.startsWith("https://")), "All extracted links should be absolute URLs");
    }

    @Test
    @DisplayName("throws an exception for a non-existent domain")
    @Disabled("This is an integration test and depends on external network/service availability")
    void throwsForNonExistentDomain() {
        // Act & Assert
        assertThrows(Exception.class,
            () -> fetcher.fetchAndExtractLinks("https://nonexistent.domain.example.com"),
            "Should throw an exception for a domain that does not exist"
        );
    }

    @Test
    @DisplayName("throws an exception for non-HTML content")
    @Disabled("This is an integration test and depends on external network/service availability")
    void throwsForNonHtmlContent() {
        // Act & Assert
        assertThrows(Exception.class,
            () -> fetcher.fetchAndExtractLinks("https://httpbin.org/image/png"),
            "Should throw an exception when the content type is not HTML"
        );
    }

    @Test
    @DisplayName("throws an exception for a malformed URL")
    void throwsForMalformedUrl() {
        // Act & Assert
        var exception = assertThrows(Exception.class,
            () -> fetcher.fetchAndExtractLinks("ht!tp://bad-url"),
            "Should throw an exception for a URL with an invalid format"
        );

        // Also assert that the exception provides a message
        assertNotNull(exception.getMessage(), "Exception message should not be null");
        assertFalse(exception.getMessage().isBlank(), "Exception message should not be empty");
    }
}

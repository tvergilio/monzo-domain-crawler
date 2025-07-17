package com.monzo.crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HtmlFetcher.
 * Focuses on extracting absolute links from real HTML.
 */
class HtmlFetcherIntegrationTest {
    @Test
    void extractsAbsoluteLinksFromWikipedia() throws Exception {
        var fetcher = new HtmlFetcher();
        var links = fetcher.fetchAndExtractLinks("https://en.wikipedia.org/wiki/Main_Page");
        assertNotNull(links);
        assertFalse(links.isEmpty(), "Should extract at least one link");
        assertTrue(links.stream().allMatch(url -> url.startsWith("https://")), "All links should be absolute URLs");
    }

    @Test
    void throwsForNonExistentDomain() {
        var fetcher = new HtmlFetcher();
        Exception ex = null;
        try {
            fetcher.fetchAndExtractLinks("https://nonexistent.domain.example");
        } catch (Exception e) {
            ex = e;
        }
        assertNotNull(ex, "Exception should have been thrown for non-existent domain");
    }

    @Test
    void throwsForNonHtmlContent() {
        var fetcher = new HtmlFetcher();
        Exception ex = null;
        try {
            fetcher.fetchAndExtractLinks("https://httpbin.org/image/png");
        } catch (Exception e) {
            ex = e;
        }
        assertNotNull(ex, "Exception should have been thrown for non-HTML content");
        if (ex != null) {
            System.out.println("Non-HTML content exception: " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    @Test
    void throwsForMalformedUrl() {
        var fetcher = new HtmlFetcher();
        Exception ex = null;
        try {
            fetcher.fetchAndExtractLinks("ht!tp://bad-url");
        } catch (Exception e) {
            ex = e;
        }
        assertNotNull(ex, "Exception should have been thrown for malformed URL");
        var msg = ex.getMessage() == null ? "" : ex.getMessage();
        assertTrue(msg.length() > 0, "Exception message should not be empty");
    }
}

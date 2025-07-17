package com.monzo.crawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HtmlFetcher using mocking.
 * Focuses on behaviour with controlled HTTP responses.
 */
@ExtendWith(MockitoExtension.class)
public class HtmlFetcherTest {

    @Mock
    private HttpClient mockClient;

    @Mock
    private HttpResponse<String> mockResponse;

    @Mock
    private HttpHeaders mockHeaders;

    @InjectMocks
    private HtmlFetcher fetcher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        // This behavior is common to almost all tests, so we can set it up here.
        // It returns the generic mockResponse, which can be further configured in each test.
        when(mockClient.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
    }

    @Test
    void returnsEmptySetForPageWithNoLinks() throws Exception {
        // Arrange
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(mockHeaders);
        when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("text/html"));
        when(mockResponse.body()).thenReturn("<html><body>No links here</body></html>");

        // Act
        Set<String> links = fetcher.fetchAndExtractLinks("https://example.com/no-links");

        // Assert
        assertTrue(links.isEmpty(), "Should return an empty set for a page with no links");
    }

    @Test
    void extractsAndResolvesLinksFromHtml() throws Exception {
        // Arrange
        String html = "<html><body><a href='https://test.com/page1'>Link1</a><a href='/relative'>Link2</a></body></html>";
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(mockHeaders);
        when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("text/html"));
        when(mockResponse.body()).thenReturn(html);

        // Act
        Set<String> links = fetcher.fetchAndExtractLinks("https://test.com");

        // Assert
        assertTrue(links.contains("https://test.com/page1"), "Should contain the absolute link");
        assertTrue(links.contains("https://test.com/relative"), "Should resolve the relative link to absolute");
    }

    @Test
    void throwsForNonHtmlContentType() {
        // Arrange
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(mockHeaders);
        when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("image/png"));
        // No need to mock body() as the content-type check should happen first

        // Act & Assert
        assertThrows(Exception.class,
                () -> fetcher.fetchAndExtractLinks("https://test.com/image.png"),
                "Exception should be thrown for non-HTML content"
        );
    }

    @Test
    void throwsForHttpErrorStatus() {
        // Arrange
        when(mockResponse.statusCode()).thenReturn(404);

        // Act & Assert
        assertThrows(Exception.class,
                () -> fetcher.fetchAndExtractLinks("https://test.com/notfound"),
                "Exception should be thrown for an HTTP error status"
        );
    }
}
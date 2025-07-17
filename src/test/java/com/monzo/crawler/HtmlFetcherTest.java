package com.monzo.crawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
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
@DisplayName("HtmlFetcher Unit Tests")
class HtmlFetcherTest {

    @Mock
    private HttpClient mockClient;

    @Mock
    private HttpResponse<String> mockResponse;

    @Mock
    private HttpHeaders mockHeaders;

    @InjectMocks
    private HtmlFetcher fetcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        // This behavior is common to almost all tests, so we can set it up here.
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);
    }

    @Nested
    @DisplayName("when response is successful (200 OK)")
    class WhenResponseIsSuccessful {

        @BeforeEach
        void setUpSuccess() {
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.headers()).thenReturn(mockHeaders);
            when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("text/html"));
        }

        @Test
        @DisplayName("returns an empty set for a page with no links")
        void returnsEmptySetForPageWithNoLinks() throws Exception {
            // Arrange
            when(mockResponse.body()).thenReturn("<html><body>No links here</body></html>");

            // Act
            var links = fetcher.fetchAndExtractLinks("https://example.com/no-links");

            // Assert
            assertNotNull(links);
            assertTrue(links.isEmpty(), "Should return an empty set");
        }

        @Test
        @DisplayName("extracts and resolves both absolute and relative links")
        void extractsAndResolvesLinksFromHtml() throws Exception {
            // Arrange
            var html = "<html><body><a href='https://test.com/page1'>Link1</a><a href='/relative'>Link2</a></body></html>";
            when(mockResponse.body()).thenReturn(html);

            // Act
            var links = fetcher.fetchAndExtractLinks("https://test.com");

            // Assert
            assertNotNull(links);
            assertAll("Link extraction and resolution",
                () -> assertTrue(links.contains("https://test.com/page1"), "Should contain the absolute link"),
                () -> assertTrue(links.contains("https://test.com/relative"), "Should resolve the relative link")
            );
        }
    }

    @Nested
    @DisplayName("when response is an error")
    class WhenResponseIsError {

        @Test
        @DisplayName("throws an exception for non-HTML content")
        void throwsForNonHtmlContentType() {
            // Arrange
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.headers()).thenReturn(mockHeaders);
            when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("image/png"));

            // Act & Assert
            assertThrows(Exception.class,
                () -> fetcher.fetchAndExtractLinks("https://test.com/image.png"),
                "Should throw for non-HTML content"
            );
        }

        @Test
        @DisplayName("throws an exception for an HTTP error status (e.g., 404)")
        void throwsForHttpErrorStatus() {
            // Arrange
            when(mockResponse.statusCode()).thenReturn(404);

            // Act & Assert
            assertThrows(Exception.class,
                () -> fetcher.fetchAndExtractLinks("https://test.com/notfound"),
                "Should throw for an HTTP error status"
            );
        }
    }
}

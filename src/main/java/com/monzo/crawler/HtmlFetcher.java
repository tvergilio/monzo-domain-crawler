package com.monzo.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * Fetches HTML from a URL and extracts all absolute links.
 * Uses Java HttpClient and Jsoup for robust parsing.
 */
public final class HtmlFetcher {
    private final HttpClient client;

    /**
     * Constructs a HtmlFetcher with the default HttpClient.
     */
    public HtmlFetcher() {
        this(HttpClient.newHttpClient());
    }

    /**
     * Constructs a HtmlFetcher with a provided HttpClient (for testing/mocking).
     * @param client the HttpClient to use
     */
    public HtmlFetcher(HttpClient client) {
        this.client = client;
    }

    /**
     * Fetches the HTML content from the given URL and extracts all absolute links.
     *
     * @param url the URL to fetch
     * @return a set of absolute links found in the HTML
     * @throws Exception if fetching or parsing fails
     */
    public Set<String> fetchAndExtractLinks(String url) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch: " + url + " (status " + response.statusCode() + ")");
        }
        var contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        if (!contentType.contains("text/html")) {
            throw new RuntimeException("Content is not HTML: " + contentType);
        }
        var doc = Jsoup.parse(response.body(), url);
        var links = doc.select("a[href]");
        Set<String> absoluteLinks = new HashSet<>();
        for (Element link : links) {
            String absUrl = link.absUrl("href");
            if (!absUrl.isEmpty()) {
                absoluteLinks.add(absUrl);
            }
        }
        return absoluteLinks;
    }
}

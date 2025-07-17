package com.monzo;

import java.util.concurrent.CompletableFuture;

/**
 * Frontier queue abstraction for managing URLs to be crawled.
 * <p>
 * The frontier queue maintains the list of URLs that require crawling. It provides methods for adding URLs,
 * retrieving URLs, and inspecting the queue's state. Implementations should ensure thread safety and may
 * perform deduplication to prevent revisiting the same URL.
 * <p>
 * <strong>Deduplication:</strong> Implementations may reject duplicate URLs. The {@link #push(String)} method
 * returns false if a URL is rejected due to deduplication, allowing callers to know whether the URL was actually added.
 */
public interface FrontierQueue {

    /**
     * Pushes a URL onto the queue.
     * <p>
     * Implementations may deduplicate or filter URLs as necessary.
     *
     * @param url the absolute URL to enqueue
     * @return true if the URL was added, false if rejected (e.g., due to deduplication)
     */
    boolean push(String url);

    /**
     * Retrieves and removes the next URL from the queue.
     * <p>
     * Non-blocking implementations should return null immediately if the queue is empty.
     *
     * @return the next URL, or null if the queue is empty
     */
    String pop();

    /**
     * Asynchronously retrieves and removes the next URL from the queue.
     * <p>
     * Allows for non-blocking operation when the queue is empty.
     *
     * @return a CompletableFuture completed with the next URL when available, or null if the queue is empty
     */
    CompletableFuture<String> popAsync();


    /**
     * Returns the number of URLs currently in the queue.
     * <p>
     * For distributed implementations, this may be approximate.
     *
     * @return the number of URLs in the queue
     */
    int size();

    /**
     * Checks if the queue is empty.
     *
     * @return true if the queue is empty, false otherwise
     */
    boolean isEmpty();

    /**
     * Clears all URLs from the queue, leaving it empty.
     */
    void clear();
}

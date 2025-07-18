
package com.monzo.crawler;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.net.URI;

import com.monzo.config.CrawlerConfig;
import com.monzo.queue.FrontierQueue;
import com.monzo.queue.RedisFrontierQueue;

/**
 * Main entry point for the Monzo Domain Crawler.
 * <p>
 * Orchestrates the crawl loop using a fixed-size pool of virtual threads.
 * Parallelism is configurable via the RedisConfig.
 */
public final class DomainCrawler {
    private final HtmlFetcher htmlFetcher;
    private static final Random RANDOM = new Random();
    private final CrawlerConfig config;
    private final FrontierQueue frontier;

    public DomainCrawler(CrawlerConfig config, FrontierQueue frontier) {
        this(config, frontier, new HtmlFetcher());
    }

    // For testing
    public DomainCrawler(CrawlerConfig config, FrontierQueue frontier, HtmlFetcher htmlFetcher) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.frontier = Objects.requireNonNull(frontier, "frontier must not be null");
        this.htmlFetcher = Objects.requireNonNull(htmlFetcher, "htmlFetcher must not be null");
    }

    public CrawlerConfig getConfig() {
        return config;
    }

    public String getStartUrl() {
        return config.getStartUrl();
    }

    public void runCrawlLoop() {
        var parallelism = config.getConcurrency();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var task = (Runnable) () -> {
                try {
                    while (true) {
                        var url = frontier.pop();
                        if (url == null) {
                            break;
                        }
                        crawl(url);
                    }
                } catch (Exception e) {
                    System.err.printf("Crawl error: %s%n", e.getMessage());
                }
            };
            IntStream.range(0, parallelism).boxed().forEach(i -> executor.submit(task));
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Crawl loop finished.");
    }

    void crawl(String url) throws InterruptedException {
        System.out.printf("Crawling %s%n", url);
        var seedHost = getHost(config.getStartUrl());
        var urlHost = getHost(url);
        if (!sameDomain(seedHost, urlHost)) {
            System.err.printf("[WARN] Attempted to crawl %s, but host %s does not match seed host %s%n", url, urlHost, seedHost);
            // Guard: do not proceed if not same domain
            return;
        }
        try {
            var links = htmlFetcher.fetchAndExtractLinks(url);
            System.out.printf("%s -> %d links%n", url, links.size());
            for (var link : links) {
                var linkHost = getHost(link);
                if (sameDomain(seedHost, linkHost)) {
                    frontier.push(link);
                }
            }
        } catch (RetriableStatusException re) {
            backoff(re.getStatusCode());
            return;
        } catch (Exception e) {
            System.err.printf("Failed to fetch or extract links from %s: %s%n", url, e.getMessage());
        }
        Thread.sleep(100);
    }


    private static String getHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    void backoff(int statusCode) throws InterruptedException {
        var delay = config.getBackoffBaseMs();
        var maxDelay = config.getBackoffMaxMs();
        var jitterMax = config.getBackoffJitterMs();
        var maxRetries = config.getBackoffRetries();
        var attempt = 1;
        while (attempt <= maxRetries && delay <= maxDelay) {
            var jitter = RANDOM.nextInt(jitterMax + 1);
            System.out.printf("HTTP %d – backing off %d ms (%d/%d)%n", statusCode, delay + jitter, attempt, maxRetries);
            Thread.sleep(delay + jitter);
            delay = Math.min(delay * 2, maxDelay);
            attempt++;
        }
    }

    static boolean sameDomain(String seedHost, String linkHost) {
        if (seedHost == null || linkHost == null) {
            return false;
        }
        if (linkHost.equals(seedHost)) {
            return true;
        }
        // Ensure subdomain match is strict: must be ".seedHost" and not part of a longer suffix
        var idx = linkHost.length() - seedHost.length() - 1;
        return idx >= 0 && linkHost.charAt(idx) == '.' && linkHost.endsWith(seedHost);
    }


    public static void main(String[] args) {
        var config = CrawlerConfig.builderFromYaml().build();
        var redisConfig = config.getRedisConfig();
        var frontier = new RedisFrontierQueue(redisConfig);
        frontier.push(config.getStartUrl() != null ? config.getStartUrl() : "https://en.wikipedia.org");
        var crawler = new DomainCrawler(config, frontier);
        crawler.runCrawlLoop();
    }
}

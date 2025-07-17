
package com.monzo;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Main entry point for the Monzo Domain Crawler.
 * <p>
 * Orchestrates the crawl loop using a fixed-size pool of virtual threads.
 * Parallelism is configurable via the RedisConfig.
 */
public final class DomainCrawler {
    private static final Random RANDOM = new Random();
    private final CrawlerConfig config;
    private final FrontierQueue frontier;

    public DomainCrawler(CrawlerConfig config, FrontierQueue frontier) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.frontier = Objects.requireNonNull(frontier, "frontier must not be null");
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
                    var url = frontier.pop();
                    if (url == null) return;
                    crawl(url);
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

    private void crawl(String url) throws InterruptedException {
        System.out.printf("Crawling %s%n", url);
        var status = simulateFetch(url);
        if (status == 429 || status == 503) {
            backoff(status);
        } else {
            Thread.sleep(100);
        }
    }

    private void backoff(int statusCode) throws InterruptedException {
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

    private static int simulateFetch(String url) {
        var codes = new int[] { 200, 429, 503 };
        return codes[RANDOM.nextInt(codes.length)];
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

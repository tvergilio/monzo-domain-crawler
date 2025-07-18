package com.monzo.crawler;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;


import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
    private final CrawlerConfig config;
    private final FrontierQueue frontier;
    private static final Logger log = LoggerFactory.getLogger(DomainCrawler.class);
    private static final Set<Integer> RETRIABLE = Set.of(429, 502, 503, 504);

    // Thread‑safe per‑instance robots.txt cache. For demo‑scale, this is simple and robust.
    // If requirements change to scale to many containers, we should consider a Redis‑backed cache for cross‑instance sharing.
    private final ConcurrentMap<String, BaseRobotRules> robotsCache = new ConcurrentHashMap<>();
    private final int robotsTimeoutMs;
    private final HttpClient robotsClient;

    // One shared client per crawler instance: avoids recreating sockets for every robots.txt request.
    public DomainCrawler(CrawlerConfig config, FrontierQueue frontier) {
        this(config, frontier, new HtmlFetcher());
    }

    // For testing
    public DomainCrawler(CrawlerConfig config, FrontierQueue frontier, HtmlFetcher htmlFetcher) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.frontier = Objects.requireNonNull(frontier, "frontier must not be null");
        this.htmlFetcher = Objects.requireNonNull(htmlFetcher, "htmlFetcher must not be null");
        this.robotsTimeoutMs = config.getRobotsTimeoutMs();
        this.robotsClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(robotsTimeoutMs))
                .build();
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
                    log.error("Crawl task failed: {}", e.getMessage());
                }
            };
            IntStream.range(0, parallelism).boxed().forEach(i -> executor.submit(task));
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        log.info("Crawl loop finished.");
    }

    void crawl(String url) {
        var seedHost = host(config.getStartUrl());
        var pageHost = host(url);
        if (!sameDomain(seedHost, pageHost)) {
            log.warn("Skip off-domain link: {} (host {}, seed {})", url, pageHost, seedHost);
            return;
        }

        // robots.txt check: only crawl if allowed
        if (!isAllowedByRobots(url)) {
            log.info("Disallowed by robots.txt: {}", url);
            return;
        }

        try {
            var links = htmlFetcher.fetchAndExtractLinks(url);
            var filteredLinks = links.stream()
                    .filter(l -> sameDomain(seedHost, host(l)))
                    .filter(this::isAllowedByRobots)
                    .distinct()
                    .toList();
            prettyPrint(url, Set.copyOf(filteredLinks));
            filteredLinks.forEach(frontier::push);

        } catch (RetriableStatusException r) {
            if (RETRIABLE.contains(r.getStatusCode())) {
                try {
                    backoff(r.getStatusCode());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return; // exit crawl early
                }
            } else {
                log.warn("Non-retriable HTTP {} for {}", r.getStatusCode(), url);
            }

        } catch (Exception ex) {
            log.error("Fetch failed for {}: {}", url, ex.getMessage());
        }
    }

    /**
     * Returns true if the given URL is allowed by robots.txt for its host.
     * Fetches and caches robots.txt per host, with a 5 s timeout.
     */
    boolean isAllowedByRobots(String url) {
        var host = host(url);
        if (host == null) {
            return false;
        }
        var rules = robotsCache.computeIfAbsent(host, this::fetchRobotsRules);
        return rules == null || rules.isAllowed(url);
    }

    /**
     * Fetch and parse robots.txt for a host. Returns rules or <code>null</code> on error.
     */
    private BaseRobotRules fetchRobotsRules(String host) {
        try {
            var robotsUrl = "https://" + host + "/robots.txt";
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofMillis(robotsTimeoutMs))
                    .GET()
                    .build();
            var resp = robotsClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            var parser = new SimpleRobotRulesParser();
            return parser.parseContent(robotsUrl, resp.body(), "text/plain", List.of("monzo-crawler"));
        } catch (Exception e) {
            log.warn("robots.txt fetch failed for {}: {}", host, e.getMessage());
            return null;
        }
    }

    private static String host(String u) {
        try {
            return URI.create(u).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private void prettyPrint(String url, Set<String> links) {
        synchronized (System.out) { // keep lines together in parallel runs
            System.out.printf("%n%s  →  %d links%n", url, links.size());
            links.stream()
                    .sorted()
                    .forEach(l -> System.out.printf("   • %s%n", l));
        }
    }

    void backoff(int statusCode) throws InterruptedException {
        var delay = config.getBackoffBaseMs();
        var maxDelay = config.getBackoffMaxMs();
        var jitterMax = config.getBackoffJitterMs();
        var maxRetries = config.getBackoffRetries();
        var attempt = 1;
        while (attempt <= maxRetries && delay <= maxDelay) {
            var jitter = ThreadLocalRandom.current().nextInt(jitterMax + 1);
            log.info("Backing off for HTTP {}: attempt {}/{}", statusCode, attempt, maxRetries);
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
        // Ensure sub‑domain match is strict: must be ".seedHost" and not part of a longer suffix
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

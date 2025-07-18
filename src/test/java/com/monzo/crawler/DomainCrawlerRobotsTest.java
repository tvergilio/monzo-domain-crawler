package com.monzo.crawler;

import com.monzo.config.CrawlerConfig;
import com.monzo.queue.FrontierQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.mockito.Mockito.*;

/**
 * Unit tests covering robots.txt handling in {@link DomainCrawler}.
 *
 * <p>These tests verify that the crawler correctly honours robots.txt rules by:
 * <ul>
 *   <li>Skipping links disallowed by robots.txt</li>
 *   <li>Enqueuing all links if robots.txt allows all</li>
 *   <li>Enqueuing no links if robots.txt disallows all</li>
 * </ul>
 *
 * <p>All URLs use the Wikipedia domain for consistency with configuration.
 */
@DisplayName("DomainCrawler robots.txt behaviour")
class DomainCrawlerRobotsTest {

    private static final String START_URL = "https://en.wikipedia.org";
    private static final CrawlerConfig CFG = CrawlerConfig.builderFromYaml()
            .setStartUrl(START_URL)
            .build();

    private FrontierQueue frontier;

    @BeforeEach
    void setUp() {
        frontier = mock(FrontierQueue.class);
        // Pop once with the seed page, then signal queue exhaustion
        when(frontier.pop())
                .thenReturn(START_URL + "/home")
                .thenReturn(null);
    }


    /**
     * Verifies that the crawler skips links disallowed by robots.txt.
     *
     * <p>Only links not ending with <code>/disallowed</code> are permitted.
     */
    @Test
    @DisplayName("crawler skips links disallowed by robots.txt")
    void skipsDisallowedLinks() throws Exception {
        var links = Set.of(
                START_URL + "/allowed",
                START_URL + "/disallowed"
        );

        var crawler = crawlerWithMocks(links, url -> !url.endsWith("/disallowed"));
        crawler.runCrawlLoop();

        verify(frontier).push(START_URL + "/allowed");
        verify(frontier, never()).push(START_URL + "/disallowed");
    }


    /**
     * Verifies that the crawler enqueues all links if robots.txt allows all.
     */
    @Test
    @DisplayName("crawler enqueues all links if robots.txt allows all")
    void enqueuesAllIfRobotsAllowsAll() throws Exception {
        var links = Set.of(
                START_URL + "/one",
                START_URL + "/two"
        );

        var crawler = crawlerWithMocks(links, url -> true);
        crawler.runCrawlLoop();

        verify(frontier).push(START_URL + "/one");
        verify(frontier).push(START_URL + "/two");
    }


    /**
     * Verifies that the crawler enqueues no links if robots.txt disallows all.
     */
    @Test
    @DisplayName("crawler enqueues no links if robots.txt disallows all")
    void enqueuesNoneIfRobotsDisallowsAll() throws Exception {
        var links = Set.of(
                START_URL + "/one",
                START_URL + "/two"
        );

        var crawler = crawlerWithMocks(links, url -> false);
        crawler.runCrawlLoop();

        verify(frontier, never()).push(anyString());
    }

    /**
     * Creates a {@link DomainCrawler} with mocked {@link HtmlFetcher} and a robots.txt rule predicate.
     *
     * @param links      the set of links to return from the fetcher
     * @param robotsRule the predicate representing robots.txt allow/disallow logic
     * @return a spy DomainCrawler instance with all dependencies mocked
     */
    private DomainCrawler crawlerWithMocks(Set<String> links, Predicate<String> robotsRule) throws Exception {
        var fetcher = mock(HtmlFetcher.class);
        when(fetcher.fetchAndExtractLinks(START_URL + "/home")).thenReturn(links);

        var crawler = spy(new DomainCrawler(CFG, frontier, fetcher));
        // Prevent real back-off sleeps during tests
        doNothing().when(crawler).backoff(anyInt());
        doAnswer(inv -> robotsRule.test(inv.getArgument(0)))
                .when(crawler).isAllowedByRobots(anyString());

        return crawler;
    }
}

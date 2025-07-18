package com.monzo.crawler;

import com.monzo.config.CrawlerConfig;
import com.monzo.config.RedisConfig;
import com.monzo.queue.FrontierQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainCrawlerSameDomainTest {

    private static final CrawlerConfig CFG =
        CrawlerConfig.builder()
                     .setStartUrl("https://monzo.com/home")
                     .setConcurrency(1)
                     .setMaxDepth(1)
                     .setRedisConfig(new RedisConfig("localhost", 6379))
                     .build();

    private FrontierQueue fq;

    @BeforeEach
    void init() {
        fq = mock(FrontierQueue.class);
        when(fq.pop()).thenReturn("https://monzo.com/home").thenReturn(null);
    }


    @ParameterizedTest
    @CsvSource({
        "monzo.com,monzo.com,true",
        "monzo.com,api.monzo.com,true",
        "monzo.com,evilmonzo.com,false",
        "monzo.com,monzo.co.uk,false",
        "monzo.com,null,false"
    })
    void sameDomain_behaviour(String seed, String link, boolean expected) {
        assertEquals(expected, DomainCrawler.sameDomain(seed, link));
    }


    @Test
    void crawl_pushes_only_same_domain_links() throws Exception {
        HtmlFetcher fetcher = mock(HtmlFetcher.class);
        when(fetcher.fetchAndExtractLinks("https://monzo.com/home"))
            .thenReturn(Set.of(
                "https://monzo.com/careers",
                "https://evil.com/",
                "https://api.monzo.com/docs"));

        var crawler = spy(new DomainCrawler(CFG, fq, fetcher));
        doNothing().when(crawler).backoff(anyInt());
        // Mock robots.txt check to always allow
        doReturn(true)
            .when(crawler)
            .isAllowedByRobots(anyString());

        crawler.runCrawlLoop();

        verify(fq).push("https://monzo.com/careers");
        verify(fq).push("https://api.monzo.com/docs");
        verify(fq, never()).push("https://evil.com/");
    }


    private static Stream<Arguments> errorScenarios() {
        return Stream.of(
            Arguments.of(new RetriableStatusException(429, "Too many")),
            Arguments.of(new RuntimeException("I/O"))
        );
    }

    @ParameterizedTest
    @MethodSource("errorScenarios")
    void crawl_does_not_push_on_failure(Exception fetchException) throws Exception {
        HtmlFetcher fetcher = mock(HtmlFetcher.class);
        when(fetcher.fetchAndExtractLinks("https://monzo.com/home"))
            .thenThrow(fetchException);

        var crawler = spy(new DomainCrawler(CFG, fq, fetcher));
        doNothing().when(crawler).backoff(anyInt());

        crawler.runCrawlLoop();

        verifyNoInteractionsWithPush(fq);
    }


    private static void verifyNoInteractionsWithPush(FrontierQueue q) {
        verify(q, never()).push(any());
    }
}

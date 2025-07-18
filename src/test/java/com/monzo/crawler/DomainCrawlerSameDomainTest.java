package com.monzo.crawler;

import com.monzo.config.CrawlerConfig;
import com.monzo.queue.FrontierQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainCrawlerSameDomainTest {
    @ParameterizedTest
    @CsvSource({
            "monzo.com, monzo.com, true",
            "monzo.com, api.monzo.com, true",
            "monzo.com, evilmonzo.com, false",
            "monzo.com, monzo.co.uk, false",
            "monzo.com, null, false"
    })
    void testSameDomain(String seed, String link, boolean expected) {
        assertEquals(expected,
                DomainCrawler.sameDomain(seed, link));
    }

    @Test
    void crawl_pushesOnlySameDomainLinks() throws Exception {
        // Arrange
        var cfg = CrawlerConfig.builder()
                .setStartUrl("https://monzo.com/home")
                .setConcurrency(1)
                .setMaxDepth(1)
                .setRedisConfig(new com.monzo.config.RedisConfig("localhost", 6379))
                .build();
        var fq = mock(FrontierQueue.class);
        when(fq.pop())
                .thenReturn("https://monzo.com/home")
                .thenReturn(null);
        var fetcher = mock(HtmlFetcher.class);
        when(fetcher.fetchAndExtractLinks("https://monzo.com/home"))
                .thenReturn(Set.of(
                        "https://monzo.com/careers",
                        "https://evil.com/",
                        "https://api.monzo.com/docs"));
        var crawler = new DomainCrawler(cfg, fq, fetcher);

        // Act
        crawler.runCrawlLoop();

        // Assert
        verify(fq).push("https://monzo.com/careers");
        verify(fq).push("https://api.monzo.com/docs");
        verify(fq, never()).push("https://evil.com/");
    }
}
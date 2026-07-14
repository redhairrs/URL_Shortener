package com.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caching configuration using Caffeine.
 *
 * Caches shortCode → originalUrl mappings to serve the redirect hot path
 * without hitting the database on every request. The chapter's Figure 8
 * explicitly shows a cache layer between web servers and the database.
 *
 * Design decisions:
 * - Max 10,000 entries: keeps memory bounded (~2MB for URL strings)
 * - 10-minute TTL after write: balances freshness with DB load reduction
 * - Cache is on the redirect lookup only; click-count updates still go to DB
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("redirects");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }
}

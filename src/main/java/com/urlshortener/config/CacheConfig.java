package com.urlshortener.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Caching configuration using Redis.
 *
 * Caches shortCode → originalUrl mappings to serve the redirect hot path
 * without hitting the database on every request. Using Redis allows for
 * a distributed cache layer that can be shared across multiple instances
 * of the application.
 *
 * Design decisions:
 * - 10-minute TTL: balances freshness with DB load reduction
 * - JSON Serialization: makes cache entries readable and avoids class-loading issues
 * - Cache is on the redirect lookup only; click-count updates still go to DB
 */
@Configuration
@EnableCaching
@Profile("!test")
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfiguration())
                .build();
    }
}

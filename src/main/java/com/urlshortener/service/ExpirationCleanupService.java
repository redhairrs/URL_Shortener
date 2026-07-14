package com.urlshortener.service;

import com.urlshortener.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled service that cleans up expired URL mappings.
 *
 * Runs every hour and deletes all entries where expiresAt < now().
 * This prevents the database from growing unboundedly when URLs
 * have a TTL set.
 *
 * In production at scale, this would be replaced by a partitioned
 * table with time-based partition drops, or a dedicated TTL mechanism
 * in the database layer.
 */
@Service
public class ExpirationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ExpirationCleanupService.class);

    private final UrlMappingRepository repository;

    public ExpirationCleanupService(UrlMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * Delete all expired URL mappings every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Transactional
    public void cleanupExpiredUrls() {
        LocalDateTime now = LocalDateTime.now();
        long count = repository.deleteByExpiresAtBefore(now);

        if (count > 0) {
            log.info("Cleaned up {} expired URL mappings", count);
        }
    }
}

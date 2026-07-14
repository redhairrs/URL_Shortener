package com.urlshortener.repository;

import com.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for URL mappings.
 *
 * Spring generates the implementation at runtime — we only declare
 * the query methods we need beyond the built-in CRUD.
 */
@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Look up a mapping by its short code.
     * Used by both redirect and stats endpoints.
     */
    @org.springframework.cache.annotation.Cacheable(value = "redirects", unless = "#result == null")
    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Check if a short code is already taken.
     * Used to validate custom aliases before insertion.
     */
    boolean existsByShortCode(String shortCode);

    /**
     * Delete all URL mappings that have expired.
     */
    long deleteByExpiresAtBefore(java.time.LocalDateTime now);

    /**
     * Count how many URL mappings have expired.
     */
    int countByExpiresAtBefore(java.time.LocalDateTime now);
}

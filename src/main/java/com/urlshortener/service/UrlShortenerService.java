package com.urlshortener.service;

import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.ShortCodeGenerator;
import com.urlshortener.util.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core business logic for URL shortening, redirection, and analytics.
 */
@Service
public class UrlShortenerService {

    private final UrlMappingRepository repository;
    private final String baseUrl;
    private final long scrambleKey;
    
    private UrlShortenerService self;

    public UrlShortenerService(UrlMappingRepository repository,
                               @Value("${app.base-url}") String baseUrl,
                               @Value("${app.scramble-key:1575825271}") long scrambleKey) {
        this.repository = repository;
        this.baseUrl = baseUrl;
        this.scrambleKey = scrambleKey;
    }

    @Autowired
    public void setSelf(@Lazy UrlShortenerService self) {
        this.self = self;
    }

    @Transactional
    public ShortenResponse shortenUrl(String originalUrl, String customAlias, Integer expiresInHours) {
        if (!UrlValidator.isValid(originalUrl)) {
            throw new InvalidUrlException(originalUrl);
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);
        if (expiresInHours != null) {
            mapping.setExpiresAt(LocalDateTime.now().plusHours(expiresInHours));
        }

        if (customAlias != null && !customAlias.isBlank()) {
            if (repository.existsByShortCode(customAlias)) {
                throw new AliasAlreadyExistsException(customAlias);
            }
            mapping.setShortCode(customAlias);
            mapping.setCustom(true);
            repository.save(mapping);
        } else {
            mapping.setShortCode("_placeholder_");
            mapping.setCustom(false);
            mapping = repository.save(mapping);

            String shortCode = ShortCodeGenerator.generate(mapping.getId(), scrambleKey);
            mapping.setShortCode(shortCode);
            repository.save(mapping);
        }

        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl()
        );
    }

    @Transactional
    public String resolveAndTrack(String shortCode) {
        UrlMapping mapping = self.getCachedMapping(shortCode);

        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            self.evictMapping(shortCode);
            throw new UrlExpiredException(shortCode);
        }

        mapping.setClickCount(mapping.getClickCount() + 1);
        mapping.setLastAccessedAt(LocalDateTime.now());
        repository.save(mapping);

        return mapping.getOriginalUrl();
    }

    @Cacheable(value = "redirects", key = "#shortCode")
    public UrlMapping getCachedMapping(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    @CacheEvict(value = "redirects", key = "#shortCode")
    public void evictMapping(String shortCode) {
        // Evicts mapping from cache
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        return new UrlStatsResponse(
                mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getClickCount(),
                mapping.getCreatedAt(),
                mapping.getLastAccessedAt()
        );
    }
}

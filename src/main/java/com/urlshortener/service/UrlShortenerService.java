package com.urlshortener.service;

import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.Base62Encoder;
import com.urlshortener.util.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core business logic for URL shortening, redirection, and analytics.
 *
 * Design decisions:
 * - Each shorten call produces a NEW short code, even for duplicate URLs.
 *   This allows multiple campaigns to track the same destination independently.
 * - Click tracking is done via a denormalized counter on the UrlMapping entity
 *   (simple and fast for this scale; a separate clicks table would be better
 *   for per-click metadata like referrer/geo).
 * - Base62 encoding of the auto-increment ID guarantees collision-free codes
 *   without retries or locks.
 */
@Service
public class UrlShortenerService {

    private final UrlMappingRepository repository;
    private final String baseUrl;

    public UrlShortenerService(UrlMappingRepository repository,
                               @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
    }

    /**
     * Shorten a URL, optionally using a custom alias.
     *
     * Flow for generated codes:
     * 1. Validate the URL
     * 2. Save entity with a placeholder short code (to get the auto-increment ID)
     * 3. Encode the ID to Base62
     * 4. Update the entity with the real short code
     *
     * Flow for custom aliases:
     * 1. Validate the URL
     * 2. Check alias uniqueness
     * 3. Save entity with the custom alias as the short code
     */
    @Transactional
    public ShortenResponse shortenUrl(String originalUrl, String customAlias) {
        if (!UrlValidator.isValid(originalUrl)) {
            throw new InvalidUrlException(originalUrl);
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);

        if (customAlias != null && !customAlias.isBlank()) {
            // Custom alias flow
            if (repository.existsByShortCode(customAlias)) {
                throw new AliasAlreadyExistsException(customAlias);
            }
            mapping.setShortCode(customAlias);
            mapping.setCustom(true);
            repository.save(mapping);
        } else {
            // Generated code flow: save first to get the ID, then encode it
            mapping.setShortCode("_placeholder_");
            mapping.setCustom(false);
            mapping = repository.save(mapping);

            String shortCode = Base62Encoder.encode(mapping.getId());
            mapping.setShortCode(shortCode);
            repository.save(mapping);
        }

        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl()
        );
    }

    /**
     * Resolve a short code to its original URL and track the click.
     *
     * @return the original URL
     * @throws UrlNotFoundException if the code doesn't exist
     */
    @Transactional
    public String resolveAndTrack(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        mapping.setClickCount(mapping.getClickCount() + 1);
        mapping.setLastAccessedAt(LocalDateTime.now());
        repository.save(mapping);

        return mapping.getOriginalUrl();
    }

    /**
     * Return analytics for a given short code.
     *
     * @throws UrlNotFoundException if the code doesn't exist
     */
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

package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for URL shortening and analytics.
 *
 * Endpoints:
 *   POST /api/v1/shorten         → create a short URL
 *   GET  /api/v1/urls/{code}/stats → view click analytics
 */
@RestController
@RequestMapping("/api/v1")
public class UrlShortenerController {

    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    /**
     * Shorten a URL. Optionally provide a custom alias.
     *
     * @param request body with url (required) and customAlias (optional)
     * @return 201 Created with the short URL details
     */
    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = service.shortenUrl(request.getUrl(), request.getCustomAlias(), request.getExpiresInHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get click analytics for a short code.
     *
     * @param code the short code
     * @return 200 OK with stats
     */
    @GetMapping("/urls/{code}/stats")
    public ResponseEntity<UrlStatsResponse> getStats(@PathVariable String code) {
        UrlStatsResponse stats = service.getStats(code);
        return ResponseEntity.ok(stats);
    }
}

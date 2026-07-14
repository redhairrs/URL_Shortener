package com.urlshortener.controller;

import com.urlshortener.service.UrlShortenerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles short-code redirects.
 *
 * GET /{code} → 301 Moved Permanently to the original URL.
 *
 * Uses 301 (permanent redirect) because the mapping is immutable once created.
 * Browsers and proxies can cache this, reducing load on the service.
 */
@RestController
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    /**
     * Redirect to the original URL and track the click.
     *
     * @param code the short code
     * @return 301 redirect with Location header, or 404 via exception handler
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = service.resolveAndTrack(code);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, originalUrl);

        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }
}

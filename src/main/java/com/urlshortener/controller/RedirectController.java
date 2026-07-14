package com.urlshortener.controller;

import com.urlshortener.service.UrlShortenerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles short-code redirects.
 *
 * GET /{code} → redirect to the original URL.
 *
 * Redirect type is configurable via {@code app.redirect-type}:
 * - 302 (default): Temporary redirect. Every click hits the server, enabling
 *   accurate analytics. Recommended when click tracking matters.
 * - 301: Permanent redirect. Browsers cache the redirect, reducing server load
 *   but making subsequent clicks invisible to analytics.
 */
@RestController
public class RedirectController {

    private final UrlShortenerService service;
    private final HttpStatus redirectStatus;

    public RedirectController(UrlShortenerService service,
                              @Value("${app.redirect-type:302}") int redirectType) {
        this.service = service;
        this.redirectStatus = (redirectType == 301)
                ? HttpStatus.MOVED_PERMANENTLY
                : HttpStatus.FOUND;
    }

    /**
     * Redirect to the original URL and track the click.
     *
     * @param code the short code
     * @return redirect with Location header, or 404 via exception handler
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = service.resolveAndTrack(code);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, originalUrl);

        return new ResponseEntity<>(headers, redirectStatus);
    }
}


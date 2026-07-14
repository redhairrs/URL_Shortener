package com.urlshortener.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Validates URLs before shortening.
 *
 * Checks:
 * - Not null or blank
 * - Valid URI syntax (RFC 2396)
 * - Valid URL with protocol and host
 * - Only http/https protocols (blocks file://, ftp://, javascript:, etc.)
 */
public final class UrlValidator {

    private UrlValidator() {
        // Utility class — no instances
    }

    /**
     * Returns true if the URL is well-formed and uses http or https.
     */
    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            URL parsed = uri.toURL();

            String protocol = parsed.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return false;
            }

            String host = parsed.getHost();
            return host != null && !host.isBlank();

        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            return false;
        }
    }
}

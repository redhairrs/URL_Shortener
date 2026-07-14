package com.urlshortener.util;

/**
 * Encodes numeric IDs into compact, URL-safe Base62 strings.
 *
 * Character set: 0-9, a-z, A-Z (62 characters).
 * This is a bijective mapping — each positive long maps to exactly one string,
 * guaranteeing collision-free short codes when fed auto-increment IDs.
 *
 * Examples:
 *   encode(1)     → "1"
 *   encode(62)    → "10"
 *   encode(1000)  → "g8"
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
        // Utility class — no instances
    }

    /**
     * Encode a positive ID to a Base62 string.
     *
     * @param id must be > 0
     * @return URL-safe Base62 string
     * @throws IllegalArgumentException if id <= 0
     */
    public static String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive, got: " + id);
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }
}

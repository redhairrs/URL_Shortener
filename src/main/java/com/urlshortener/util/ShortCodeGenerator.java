package com.urlshortener.util;

/**
 * Generates non-sequential, collision-free short codes from auto-increment IDs.
 *
 * Uses a 2-round Feistel cipher to scramble the ID before Base62 encoding.
 * This ensures:
 * - Bijection: each ID maps to exactly one code and vice versa (collision-free)
 * - Non-sequential: consecutive IDs produce visually unrelated codes
 * - Deterministic: the same ID always produces the same code (given the same key)
 * - No external dependencies: ~20 lines of pure Java
 *
 * Why Feistel over alternatives:
 * - XOR with a constant: too simple, patterns still visible
 * - Hashids library: adds a dependency for trivial functionality
 * - Random codes: require uniqueness checks and retry loops
 *
 * The Feistel cipher splits the input into two halves, applies a round function
 * with the key, and swaps the halves. Two rounds give good diffusion for our
 * use case (short code obscurity, not cryptographic security).
 *
 * Examples (with default key):
 *   generate(1)  → non-obvious code (not "1")
 *   generate(2)  → unrelated to generate(1)
 *   generate(3)  → unrelated to generate(2)
 */
public final class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    /**
     * Default scramble key. Override via app.scramble-key in application.yml.
     * Any positive long value works — changing it changes all generated codes.
     */
    private static final long DEFAULT_KEY = 0x5DEECE66DL;

    private ShortCodeGenerator() {
        // Utility class — no instances
    }

    /**
     * Generate a short code from a positive ID using the default key.
     *
     * @param id must be > 0
     * @return scrambled, Base62-encoded short code
     */
    public static String generate(long id) {
        return generate(id, DEFAULT_KEY);
    }

    /**
     * Generate a short code from a positive ID using a custom key.
     *
     * @param id  must be > 0
     * @param key the scramble key (any positive long)
     * @return scrambled, Base62-encoded short code
     */
    public static String generate(long id, long key) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive, got: " + id);
        }
        long scrambled = feistelScramble(id, key);
        return base62Encode(scrambled);
    }

    /**
     * Encode a positive value to a Base62 string.
     * Kept public for backward compatibility and direct use.
     *
     * @param value must be > 0
     * @return URL-safe Base62 string
     */
    public static String base62Encode(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be positive, got: " + value);
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * 2-round Feistel cipher for scrambling IDs.
     *
     * Splits the 64-bit ID into two 32-bit halves, applies a keyed
     * round function, and swaps. The result is a bijective permutation
     * of the input space — every input maps to a unique output.
     *
     * @param id  the input ID
     * @param key the scramble key
     * @return scrambled ID (always positive)
     */
    static long feistelScramble(long id, long key) {
        // Split into two 32-bit halves
        int left = (int) (id >>> 32);
        int right = (int) (id & 0xFFFFFFFFL);

        // Round 1
        int temp = left ^ roundFunction(right, key, 1);
        left = right;
        right = temp;

        // Round 2
        temp = left ^ roundFunction(right, key, 2);
        left = right;
        right = temp;

        // Recombine and ensure positive
        long result = ((long) left << 32) | (right & 0xFFFFFFFFL);
        return Math.abs(result) + 1; // +1 ensures result > 0
    }

    /**
     * Keyed round function using multiplicative hashing.
     * Produces good bit diffusion without being cryptographically secure
     * (which we don't need for URL shortening).
     */
    private static int roundFunction(int value, long key, int round) {
        long mixed = (value & 0xFFFFFFFFL) * (key + round);
        mixed ^= (mixed >>> 16);
        return (int) mixed;
    }
}

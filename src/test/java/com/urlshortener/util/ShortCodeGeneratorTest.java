package com.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShortCodeGenerator.
 */
class ShortCodeGeneratorTest {

    @Test
    void base62Encode_singleDigitIds_producesExpectedCodes() {
        assertEquals("1", ShortCodeGenerator.base62Encode(1));
        assertEquals("2", ShortCodeGenerator.base62Encode(2));
        assertEquals("a", ShortCodeGenerator.base62Encode(10));
        assertEquals("z", ShortCodeGenerator.base62Encode(35));
        assertEquals("A", ShortCodeGenerator.base62Encode(36));
        assertEquals("Z", ShortCodeGenerator.base62Encode(61));
    }

    @Test
    void base62Encode_multiDigitIds_producesExpectedCodes() {
        assertEquals("10", ShortCodeGenerator.base62Encode(62));
        assertEquals("11", ShortCodeGenerator.base62Encode(63));
        assertEquals("g8", ShortCodeGenerator.base62Encode(1000));
    }

    @Test
    void generate_consecutiveIds_produceUniqueAndScrambledResults() {
        long[] ids = {1, 2, 3, 4, 5, 1000, 1001};
        String[] codes = new String[ids.length];

        for (int i = 0; i < ids.length; i++) {
            codes[i] = ShortCodeGenerator.generate(ids[i], 123456789L);
        }

        // All codes should be distinct (bijection property)
        assertEquals(ids.length, java.util.Arrays.stream(codes).distinct().count(),
                "All encoded values should be unique");
        
        // Also check they are not consecutive base62 strings like "1", "2", "3"
        assertNotEquals("1", codes[0]);
        assertNotEquals("2", codes[1]);
    }
    
    @Test
    void feistelScramble_sameId_producesSameResult() {
        long id = 12345L;
        long key = 987654321L;
        assertEquals(ShortCodeGenerator.feistelScramble(id, key), ShortCodeGenerator.feistelScramble(id, key));
    }
}

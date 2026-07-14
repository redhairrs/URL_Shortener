package com.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Base62Encoder.
 *
 * Verifies:
 * - Known ID-to-code mappings
 * - Bijection (unique inputs → unique outputs)
 * - URL-safe output (only alphanumeric characters)
 * - Edge cases (zero, negative)
 */
class Base62EncoderTest {

    @Test
    void encode_singleDigitIds_producesExpectedCodes() {
        assertEquals("1", Base62Encoder.encode(1));
        assertEquals("2", Base62Encoder.encode(2));
        assertEquals("a", Base62Encoder.encode(10));
        assertEquals("z", Base62Encoder.encode(35));
        assertEquals("A", Base62Encoder.encode(36));
        assertEquals("Z", Base62Encoder.encode(61));
    }

    @Test
    void encode_multiDigitIds_producesExpectedCodes() {
        assertEquals("10", Base62Encoder.encode(62));
        assertEquals("11", Base62Encoder.encode(63));
        assertEquals("g8", Base62Encoder.encode(1000));
    }

    @Test
    void encode_largeId_producesUrlSafeString() {
        String code = Base62Encoder.encode(999_999_999L);
        assertTrue(code.matches("^[0-9a-zA-Z]+$"),
                "Code should only contain URL-safe characters: " + code);
    }

    @Test
    void encode_consecutiveIds_produceUniqueResults() {
        // Verify no collisions across a range of IDs
        long[] ids = {1, 2, 10, 62, 100, 1000, 10000, 100000, 999999};
        String[] codes = new String[ids.length];

        for (int i = 0; i < ids.length; i++) {
            codes[i] = Base62Encoder.encode(ids[i]);
        }

        // All codes should be distinct
        assertEquals(ids.length, java.util.Arrays.stream(codes).distinct().count(),
                "All encoded values should be unique");
    }

    @Test
    void encode_zeroId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(0));
    }

    @Test
    void encode_negativeId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(-1));
    }
}

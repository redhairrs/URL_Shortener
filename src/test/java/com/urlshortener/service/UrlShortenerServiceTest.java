package com.urlshortener.service;

import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UrlShortenerService.
 *
 * Uses Mockito to isolate service logic from the database.
 */
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService(repository, "http://localhost:8080");
    }

    // --- shortenUrl tests ---

    @Test
    void shortenUrl_validUrl_returnsGeneratedCode() {
        // Simulate auto-increment: first save returns entity with id=1
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping m = invocation.getArgument(0);
            if (m.getId() == null) {
                m.setId(1L);
            }
            return m;
        });

        ShortenResponse response = service.shortenUrl("https://www.example.com", null);

        assertNotNull(response.getShortCode());
        assertEquals("1", response.getShortCode()); // Base62(1) = "1"
        assertEquals("http://localhost:8080/1", response.getShortUrl());
        assertEquals("https://www.example.com", response.getOriginalUrl());

        // save called twice: once for placeholder, once for real code
        verify(repository, times(2)).save(any(UrlMapping.class));
    }

    @Test
    void shortenUrl_customAlias_usesAliasAsCode() {
        when(repository.existsByShortCode("my-link")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });

        ShortenResponse response = service.shortenUrl("https://www.example.com", "my-link");

        assertEquals("my-link", response.getShortCode());
        assertEquals("http://localhost:8080/my-link", response.getShortUrl());

        // save called once (no placeholder step for custom aliases)
        verify(repository, times(1)).save(any(UrlMapping.class));
    }

    @Test
    void shortenUrl_duplicateAlias_throwsConflict() {
        when(repository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class,
                () -> service.shortenUrl("https://www.example.com", "taken"));
    }

    @Test
    void shortenUrl_invalidUrl_throwsBadRequest() {
        assertThrows(InvalidUrlException.class,
                () -> service.shortenUrl("not-a-url", null));
    }

    @Test
    void shortenUrl_ftpUrl_throwsBadRequest() {
        assertThrows(InvalidUrlException.class,
                () -> service.shortenUrl("ftp://files.example.com/doc.pdf", null));
    }

    @Test
    void shortenUrl_emptyUrl_throwsBadRequest() {
        assertThrows(InvalidUrlException.class,
                () -> service.shortenUrl("", null));
    }

    // --- resolveAndTrack tests ---

    @Test
    void resolveAndTrack_existingCode_returnsUrlAndIncrementsCount() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc");
        mapping.setOriginalUrl("https://www.example.com");
        mapping.setClickCount(5);

        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));
        when(repository.save(any(UrlMapping.class))).thenReturn(mapping);

        String originalUrl = service.resolveAndTrack("abc");

        assertEquals("https://www.example.com", originalUrl);

        // Verify click count was incremented
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).save(captor.capture());
        assertEquals(6, captor.getValue().getClickCount());
        assertNotNull(captor.getValue().getLastAccessedAt());
    }

    @Test
    void resolveAndTrack_unknownCode_throwsNotFound() {
        when(repository.findByShortCode("nope")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class,
                () -> service.resolveAndTrack("nope"));
    }

    // --- getStats tests ---

    @Test
    void getStats_existingCode_returnsStats() {
        LocalDateTime now = LocalDateTime.now();
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc");
        mapping.setOriginalUrl("https://www.example.com");
        mapping.setClickCount(42);
        mapping.setCreatedAt(now.minusDays(1));
        mapping.setLastAccessedAt(now);

        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        UrlStatsResponse stats = service.getStats("abc");

        assertEquals("abc", stats.getShortCode());
        assertEquals("https://www.example.com", stats.getOriginalUrl());
        assertEquals(42, stats.getClickCount());
        assertEquals(now.minusDays(1), stats.getCreatedAt());
        assertEquals(now, stats.getLastAccessedAt());
    }

    @Test
    void getStats_unknownCode_throwsNotFound() {
        when(repository.findByShortCode("nope")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class,
                () -> service.getStats("nope"));
    }
}

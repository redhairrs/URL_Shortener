package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests exercising the full Spring context with H2 in-memory database.
 *
 * Tests cover the complete request lifecycle:
 * shorten → redirect → stats, plus error cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlShortenerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlMappingRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    // --- POST /api/v1/shorten ---

    @Test
    void shorten_validUrl_returns201WithShortCode() throws Exception {
        ShortenRequest request = new ShortenRequest("https://www.example.com", null);

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").isNotEmpty())
                .andExpect(jsonPath("$.originalUrl").value("https://www.example.com"));
    }

    @Test
    void shorten_customAlias_returns201WithAlias() throws Exception {
        ShortenRequest request = new ShortenRequest("https://www.example.com", "my-custom");

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("my-custom"))
                .andExpect(jsonPath("$.shortUrl", containsString("my-custom")));
    }

    @Test
    void shorten_duplicateAlias_returns409() throws Exception {
        ShortenRequest request = new ShortenRequest("https://www.example.com", "dup-alias");

        // First call succeeds
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second call with same alias fails with 409
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shorten_invalidUrl_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest("not-a-valid-url", null);

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void shorten_missingUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void shorten_invalidAliasFormat_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest("https://www.example.com", "a"); // too short

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- GET /{code} (redirect) ---

    @Test
    void redirect_existingCode_returns301WithLocation() throws Exception {
        // First, create a short URL
        ShortenRequest request = new ShortenRequest("https://www.google.com", "goog");

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Then redirect
        mockMvc.perform(get("/goog"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.google.com"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // --- GET /api/v1/urls/{code}/stats ---

    @Test
    void stats_existingCode_returnsClickCount() throws Exception {
        // Create a short URL
        ShortenRequest request = new ShortenRequest("https://www.example.com", "stats-test");

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Stats before any clicks
        mockMvc.perform(get("/api/v1/urls/stats-test/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("stats-test"))
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.lastAccessedAt").isEmpty());
    }

    @Test
    void stats_unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/urls/nope/stats"))
                .andExpect(status().isNotFound());
    }

    // --- Round-trip test ---

    @Test
    void roundTrip_shorten_redirect_stats() throws Exception {
        // 1. Shorten
        ShortenRequest request = new ShortenRequest("https://www.wikipedia.org", "wiki");

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("wiki"));

        // 2. Redirect (click 1)
        mockMvc.perform(get("/wiki"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.wikipedia.org"));

        // 3. Redirect again (click 2)
        mockMvc.perform(get("/wiki"))
                .andExpect(status().isFound());

        // 4. Verify stats show 2 clicks
        mockMvc.perform(get("/api/v1/urls/wiki/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(2))
                .andExpect(jsonPath("$.lastAccessedAt").isNotEmpty());
    }

    // --- Duplicate URL, new code each time ---

    @Test
    void shorten_sameUrlTwice_returnsDifferentCodes() throws Exception {
        ShortenRequest request = new ShortenRequest("https://www.example.com/same", null);

        MvcResult result1 = mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String code1 = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("shortCode").asText();
        String code2 = objectMapper.readTree(result2.getResponse().getContentAsString())
                .get("shortCode").asText();

        assertNotEquals(code1, code2,
                "Same URL shortened twice should produce different short codes");
    }
}

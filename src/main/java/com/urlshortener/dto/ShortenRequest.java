package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/shorten.
 */
public class ShortenRequest {

    @NotBlank(message = "URL is required")
    private String url;

    /**
     * Optional custom alias. If provided, must be 3-30 alphanumeric/hyphen characters.
     */
    @Size(min = 3, max = 30, message = "Custom alias must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Custom alias may only contain letters, digits, and hyphens")
    private String customAlias;

    public ShortenRequest() {}

    public ShortenRequest(String url, String customAlias) {
        this.url = url;
        this.customAlias = customAlias;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }
}

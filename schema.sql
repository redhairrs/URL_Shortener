-- URL Shortener Schema
-- MySQL 8.x DDL for manual setup (Hibernate also auto-creates in dev mode)

CREATE DATABASE IF NOT EXISTS url_shortener;
USE url_shortener;

CREATE TABLE IF NOT EXISTS urls (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    short_code      VARCHAR(30)     NOT NULL,
    original_url    VARCHAR(2048)   NOT NULL,
    is_custom       BOOLEAN         NOT NULL DEFAULT FALSE,
    click_count     BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP      NULL,
    expires_at      TIMESTAMP       NULL,

    CONSTRAINT uk_short_code UNIQUE (short_code),
    INDEX idx_short_code (short_code),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

# URL Shortener & Link Analytics

A Spring Boot 3 service that shortens URLs, redirects via short codes, and tracks click analytics.

## Prerequisites

- **Java 21** (or later)
- **Maven 3.9+** (or use the included Maven wrapper `./mvnw`)
- **MySQL 8.x** (for production; tests use H2 in-memory)

## Quick Start

### 1. Set up MySQL

```sql
CREATE DATABASE IF NOT EXISTS url_shortener;
```

Or run the provided schema:

```bash
mysql -u root -p < schema.sql
```

### 2. Configure database credentials

Edit `src/main/resources/application.yml` if your MySQL credentials differ from the defaults (`root`/`root`):

```yaml
spring:
  datasource:
    username: root
    password: root
```

### 3. Build and run

```bash
# Build (skip tests if MySQL isn't available)
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

The service starts at `http://localhost:8080`.

### 4. Run tests

Tests use H2 in-memory database — no MySQL needed:

```bash
./mvnw test
```

## API Reference

### Shorten a URL

```bash
# Auto-generated short code
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com/very/long/path"}'
```

Response (201 Created):

```json
{
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/1",
  "originalUrl": "https://www.example.com/very/long/path"
}
```

### Shorten with a custom alias

```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com", "customAlias": "my-link"}'
```

Response (201 Created):

```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "originalUrl": "https://www.example.com"
}
```

### Redirect

```bash
curl -v http://localhost:8080/my-link
# → 301 Moved Permanently
# → Location: https://www.example.com
```

### View analytics

```bash
curl http://localhost:8080/api/v1/urls/my-link/stats
```

Response (200 OK):

```json
{
  "shortCode": "my-link",
  "originalUrl": "https://www.example.com",
  "clickCount": 5,
  "createdAt": "2025-01-15T10:30:00",
  "lastAccessedAt": "2025-01-15T14:22:00"
}
```

### Error responses

| Scenario             | Status | Error            |
|----------------------|--------|------------------|
| Invalid URL          | 400    | Bad Request      |
| Missing URL field    | 400    | Validation Error |
| Invalid alias format | 400    | Validation Error |
| Alias already taken  | 409    | Conflict         |
| Unknown short code   | 404    | Not Found        |

## Design Decisions

### Short-code generation

**Base62 encoding of auto-increment IDs** — collision-free by design:
- MySQL `AUTO_INCREMENT` guarantees unique, monotonically increasing IDs
- Base62 (0-9, a-z, A-Z) is a bijective mapping — each ID produces exactly one unique code
- No randomness, no hash collisions, no retry loops
- 6 characters support 62⁶ ≈ 56 billion URLs

### Duplicate URL handling

Each `POST /shorten` creates a **new short code**, even for the same URL. This is intentional: multiple campaigns can independently track clicks to the same destination.

### Custom aliases

- 3–30 characters, alphanumeric + hyphens only
- Validated via Bean Validation annotations
- Checked for uniqueness before insertion (409 Conflict if taken)

## Project Structure

```
src/main/java/com/urlshortener/
├── UrlShortenerApplication.java     # Entry point
├── controller/
│   ├── UrlShortenerController.java  # POST /shorten, GET /stats
│   └── RedirectController.java      # GET /{code} → 301
├── service/
│   └── UrlShortenerService.java     # Business logic
├── repository/
│   └── UrlMappingRepository.java    # Spring Data JPA
├── entity/
│   └── UrlMapping.java              # JPA entity
├── dto/
│   ├── ShortenRequest.java
│   ├── ShortenResponse.java
│   ├── UrlStatsResponse.java
│   └── ErrorResponse.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── UrlNotFoundException.java
│   ├── AliasAlreadyExistsException.java
│   └── InvalidUrlException.java
└── util/
    ├── Base62Encoder.java
    └── UrlValidator.java
```

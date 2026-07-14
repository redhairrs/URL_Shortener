# Write-Up

## 1. What did you ask the AI to do, and what did you write or decide yourself?

I used the AI assistant to scaffold the project structure, generate boilerplate code (entity, repository, DTOs, exception classes), and produce the initial test suite. The AI handled the repetitive, well-understood parts: Maven POM setup, JPA annotations, controller request mapping, and test structure.

**What I decided myself:**

- **Short-code generation strategy**: I chose Base62 encoding of auto-increment IDs over alternatives (UUID truncation, random strings with collision checks, hash-based). The reasoning: it's mathematically collision-free, requires no retry logic, and produces compact codes. I explained this trade-off to the AI and directed it to implement this specific approach.
- **Duplicate-URL policy**: I made the deliberate choice that each `POST /shorten` creates a new short code, even for the same URL. This supports independent campaign tracking. The alternative (idempotent — return existing code) is simpler but loses per-campaign analytics.
- **Layered architecture**: I specified the Controller → Service → Repository pattern and the separation of RedirectController from UrlShortenerController, keeping redirect logic (which serves end-users) separate from the API (which serves developers).
- **Error handling strategy**: I designed the exception hierarchy and decided which HTTP status codes to use for each error case.

## 2. Where did you override, correct, or throw away the AI's output — and why?

- **Base62 encoder**: The AI's initial suggestion used `Math.random()` for code generation. I rejected this entirely because it introduces collision risk. I directed it to implement deterministic Base62 encoding of the database ID instead, which is guaranteed unique.
- **Placeholder save pattern**: For the generated code flow, the service needs the auto-increment ID before it can compute the short code. The AI initially tried to generate the code before saving, which is impossible with auto-increment. I designed the two-step save (save with placeholder → encode ID → update short code).
- **URL validation**: I tightened the AI's validation to reject non-HTTP/HTTPS protocols (blocking `file://`, `ftp://`, `javascript:` schemes) and to require a non-empty host.
- **Test assertions**: I reviewed and strengthened several test assertions, particularly the round-trip test that verifies click count increments correctly after multiple redirects.

## 3. The two or three biggest trade-offs you made, and the alternatives you considered

### Trade-off 1: Feistel-Scrambled Base62 ID vs. Random short codes

**Chose**: Base62 encoding of a Feistel-scrambled auto-increment ID.
**Alternative**: Random 6-character string with uniqueness check + retry, or simple XOR masking.
**Why**: The 2-round Feistel cipher provides deterministic, non-sequential, and collision-free ID generation — no retry loops, no race conditions, O(1) generation. The output is pseudo-random, preventing enumeration attacks (guessing the total URL count) without requiring external dependencies like Hashids.

### Trade-off 2: Caffeine Cache for Redirects vs. Immediate DB Write for Click Counts

**Chose**: In-memory Caffeine cache for the `shortCode → originalUrl` mapping lookup, while still updating `clickCount` synchronously in the database on every redirect.
**Alternative**: Cache the full mapping and buffer/batch click count updates to the database asynchronously.
**Why**: Caching the lookup serves the hot read path without introducing data loss or consistency issues for analytics. While batched writes would scale better under extreme load, synchronous writes are simpler and ensure the stats endpoint is always perfectly accurate. The trade-off is a slightly higher database write load, but the heavy lifting of the read query is bypassed.

### Trade-off 3: New code per duplicate URL vs. idempotent shortening

**Chose**: Each `POST /shorten` generates a new short code, even for the same original URL.
**Alternative**: Return the existing short code if the URL has already been shortened (idempotent).
**Why**: Non-idempotent allows multiple campaigns to track the same destination independently. The trade-off is slightly more storage, but the analytics benefit outweighs it. This is also a common pattern in production URL shorteners (Bitly, for example).

## 4. What's missing, or what you'd do with another day?

- **Observability**: No metrics or structured logging. I'd add Micrometer metrics (shorten rate, redirect latency, cache hit ratio) and structured JSON logging.
- **Security hardening**: No CORS configuration, no input sanitization beyond URL validation, no protection against open-redirect attacks (where the original URL is itself a redirect).
- **Pagination on stats**: If analytics grew to include per-click data (or if we retrieved a list of top URLs), the stats endpoint would need pagination.
- **High Availability Caching**: The current Caffeine cache is purely local. In a multi-node deployment, I'd switch to Redis for a distributed caching layer.

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

### Trade-off 1: Base62(auto-increment ID) vs. random short codes

**Chose**: Base62 encoding of the auto-increment ID.
**Alternative**: Random 6-character string with uniqueness check + retry.
**Why**: Base62(ID) is deterministic and collision-free — no retry loops, no race conditions, O(1) generation. The downside is predictability (sequential codes are guessable), but for this exercise, simplicity and correctness matter more than obscurity. In production, I'd add a random offset or use a Snowflake-style ID generator.

### Trade-off 2: Denormalized click counter vs. separate clicks table

**Chose**: A `click_count` column on the `urls` table, incremented on each redirect.
**Alternative**: A separate `clicks` table with one row per click (storing timestamp, referrer, IP, etc.).
**Why**: The denormalized counter is simpler, avoids JOINs for the stats endpoint, and is sufficient for the exercise scope (total count + last access time). The separate table would be necessary for time-series analytics, geo breakdown, or referrer tracking — but that's scope I intentionally deferred.

### Trade-off 3: New code per duplicate URL vs. idempotent shortening

**Chose**: Each `POST /shorten` generates a new short code, even for the same original URL.
**Alternative**: Return the existing short code if the URL has already been shortened (idempotent).
**Why**: Non-idempotent allows multiple campaigns to track the same destination independently. The trade-off is slightly more storage, but the analytics benefit outweighs it. This is also a common pattern in production URL shorteners (Bitly, for example).

## 4. What's missing, or what you'd do with another day?

- **Rate limiting**: No request throttling. I'd add Spring's `Bucket4j` or a simple in-memory rate limiter to prevent abuse.
- **Caching**: Redirect lookups hit the database on every request. A Redis or Caffeine cache in front of `findByShortCode` would dramatically reduce latency.
- **Expiration / TTL**: Short URLs live forever. I'd add an optional `expiresAt` column and a scheduled cleanup job.
- **Observability**: No metrics or structured logging. I'd add Micrometer metrics (shorten rate, redirect latency, cache hit ratio) and structured JSON logging.
- **Security hardening**: No CORS configuration, no input sanitization beyond URL validation, no protection against open-redirect attacks (where the original URL is itself a redirect).
- **Docker Compose**: A `docker-compose.yml` for one-command local setup with MySQL.
- **Pagination on stats**: If analytics grew to include per-click data, the stats endpoint would need pagination.
- **Short code obscurity**: Base62(ID) is sequential and guessable. For production, I'd XOR the ID with a secret key or use a pseudo-random permutation to make codes non-sequential while remaining collision-free.

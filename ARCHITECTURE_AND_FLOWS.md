# System Architecture & Flow Diagrams

This document contains a comprehensive visual guide to the system architecture, component interactions, runtime flowcharts, and sequence diagrams of the URL Shortener project.

![System Architecture Concept Diagram](/C:/Users/preyr/.gemini/antigravity-ide/brain/ce7e6ebb-2489-4b52-bdbb-ebd64eed1fca/system_architecture_diagram_1784055530059.png)

---

## 1. High-Level System Architecture

The following diagram illustrates how external traffic routes through the system components to either the in-memory Redis cache layer or the persistent database layer.

```mermaid
graph TB
    Client((User Client / Browser))
    
    subgraph VPC / Network Boundary
        direction TB
        
        subgraph Web Layer
            LB[Load Balancer / Ingress]
            App1[Spring Boot App Instance 1]
            App2[Spring Boot App Instance 2]
        end
        
        subgraph Cache Layer
            Redis[(Redis Cache Cluster<br/>Port 6379)]
        end
        
        subgraph Data Layer
            DB[(MySQL 8 Database<br/>Port 3306)]
        end
    end
    
    %% Traffic flows
    Client -->|HTTP Request| LB
    LB -->|Round Robin / Path Routing| App1
    LB -->|Round Robin / Path Routing| App2
    
    %% App to Caching & Database
    App1 <-->|Read / Write Cached URLs| Redis
    App2 <-->|Read / Write Cached URLs| Redis
    
    App1 <-->|SQL Data Persistence| DB
    App2 <-->|SQL Data Persistence| DB
    
    %% Styling
    classDef client fill:#d4ebf2,stroke:#0b84a5,stroke-width:2px;
    classDef lb fill:#f2d4e0,stroke:#a50b5e,stroke-width:2px;
    classDef app fill:#e2f0cb,stroke:#5ba300,stroke-width:2px;
    classDef cache fill:#ffe7d1,stroke:#e65100,stroke-width:2px;
    classDef db fill:#e8e3f5,stroke:#4a148c,stroke-width:2px;
    
    class Client client;
    class LB lb;
    class App1,App2 app;
    class Redis cache;
    class DB db;
```

---

## 2. API End-to-End Execution Flows

### 2.1 Write Flow: URL Shortening Request

This flowchart shows the logical decision steps when a user requests to shorten a URL, including validation, rate-limiting check, alias lookup, Feistel cipher generation, and persistence.

```mermaid
flowchart TD
    Start([User sends POST /api/v1/shorten]) --> ExtIP[Extract IP Address]
    ExtIP --> CheckRate{Is request within Rate Limit?}
    
    %% Rate Limit branch
    CheckRate -- No --> Status429[Return HTTP 429 Too Many Requests]
    
    %% Rate Limit OK branch
    CheckRate -- Yes --> ValidBody{Is request body valid?}
    ValidBody -- No --> Status400[Return HTTP 400 Bad Request]
    
    ValidBody -- Yes --> CheckAlias{Custom Alias specified?}
    
    %% Custom Alias Path
    CheckAlias -- Yes --> QueryAliasDB{Does Alias exist in DB?}
    QueryAliasDB -- Yes --> Status409[Return HTTP 409 Conflict]
    QueryAliasDB -- No --> SaveAlias[Save URL Mapping with Custom Alias]
    SaveAlias --> ReturnShort[Generate ShortenResponse and Return HTTP 201 Created]
    
    %% Auto-Generate Code Path
    CheckAlias -- No --> GenPlaceholder[Insert placeholder to DB & get Auto-Increment ID]
    GenPlaceholder --> FeistelScramble[Apply 2-Round Feistel Cipher to ID]
    FeistelScramble --> Base62Encode[Base62 Encode Scrambled ID to Short Code]
    Base62Encode --> UpdateDB[Update row in DB with final Short Code]
    UpdateDB --> ReturnShort
    
    Status429 --> End([End Request])
    Status400 --> End
    Status409 --> End
    ReturnShort --> End
```

### 2.2 Read Flow: URL Redirection Hot-Path

This sequence flowchart shows how the application uses Redis to serve redirects at sub-millisecond speeds, checking expiration, and handling database falls.

```mermaid
flowchart TD
    Start(["User navigates to GET /{code}"]) --> CheckCache{Check Redis Cache for Short Code}
    
    %% Cache Hit
    CheckCache -- Hit --> ParseMapping[Retrieve original URL & Expiration time]
    
    %% Cache Miss
    CheckCache -- Miss --> QueryDB{Query MySQL DB for Short Code}
    QueryDB -- Not Found --> Status404[Return HTTP 404 Not Found]
    QueryDB -- Found --> PopulateCache[Write Mapping to Redis Cache with 10m TTL]
    PopulateCache --> ParseMapping
    
    %% Expire checks
    ParseMapping --> CheckExpired{Has URL expired?}
    CheckExpired -- Yes --> EvictCache[Evict Short Code from Redis Cache]
    EvictCache --> Status410[Return HTTP 410 Gone / Expired]
    
    %% Track analytics
    CheckExpired -- No --> AsyncStats[Increment click_count in MySQL DB]
    AsyncStats --> Redirect[Return HTTP 302 Found with Location header]
    
    Status404 --> End([End Request])
    Status410 --> End
    Redirect --> End
```

---

## 3. Data Lifecycle & State Transition

A shortened URL is represented as a state machine. It is created, accessed (redirected), updated, and eventually deleted either manually or automatically through the cleanup daemon.

```mermaid
stateDiagram-v2
    [*] --> Unsaved : Request Received
    
    state Unsaved {
        [*] --> ValidateURL
        ValidateURL --> GenerateCode
    }
    
    Unsaved --> Active : Saved to MySQL & Cached in Redis
    
    state Active {
        [*] --> RedirectServe : User visits URL
        RedirectServe --> CheckExpiration
        CheckExpiration --> Serve302 : Not Expired
        CheckExpiration --> Expired : Expired
    }
    
    Serve302 --> Active : Incremented click_count in MySQL
    
    Expired --> Deleted : ExpirationCleanupService runs hourly DELETE
    Active --> Deleted : Manual Eviction / Purge
    
    Deleted --> [*]
```

---

## 4. Class & Component Relationships

The diagram below details the Spring Boot project components, how they depend on one another, and their single responsibilities.

```mermaid
classDiagram
    class RateLimitInterceptor {
        -Map IPBuckets
        +preHandle(Request, Response) boolean
    }
    
    class UrlShortenerController {
        -UrlShortenerService service
        +shortenUrl(ShortenRequest) ResponseEntity
        +getStats(String) ResponseEntity
    }
    
    class RedirectController {
        -UrlShortenerService service
        +redirectToOriginal(String) ResponseEntity
    }
    
    class UrlShortenerService {
        -UrlMappingRepository repository
        -ShortCodeGenerator generator
        -UrlShortenerService selfProxy
        +shortenUrl(String, String, LocalDateTime) UrlMapping
        +resolveAndTrack(String) String
        +getStats(String) UrlMapping
    }
    
    class ShortCodeGenerator {
        -long scrambleKey
        +generate(long id) String
        -feistelScramble(long val) long
        -base62Encode(long val) String
    }
    
    class ExpirationCleanupService {
        -UrlMappingRepository repository
        +cleanupExpiredUrls() void
    }
    
    class UrlMappingRepository {
        <<interface>>
        +findByShortCode(String) Optional
        +existsByShortCode(String) boolean
        +deleteByExpiresAtBefore(LocalDateTime) int
    }

    UrlShortenerController --> UrlShortenerService : Delegating Business Logic
    RedirectController --> UrlShortenerService : Fetching mapping for redirects
    UrlShortenerService --> UrlMappingRepository : SQL operations
    UrlShortenerService --> ShortCodeGenerator : Feistel calculation
    ExpirationCleanupService --> UrlMappingRepository : Scheduled cleanup task
    RateLimitInterceptor ..> UrlShortenerController : Intercepts POST requests
```

---

## 5. UML Entity Relationship Diagram (ERD)

This physical ER diagram shows the schema structure of the `urls` entity, including types, keys, indexes, and nullability.

```mermaid
erDiagram
    URL_MAPPING ||--|| URL_ANALYTICS : maintains
    
    URL_MAPPING {
        bigint id PK "Auto-Increment"
        varchar short_code UK "Unique, length 30"
        varchar original_url "Length 2048, Not Null"
        boolean is_custom "Default false"
        timestamp expires_at "Nullable"
        timestamp created_at "Default Current Timestamp"
    }

    URL_ANALYTICS {
        bigint id FK "Refers to URL_MAPPING"
        bigint click_count "Default 0"
        timestamp last_accessed_at "Nullable"
    }
```

---

## 6. Formal UML Sequence Diagrams

### 6.1 UML Sequence: Creating a Short URL

This sequence diagram uses formal UML representations (Boundary, Control, Entity, Database) to map object interactions.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / Browser
    box Application Tier
        boundary API as UrlShortenerController
        control Service as UrlShortenerService
        control Feistel as ShortCodeGenerator
    end
    box Database Tier
        database DB as MySQL Database
    end

    Client->>API: POST /api/v1/shorten (request)
    activate API
    API->>Service: shortenUrl(originalUrl, alias, expires)
    activate Service
    
    alt Custom Alias Path
        Service->>DB: existsByShortCode(alias)
        activate DB
        DB-->>Service: boolean
        deactivate DB
        
        alt Alias Already Exists
            Service-->>API: throws AliasAlreadyExistsException
            API-->>Client: HTTP 409 Conflict
        else Alias Available
            Service->>DB: save(UrlMapping)
            activate DB
            DB-->>Service: saved Entity
            deactivate DB
        end
    else Auto-Generate Path
        Service->>DB: save(Placeholder Mapping)
        activate DB
        DB-->>Service: Entity with ID (e.g. 101)
        deactivate DB
        
        Service->>Feistel: generate(101)
        activate Feistel
        Feistel-->>Service: short code (e.g. "aB3")
        deactivate Feistel
        
        Service->>DB: update short_code where id=101
        activate DB
        DB-->>Service: updated Entity
        deactivate DB
    end
    
    Service-->>API: ShortenResponse DTO
    deactivate Service
    API-->>Client: HTTP 201 Created (JSON body)
    deactivate API
```

### 6.2 UML Sequence: Redirecting a Short URL (GET Path)

```mermaid
sequenceDiagram
    autonumber
    actor Client as User Client
    box Application Tier
        boundary API as RedirectController
        control ServiceProxy as UrlShortenerService (Proxy)
        control Service as UrlShortenerService (Target)
    end
    box Cache / Persistence Tier
        participant Cache as Redis Cache
        database DB as MySQL Database
    end

    Client->>API: GET /{shortCode}
    activate API
    API->>ServiceProxy: resolveAndTrack(shortCode)
    activate ServiceProxy
    
    ServiceProxy->>Cache: Check for cached mapping
    activate Cache
    
    alt Cache Hit
        Cache-->>ServiceProxy: cached UrlMapping JSON
    else Cache Miss
        Cache-->>ServiceProxy: null
        ServiceProxy->>Service: getCachedMapping(shortCode)
        activate Service
        Service->>DB: findByShortCode(shortCode)
        activate DB
        DB-->>Service: Optional<UrlMapping>
        deactivate DB
        Service-->>ServiceProxy: UrlMapping
        deactivate Service
        
        ServiceProxy->>Cache: Write cache (TTL 10m)
    end
    deactivate Cache
    
    alt URL is Expired
        ServiceProxy-->>API: throws UrlExpiredException
        API-->>Client: HTTP 410 Gone
    else URL is Active
        Note over ServiceProxy,DB: Click increment runs synchronously
        ServiceProxy->>DB: incrementClickCount(id)
        activate DB
        DB-->>ServiceProxy: updated
        deactivate DB
        
        ServiceProxy-->>API: original URL String
        deactivate ServiceProxy
        API-->>Client: HTTP 302 Found (Location Header)
    end
    deactivate API
```

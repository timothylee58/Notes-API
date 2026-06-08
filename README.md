# notes-api-springboot

A production-grade RESTful Notes API built with **Spring Boot 3.x / Java 21**, demonstrating enterprise Java patterns: JWT auth, layered architecture, keyset pagination, Redis caching, Flyway migrations, Testcontainers, and Railway deployment via GitHub Actions CI/CD.

> **Portfolio signal:** Spring Security filter chain wiring, JPA/Hibernate optimisation, idiomatic Maven multi-module layout, and Docker-first local dev — the patterns expected at banks, SIs, and enterprise tech companies in MY/SG.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [API Reference](#api-reference)
5. [Auth Flow](#auth-flow)
6. [Pagination Strategy](#pagination-strategy)
7. [Data Model & Migrations](#data-model--migrations)
8. [Redis Usage](#redis-usage)
9. [Running Locally](#running-locally)
10. [Running Tests](#running-tests)
11. [Environment Variables](#environment-variables)
12. [CI/CD Pipeline](#cicd-pipeline)
13. [Deployment (Railway)](#deployment-railway)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        HTTP Client                          │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTPS
┌────────────────────────────▼────────────────────────────────┐
│              Spring Security Filter Chain                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  JwtAuthenticationFilter  →  SecurityContextHolder   │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                  Controller Layer (@RestController)         │
│   AuthController   │   NoteController   │  UserController   │
└────────────────────────────┬────────────────────────────────┘
                             │ DTOs (request / response)
┌────────────────────────────▼────────────────────────────────┐
│                   Service Layer (@Service)                   │
│   AuthService  │  NoteService  │  TokenBlacklistService     │
└────────┬───────────────────────────────────┬────────────────┘
         │ JPA Repositories                  │ Redis
┌────────▼────────────────┐   ┌──────────────▼───────────────┐
│  PostgreSQL 16 (Railway) │   │   Redis (Railway)            │
│  ┌──────────┐ ┌───────┐ │   │  • JWT blacklist (logout)    │
│  │  users   │ │ notes │ │   │  • Rate-limit counters       │
│  └──────────┘ └───────┘ │   └──────────────────────────────┘
│  Flyway migrations       │
└─────────────────────────┘
```

### Layer Responsibilities

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `controller` | HTTP mapping, input validation (`@Valid`), response shaping |
| Service | `service` | Business logic, transaction boundaries (`@Transactional`) |
| Repository | `repository` | Spring Data JPA interfaces + custom JPQL/native queries |
| Entity | `entity` | JPA-mapped domain objects |
| DTO | `dto/request`, `dto/response` | API contract types; kept separate from entities |
| Security | `security` | JWT filter, `UserDetailsService` impl, `SecurityConfig` |
| Config | `config` | Bean wiring — Redis, security, OpenAPI |
| Exception | `exception` | `@ControllerAdvice` global error handler, problem detail responses |

---

## Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Java 21 LTS | Virtual threads (Loom), records, pattern matching; LTS = production-safe |
| Framework | Spring Boot 3.3 | Industry standard for enterprise MY/SG; battle-tested ecosystem |
| REST | Spring Web (MVC) | Annotation-driven controllers, content negotiation, exception handling |
| Auth | Spring Security + jjwt 0.12 | Stateless JWT with refresh-token rotation; blacklist via Redis |
| ORM | Spring Data JPA + Hibernate 6 | Reduces boilerplate; native queries where perf matters |
| Database | PostgreSQL 16 | ACID, JSON support, keyset pagination with cursors |
| Migrations | Flyway | SQL-first versioned migrations; audit trail; CI-safe |
| Cache / Rate-limit | Spring Data Redis (Lettuce) | Token blacklist + sliding-window rate limiter |
| Build | Maven 3.9 | Standard enterprise build; plugin ecosystem; CI caching |
| Container | Docker + Compose | Local dev parity with Railway prod |
| CI/CD | GitHub Actions | Build → test → Docker push → Railway deploy |
| Deploy | Railway | Managed PaaS; no cold starts; built-in PostgreSQL + Redis |
| Testing | JUnit 5 + Mockito + Testcontainers | Unit, slice, and integration tests against real Postgres/Redis |
| API Docs | springdoc-openapi (Swagger UI) | Auto-generated from annotations; `/swagger-ui.html` |

---

## Project Structure

```
notes-api-springboot/
├── src/
│   ├── main/
│   │   ├── java/com/notesapi/
│   │   │   ├── NotesApiApplication.java          # Entry point
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java           # Filter chain, CORS, CSRF
│   │   │   │   ├── RedisConfig.java              # RedisTemplate beans
│   │   │   │   └── OpenApiConfig.java            # springdoc JWT bearer scheme
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java           # /api/v1/auth/**
│   │   │   │   ├── NoteController.java           # /api/v1/notes/**
│   │   │   │   └── UserController.java           # /api/v1/users/me
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── NoteCreateRequest.java
│   │   │   │   │   └── NoteUpdateRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── AuthResponse.java         # access + refresh tokens
│   │   │   │       ├── NoteResponse.java
│   │   │   │       ├── PageResponse.java         # keyset cursor wrapper
│   │   │   │       └── UserResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── User.java                     # @Entity, Spring Security UserDetails
│   │   │   │   └── Note.java                     # @Entity, soft-delete flag
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java   # @ControllerAdvice, RFC 7807
│   │   │   │   ├── NoteNotFoundException.java
│   │   │   │   ├── UserAlreadyExistsException.java
│   │   │   │   └── InvalidTokenException.java
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── NoteRepository.java           # keyset pagination native query
│   │   │   ├── security/
│   │   │   │   ├── JwtService.java               # token generation & validation
│   │   │   │   ├── JwtAuthenticationFilter.java  # OncePerRequestFilter
│   │   │   │   └── UserDetailsServiceImpl.java
│   │   │   └── service/
│   │   │       ├── AuthService.java
│   │   │       ├── NoteService.java
│   │   │       ├── TokenBlacklistService.java    # Redis-backed logout
│   │   │       └── RateLimitService.java         # sliding-window via Redis
│   │   └── resources/
│   │       ├── application.yml                   # base config
│   │       ├── application-local.yml             # local overrides (gitignored)
│   │       ├── application-test.yml              # Testcontainers config
│   │       └── db/migration/
│   │           ├── V1__create_users.sql
│   │           ├── V2__create_notes.sql
│   │           └── V3__add_notes_indexes.sql
│   └── test/
│       └── java/com/notesapi/
│           ├── controller/                       # @WebMvcTest slices
│           ├── service/                          # @ExtendWith(MockitoExtension)
│           └── repository/                       # @DataJpaTest + Testcontainers
├── docker/
│   └── Dockerfile                               # multi-stage JDK 21 image
├── docker-compose.yml                           # local Postgres + Redis
├── docker-compose.test.yml                      # Testcontainers override
├── .github/
│   └── workflows/
│       └── ci-cd.yml                            # build → test → push → deploy
├── pom.xml
└── README.md
```

---

## API Reference

All endpoints are prefixed `/api/v1`. Authenticated routes require `Authorization: Bearer <access_token>`.

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Create account; returns token pair |
| `POST` | `/auth/login` | Public | Validate credentials; returns token pair |
| `POST` | `/auth/refresh` | Public | Exchange refresh token for new access token |
| `POST` | `/auth/logout` | Bearer | Blacklist current access token in Redis |

### Notes

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/notes` | Bearer | List own notes (keyset paginated) |
| `POST` | `/notes` | Bearer | Create a note |
| `GET` | `/notes/{id}` | Bearer | Get single note |
| `PUT` | `/notes/{id}` | Bearer | Full update |
| `PATCH` | `/notes/{id}` | Bearer | Partial update (title or content) |
| `DELETE` | `/notes/{id}` | Bearer | Soft-delete (marks `deleted_at`) |

#### Pagination query params (`GET /notes`)

| Param | Type | Default | Description |
|---|---|---|---|
| `cursor` | `string` | — | Opaque base64 keyset cursor from previous response |
| `limit` | `int` | `20` | Page size (max 100) |
| `sort` | `string` | `createdAt` | Sort field: `createdAt` \| `updatedAt` \| `title` |
| `direction` | `string` | `DESC` | `ASC` \| `DESC` |

#### Example paginated response

```json
{
  "data": [
    {
      "id": "01J0...",
      "title": "Design patterns",
      "content": "...",
      "createdAt": "2024-06-01T10:00:00Z",
      "updatedAt": "2024-06-01T10:00:00Z"
    }
  ],
  "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI0LTA2...",
  "hasMore": true,
  "limit": 20
}
```

### Users

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/users/me` | Bearer | Get own profile |
| `PATCH` | `/users/me` | Bearer | Update display name |
| `DELETE` | `/users/me` | Bearer | Delete account (cascades notes) |

---

## Auth Flow

```
Client                              API
  │                                  │
  │── POST /auth/register ──────────►│ hash password (BCrypt 12)
  │◄── 201 { accessToken, refreshToken } ──│ store hashed refresh token
  │                                  │
  │── GET /notes (Authorization: Bearer <accessToken>) ──►│
  │                                  │ JwtAuthenticationFilter validates
  │                                  │ checks Redis blacklist
  │◄── 200 notes ───────────────────│
  │                                  │
  │── POST /auth/refresh ───────────►│ validate refresh token hash
  │◄── 200 { new accessToken } ─────│ rotate: invalidate old, issue new
  │                                  │
  │── POST /auth/logout ────────────►│ add jti to Redis blacklist (TTL = token expiry)
  │◄── 204 ─────────────────────────│
```

**Token lifetimes (configurable):**
- Access token: 15 minutes
- Refresh token: 7 days (stored as SHA-256 hash in DB)

---

## Pagination Strategy

Offset pagination (`LIMIT x OFFSET y`) degrades at scale. This API uses **keyset (cursor) pagination**:

```sql
-- Forward page after cursor (createdAt DESC)
SELECT * FROM notes
WHERE user_id = :userId
  AND deleted_at IS NULL
  AND (created_at, id) < (:cursorCreatedAt, :cursorId)
ORDER BY created_at DESC, id DESC
LIMIT :limit;
```

The cursor is a base64-encoded JSON object `{"createdAt": "...", "id": "..."}` — opaque to clients, stable across inserts.

**Why keyset over offset:**
- O(log n) via index seek vs O(n) for offset scans
- No missing/duplicate rows when items are inserted between pages
- Consistent performance at millions of rows

---

## Data Model & Migrations

### Flyway versioning

| Version | File | Description |
|---|---|---|
| V1 | `V1__create_users.sql` | `users` table with BCrypt password, timestamps |
| V2 | `V2__create_notes.sql` | `notes` table with `user_id` FK, soft-delete `deleted_at` |
| V3 | `V3__add_notes_indexes.sql` | Composite index `(user_id, created_at DESC, id DESC)` for keyset |

### Entity relationship

```
users
  id            UUID  PK
  email         TEXT  UNIQUE NOT NULL
  display_name  TEXT
  password_hash TEXT  NOT NULL
  created_at    TIMESTAMPTZ DEFAULT now()
  updated_at    TIMESTAMPTZ DEFAULT now()

notes
  id          UUID  PK  DEFAULT gen_random_uuid()
  user_id     UUID  FK → users(id) ON DELETE CASCADE
  title       TEXT  NOT NULL
  content     TEXT
  deleted_at  TIMESTAMPTZ  (NULL = active)
  created_at  TIMESTAMPTZ DEFAULT now()
  updated_at  TIMESTAMPTZ DEFAULT now()
```

---

## Redis Usage

| Key pattern | TTL | Purpose |
|---|---|---|
| `jwt:blacklist:<jti>` | Token remaining TTL | Logout token invalidation |
| `ratelimit:<userId>:<windowStart>` | 60 s | Sliding-window rate limiter (100 req/min) |

---

## Running Locally

**Prerequisites:** Docker, Java 21, Maven 3.9

```bash
# 1. Clone
git clone https://github.com/timothylee58/notes-api.git
cd notes-api

# 2. Start Postgres + Redis
docker compose up -d

# 3. Copy env template and fill in secrets
cp .env.example .env.local

# 4. Run the app (local profile reads .env.local via spring-dotenv)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# API base URL
open http://localhost:8080/swagger-ui.html
```

---

## Running Tests

```bash
# Unit + slice tests (no Docker needed)
./mvnw test

# Integration tests (Testcontainers — requires Docker)
./mvnw verify -P integration-test

# Coverage report (JaCoCo)
./mvnw verify
open target/site/jacoco/index.html
```

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | DB user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | DB password |
| `SPRING_DATA_REDIS_URL` | Yes | Redis URL (`redis://...`) |
| `JWT_SECRET` | Yes | HS256 key, min 32 bytes, base64 |
| `JWT_ACCESS_EXPIRY_MS` | No | Default `900000` (15 min) |
| `JWT_REFRESH_EXPIRY_MS` | No | Default `604800000` (7 days) |
| `APP_RATE_LIMIT_RPM` | No | Default `100` |

---

## CI/CD Pipeline

```
push / PR to main
        │
        ▼
┌───────────────────────────────────────────┐
│  GitHub Actions: ci-cd.yml                │
│                                           │
│  1. Checkout + Java 21 setup              │
│  2. Maven cache restore                   │
│  3. ./mvnw verify (unit + integration)    │
│  4. Docker build (multi-stage)            │
│  5. Push image → GitHub Container Registry│
│  6. Deploy to Railway via Railway CLI     │
└───────────────────────────────────────────┘
```

Pipeline enforces: green tests → image build → deploy. No deploy on test failure.

---

## Deployment (Railway)

Infrastructure declared as code via `railway.toml` (in repo root):

```
notes-api (Spring Boot container)
    ├── PostgreSQL 16 (Railway managed)
    └── Redis (Railway managed)
```

- **Always-on:** no cold starts (unlike serverless)
- **Auto TLS:** Railway terminates HTTPS
- **Health check:** `GET /actuator/health` — Railway uses this to gate deploys

---

## License

MIT

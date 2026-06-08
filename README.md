# notes-api-springboot

![CI](https://img.shields.io/github/actions/workflow/status/timothylee58/notes-api-springboot/ci-cd.yml?branch=main&label=build)
![Java](https://img.shields.io/badge/java-21-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/spring--boot-3.3-brightgreen?logo=springboot)
![License](https://img.shields.io/badge/license-MIT-green)

A production-grade RESTful Notes API built with **Spring Boot 3.x / Java 21**, demonstrating enterprise Java patterns: stateless JWT authentication with Redis-backed token blacklist, keyset cursor pagination, Flyway SQL migrations, layered DTO/entity architecture, RFC 7807 Problem Detail error responses, and Railway deployment via GitHub Actions CI/CD.

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
│  │  JwtAuthFilter  →  SecurityContextHolder             │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                  Controller Layer (@RestController)         │
│         AuthController          │      NoteController       │
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
│  │  users   │ │ notes │ │   └──────────────────────────────┘
│  └──────────┘ └───────┘ │
│  Flyway migrations       │
└─────────────────────────┘
```

---

## Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Java 21 LTS | Virtual threads (Loom), records, pattern matching; LTS = production-safe |
| Framework | Spring Boot 3.3 | Industry standard; battle-tested ecosystem |
| Auth | Spring Security + jjwt 0.12.3 | Stateless JWT with refresh-token rotation; blacklist via Redis |
| ORM | Spring Data JPA + Hibernate 6 | Reduces boilerplate; native queries where perf matters |
| Database | PostgreSQL 16 | ACID, array types, keyset pagination with cursors |
| Migrations | Flyway | SQL-first versioned migrations; audit trail; CI-safe |
| Cache | Spring Data Redis (Lettuce) | Token blacklist for stateless logout |
| Build | Maven 3.9 | Standard enterprise build; CI caching |
| Container | Docker + Compose | Local dev parity with Railway prod |
| CI/CD | GitHub Actions | Build → test → Docker smoke test |
| Deploy | Railway | Managed PaaS; built-in PostgreSQL + Redis |
| API Docs | springdoc-openapi (Swagger UI) | Auto-generated; `/swagger-ui.html` |

---

## MVP Scope

### v1 — In Scope

| Feature | Detail |
|---|---|
| Auth | Registration + login, BCrypt password hashing (cost 12) |
| JWT | Access token (15 min) + refresh token (7 d); logout via Redis blacklist |
| Notes CRUD | `title`, `content`, `tags[]`, `created_at`, `updated_at`; scoped to authenticated user |
| Pagination | Keyset cursor on `GET /api/v1/notes` (`cursor`, `limit` params) |
| Validation | `@Valid`, `@NotBlank`, `@Size` on all request bodies |
| Error handling | RFC 7807 `ProblemDetail` via `@RestControllerAdvice` |
| Migrations | Flyway V1–V3 |
| Local dev | Docker + docker-compose (Postgres 16 + Redis 7) |
| CI | GitHub Actions — build + test on every push/PR to main/develop |
| Docs | Swagger UI at `/swagger-ui.html` |
| Deploy | Railway — app + PostgreSQL + Redis |

### v2+ — Out of Scope

- Note sharing / collaboration
- Full-text search
- File attachments
- Frontend UI
- Rate limiting
- OAuth2 / Google SSO

---

## Implementation Decisions

### 1. Keyset pagination over offset

Offset pagination (`LIMIT n OFFSET k`) requires the DB to scan and discard `k` rows on every request — it degrades linearly and produces duplicate/missing rows when rows are inserted between pages. Keyset pagination is an `O(log n)` index seek regardless of depth.

The cursor is a base64-encoded `Instant` timestamp. The query:
```sql
SELECT * FROM notes
WHERE user_id = :userId
  AND (:cursor IS NULL OR created_at < :cursor)
ORDER BY created_at DESC LIMIT :limit;
```

### 2. Flyway SQL migrations over JPA `ddl-auto`

Raw SQL (`V1__`, `V2__`, `V3__`) gives full control of indexes, constraints, and triggers. `ddl-auto: validate` is used in production — Flyway owns schema changes, Hibernate only validates. Enterprise teams always use a migration tool; the `V<n>__<description>.sql` naming is industry convention.

### 3. Redis JWT blacklist (logout-safe stateless auth)

On `POST /api/v1/auth/logout`, the token's `jti` (JWT ID) is stored in Redis with `TTL = remaining token expiry`. `JwtAuthFilter` checks the blacklist on every authenticated request. This is the correct pattern for stateless-but-logout-safe JWTs — no session state is kept server-side, and the Redis key is automatically evicted at token expiry.

### 4. DTO / entity separation

`Note.java` (JPA entity) never crosses the service boundary into controllers. `NoteResponse.java` (DTO record) is what controllers return. This decouples the API contract from the DB schema and prevents Hibernate lazy-loading surprises in Jackson serialisation.

### 5. RFC 7807 Problem Detail error responses

Spring Boot 3 ships `ProblemDetail` natively. All error responses follow the RFC 7807 shape, centralised in `GlobalExceptionHandler` (`@RestControllerAdvice`). Controllers never write error-response boilerplate.

---

## Running Locally

**Option A — Docker Compose (recommended)**

```bash
git clone https://github.com/timothylee58/notes-api-springboot.git
cd notes-api-springboot
docker-compose up --build
```

The app, Postgres, and Redis all start together. API available at `http://localhost:8080`.

**Option B — Manual**

```bash
# Start Postgres + Redis only
docker-compose up -d postgres redis

# Run the app
mvn spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## API Endpoints

All authenticated routes require `Authorization: Bearer <access_token>`.

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Create account; returns token pair |
| `POST` | `/api/v1/auth/login` | Public | Validate credentials; returns token pair |
| `POST` | `/api/v1/auth/refresh` | Public | Exchange refresh token for new access token |
| `POST` | `/api/v1/auth/logout` | Bearer | Blacklist current access token in Redis |
| `GET` | `/api/v1/notes` | Bearer | List own notes (keyset paginated) |
| `POST` | `/api/v1/notes` | Bearer | Create a note |
| `GET` | `/api/v1/notes/{id}` | Bearer | Get single note |
| `PUT` | `/api/v1/notes/{id}` | Bearer | Update a note |
| `DELETE` | `/api/v1/notes/{id}` | Bearer | Delete a note |

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/notesdb` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `postgres` | DB username |
| `DATABASE_PASSWORD` | `postgres` | DB password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password (optional) |
| `JWT_SECRET` | _(changeme)_ | HS256 signing key — min 32 bytes in production |

Copy `.env.example` to get started:
```bash
cp .env.example .env
```

---

## Deployment (Railway)

1. Create a new Railway project and add a **PostgreSQL** and **Redis** plugin.
2. Connect your GitHub repository to Railway.
3. Set environment variables (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`) in the Railway dashboard.
4. Railway automatically builds from the `Dockerfile` and deploys on every push to `main`.
5. Health check: `GET /actuator/health` — Railway uses this to gate deploys.

---

## License

MIT

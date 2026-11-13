---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 95b4c5958d79c54d98437fc801417993_92b51bf7df7111f1b140525400de85a5
    ReservedCode1: TDNaf0lemNr9dbU279wWKrQdyaEzpiwFdlX4VaZpNfXXX1wKm++Il7WGrDsywiLjSQ2Us2el6hAvZUGJlr/037xguUQNmu1UEt4YiTwCzvH1n73/3CK678C7EAYJyQ9cOTEiZNSJkegfXpjeI4QRG+LKS6f3w9j5vH7cLKffqu9GWE9ogNtjXJzOepg=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 95b4c5958d79c54d98437fc801417993_92b51bf7df7111f1b140525400de85a5
    ReservedCode2: TDNaf0lemNr9dbU279wWKrQdyaEzpiwFdlX4VaZpNfXXX1wKm++Il7WGrDsywiLjSQ2Us2el6hAvZUGJlr/037xguUQNmu1UEt4YiTwCzvH1n73/3CK678C7EAYJyQ9cOTEiZNSJkegfXpjeI4QRG+LKS6f3w9j5vH7cLKffqu9GWE9ogNtjXJzOepg=
---

# Library Service — Kotlin Backend Final Project

A production-style microservice for a **library management system** built with **Kotlin + Ktor 2.3.12 + PostgreSQL**, covering every item of the final-project checklist: REST API, three-plus tables, repository pattern, validated service layer, JWT auth with roles, unit + integration tests, Docker, CI/CD, caching, health checks, logging and centralised error handling.

---

## 1. Quick start

### 1.1 With Docker Compose (recommended)

```bash
docker compose up --build
```

* API: <http://localhost:8080>
* PostgreSQL: `localhost:5433` (mapped inside the network to `db:5432`, db/user/password = `library`)
* The schema (`authors`, `books`, `loans`, `users`) is created by the app on startup, and three accounts are seeded.

### 1.2 Locally (no Docker required)

```bash
# 1. make sure PostgreSQL 16 is running and create the database once
#    psql -U postgres -c "CREATE DATABASE library;"
# 2. point the service at it (all variables are optional, defaults below)
set DATABASE_URL=jdbc:postgresql://localhost:5432/library
set DATABASE_USER=postgres
set DATABASE_PASSWORD=postgres
set JWT_SECRET=library-service-dev-secret-change-me-in-production-0123456789

gradlew.bat clean build        # compile + all tests
gradlew.bat jar                # build/libs/library-service.jar
java -jar build/libs/library-service.jar
```

If the database is unreachable the service **still starts** with in-memory repositories; `/health` then reports `DOWN` (HTTP 503) while `/health/live` stays `UP`, so the degradation is visible but not fatal.

### 1.3 Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/library` | JDBC URL |
| `DATABASE_USER` | `postgres` | DB user |
| `DATABASE_PASSWORD` | `postgres` | DB password |
| `JWT_SECRET` | dev secret (see `JwtService`) | HS256 signing key — **must be overridden in production** |
| `JWT_ISSUER` | `library-service` | JWT `iss` claim |

Seeded accounts (created only when missing): `admin/admin123` (ADMIN), `librarian/librarian123` (LIBRARIAN), `reader/reader123` (USER).

---

## 2. Architecture

```
src/main/kotlin/com/library
├── Application.kt              # entry point, dependency wiring, seeding, DB→in-memory fallback
├── domain/Models.kt            # data classes + request/response DTOs (kotlinx.serialization)
├── repository/
│   ├── Repositories.kt         # 4 interfaces: Author/Book/Loan/User (repository pattern)
│   ├── postgres/               # JDBC implementations used in production
│   └── memory/                 # thread-safe in-memory implementations used by tests & degraded mode
├── service/                    # business logic + validation (Author, Book, Loan, Auth)
├── security/JwtService.kt      # HS256 token issuing/parsing, role claim
├── cache/BookCache.kt          # Caffeine cache + statistics snapshot
├── persistence/DatabaseFactory.kt  # connection, schema DDL, ping()
├── routes/                     # AuthRoutes, CatalogRoutes, LoanRoutes, Http helpers
└── plugins/                    # serialization, logging, error handling, metrics, health
```

Layering: **routes → services → repositories**. Routes only translate HTTP to service calls; services own validation and business rules; repositories own SQL (plain JDBC with `PreparedStatement`, no ORM magic). Nothing above the repository layer knows whether storage is PostgreSQL or in-memory — that is what makes the tests fast and the fallback possible.

### 2.1 Database schema (4 tables, 3 required)

| Table | Columns |
| --- | --- |
| `authors` | `id`, `name` (unique), `nationality`, `birth_year` |
| `books` | `id`, `title`, `author_id` → `authors.id`, `isbn` (unique), `genre`, `total_copies`, `available_copies`, `published_year` |
| `loans` | `id`, `book_id` → `books.id`, `borrower_name`, `borrowed_at`, `due_at`, `returned_at`, `status` |
| `users` | `id`, `username` (unique), `password_hash`, `role` |

Indexes: `idx_books_author`, `idx_loans_book`, `idx_loans_borrower`. DDL lives in `DatabaseFactory.schemaStatements()` and runs idempotently (`CREATE TABLE IF NOT EXISTS`).

---

## 3. API reference

All responses are JSON. Endpoints except `/health*`, `/metrics*`, `/api/auth/*` require `Authorization: Bearer <token>`.

### 3.1 Authentication

| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | public | Create a user (`username` ≥ 3 chars, `password` ≥ 6 chars) |
| POST | `/api/auth/login` | public | Returns `{token, username, role, expiresInMs}` |
| GET | `/api/auth/me` | any | Current principal (id, username, role) |
| GET | `/api/users` | ADMIN | List all users |

### 3.2 Authors

| Method | Path | Role | Description |
| --- | --- | --- | --- |
| GET | `/api/authors` | any | List authors |
| GET | `/api/authors/{id}` | any | Single author (404 when missing) |
| POST | `/api/authors` | ADMIN, LIBRARIAN | Create (name 2–80 chars, birth year 1000..current) |
| PUT | `/api/authors/{id}` | ADMIN, LIBRARIAN | Update |
| DELETE | `/api/authors/{id}` | ADMIN | Delete (409 while books still reference the author) |

### 3.3 Books (cached reads)

| Method | Path | Role | Description |
| --- | --- | --- | --- |
| GET | `/api/books` | any | List all |
| GET | `/api/books?q=clean` | any | Full-text-ish search on title/genre |
| GET | `/api/books/{id}` | any | **Cached** (Caffeine, TTL 5 min, cache-aside) |
| GET | `/api/books/author/{authorId}` | any | Books of one author |
| POST | `/api/books` | ADMIN, LIBRARIAN | Create (ISBN-10/13 regex, copies > 0) |
| PUT | `/api/books/{id}` | ADMIN, LIBRARIAN | Update (copies cannot drop below active loans) |
| DELETE | `/api/books/{id}` | ADMIN | Delete (409 while copies are on loan) |

### 3.4 Loans

| Method | Path | Role | Description |
| --- | --- | --- | --- |
| GET | `/api/loans` | any | List loans |
| GET | `/api/loans/overdue` | any | Loans past due and not returned |
| GET | `/api/loans/borrower/{name}` | any | Loans of one borrower |
| GET | `/api/loans/{id}` | any | Single loan |
| POST | `/api/loans` | ADMIN, LIBRARIAN | Borrow a copy (decrements `available_copies`, 409 when none left) |
| POST | `/api/loans/{id}/return` | ADMIN, LIBRARIAN | Return a copy (status `RETURNED`, restocks the book) |

### 3.5 Operations

| Method | Path | Description |
| --- | --- | --- |
| GET | `/health` | Aggregate health: `status=UP` (200) when the DB answers, otherwise `status=DEGRADED` with `database=DOWN` and **503**; always reports cache state, size and uptime |
| GET | `/health/live` | Liveness — always 200 while the process runs (`{"status":"UP",...}`) |
| GET | `/health/ready` | Readiness — 200 `READY` only when the database responds, else 503 `NOT_READY` |
| GET | `/metrics` | Prometheus exposition: `ktor_http_server_requests_seconds*`, `library_cache_hits`, `library_cache_size`, JVM/process meters |
| GET | `/metrics/cache` | JSON snapshot: `provider`, `hitCount`, `missCount`, `hitRate`, `evictionCount`, `size` |

Total: **24 business endpoints** plus 5 operational ones — well beyond the required 5.

### 3.6 Error model

Every failure returns a stable JSON envelope produced by `StatusPages`, e.g.

```json
{ "error": "Book with id=9999 not found", "timestamp": 1794580312989 }
{ "error": "Validation failed",
  "errors": ["title must not be blank", "isbn must be a valid ISBN-10 or ISBN-13"],
  "timestamp": 1794580312989 }
```

Mapping: `ValidationFailure → 400` (all field errors in one response), `NotFound → 404`, `Conflict → 409` (duplicates / business conflicts), malformed JSON → 400, unknown route → 404, missing/invalid token → 401, insufficient role → 403, unexpected exception → 500 (logged with stack trace, never leaked to the client).

---

## 4. Cross-cutting features

### 4.1 JWT authentication & roles

* HS256 via `com.auth0:java-jwt`, subject = user id, claims = `username`, `role`, `iss`, `exp` (2 h).
* `install(Authentication) { jwt("auth-jwt") { … } }`, then `authorize()` per route group.
* Role enforcement helper `authorize(principal, UserRole.ADMIN)` answers **403** when the role is insufficient (401 when the token is missing/invalid).

### 4.2 Caching

`GET /api/books/{id}` is cache-aside over a Caffeine cache (`expireAfterWrite=5min`, `maximumSize=1000`, `recordStats`). Write paths keep it consistent: `create`/`update` refresh the entry, `delete` evicts it, and `return` loan refreshes the affected book. Hit/miss counters are exported both as Prometheus gauges and as the `/metrics/cache` JSON.

### 4.3 Logging

Logback console appender, root `INFO`, `com.library` at `DEBUG`. Ktor `CallLogging` logs method, path, status and duration for every request; services log state transitions (loan created, copy returned, cache refresh) so failures are traceable from the log alone.

### 4.4 Validation

`service/Validator.kt` collects field-level errors into a map and every service returns `ServiceResult.ValidationFailure` — so a single request reports **all** invalid fields at once instead of one-by-one. Rules include: ISBN-10/13 regex, non-blank/trimmed names, length caps, positive copy counts, `publishedYear ∈ [1000, current year]`, `totalCopies ≥ active loans`, unique ISBN/username at the service level plus unique constraints at the DB level.

---

## 5. Tests

```bash
gradlew.bat test                    # everything
gradlew.bat test --tests "*BookServiceTest"   # one class
```

| Suite | Type | Tests | Focus |
| --- | --- | --- | --- |
| `AuthorServiceTest` | unit | 9 | CRUD happy paths, validation errors, duplicate handling, delete conflict |
| `BookServiceTest` | unit | 12 | validation (ISBN/copies/year), search, author lookup, cache population & eviction |
| `LoanServiceTest` | unit | 11 | borrow/return rules, copy accounting, overdue detection, invalid references |
| `AuthServiceTest` | unit | 9 | register/login, duplicate username, wrong password, password hashing |
| `ApiIntegrationTest` | integration | 8 | full Ktor pipeline via `testApplication`: auth flow, 401/403, CRUD, 400/404/409 envelope, cache hit metrics, health & metrics endpoints |
| **Total** | | **49** (41 unit + 8 integration) | requirement was ≥ 10 unit + ≥ 5 integration |

Integration tests boot the real application module (`libraryModule`) with in-memory repositories and exercise it through a real `HttpClient`, so routing, serialization, auth, error mapping and caching are all covered end-to-end. JUnit XML output: `build/reports/tests/test/index.html`.

---

## 6. Docker

`Dockerfile` is a two-stage build:

1. `eclipse-temurin:17-jdk-jammy` → `./gradlew clean test jar` (tests gate the image, so a broken build can never be published).
2. `eclipse-temurin:17-jre-jammy` → installs `curl`, creates a non-root `app` user, copies only `library-service.jar`, adds a `HEALTHCHECK` on `/health/live`, exposes 8080.

```bash
docker build -t library-service:latest .
docker run -p 8080:8080 -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/library library-service:latest
docker compose up --build      # app + postgres:16 with a named volume and pg_isready gate
```

## 7. CI/CD

`.github/workflows/ci.yml` runs on every push/PR to `main`:

1. **build-and-test** — JDK 17, Gradle cache, `./gradlew clean test`, JaCoCo report, fat JAR, artifacts (`test-reports`, `library-service-jar`). A real `postgres:16` service container is attached so the pipeline also proves the production driver path.
2. **docker** — `docker build`, container start with health polling, API smoke test (login → token → authenticated `GET /api/books`), logs on failure, and on `main` pushes the image to GHCR tagged `latest` + commit SHA.

---

## 8. Verifying by hand

```bash
# health
curl http://localhost:8080/health
curl http://localhost:8080/health/live

# login and keep the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# create an author, then a book
curl -X POST http://localhost:8080/api/authors -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Robert C. Martin","nationality":"US","birthYear":1952}'

curl -X POST http://localhost:8080/api/books -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Clean Code","authorId":1,"isbn":"9780132350884","genre":"Software","totalCopies":3,"publishedYear":2008}'

# cached read twice, then inspect the counters
curl http://localhost:8080/api/books/1 -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/books/1 -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/metrics/cache

# borrow and return
curl -X POST http://localhost:8080/api/loans -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"bookId":1,"borrowerName":"Alice","days":14}'
curl -X POST http://localhost:8080/api/loans/1/return -H "Authorization: Bearer $TOKEN"

# validation failure shape
curl -i -X POST http://localhost:8080/api/books -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"","authorId":1,"isbn":"nope","genre":"x","totalCopies":0,"publishedYear":1800}'
```

---

## 9. Local verification note

This machine has no Docker CLI/daemon, so the container build and `docker compose` steps were **not** executed locally — they are covered by the GitHub Actions pipeline (image build + container smoke test). Everything else was verified locally: `gradlew clean build` succeeds and all 49 tests pass on JDK 17 (41 unit + 8 integration), the fat JAR starts, and the endpoints respond as documented.
*（内容由AI生成，仅供参考）*

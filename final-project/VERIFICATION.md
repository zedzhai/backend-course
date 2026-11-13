---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 95b4c5958d79c54d98437fc801417993_934dece9df7111f1a768525400cd780f
    ReservedCode1: pL7mspL1+oeo/naJ9hq0iJ8pPDtv53xEVGgHVhLGPA3bInnh/kYPTOmP6iFsxzgGzBwBpkJuuESqNX67yc6ynFOL5HJuP5XWTu87zdVQ9/E/HjZucAWyFTvdvFoZojUya2sR+7xtv9kx+t0AL/gp+tZoR0lzT1wOJhfvwj3MBE3CQka0HeOjihiHC0s=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 95b4c5958d79c54d98437fc801417993_934dece9df7111f1a768525400cd780f
    ReservedCode2: pL7mspL1+oeo/naJ9hq0iJ8pPDtv53xEVGgHVhLGPA3bInnh/kYPTOmP6iFsxzgGzBwBpkJuuESqNX67yc6ynFOL5HJuP5XWTu87zdVQ9/E/HjZucAWyFTvdvFoZojUya2sR+7xtv9kx+t0AL/gp+tZoR0lzT1wOJhfvwj3MBE3CQka0HeOjihiHC0s=
---

# Library Service — Build & Runtime Verification Record

Captured on 2026-11-13 on the development machine (Windows 10 Build 19045).

## 1. Environment

| Item | Value |
| --- | --- |
| JDK | Eclipse Temurin `openjdk 17.0.20.1` (root `E:\lib`, `JAVA_HOME` unset, `E:\lib\bin` on PATH) |
| Gradle | wrapper `gradle-9.6.0-bin` (no global Gradle installation) |
| Kotlin | 2.4.20, `jvmToolchain(17)` |
| Docker | **not installed** (no CLI, no Docker Desktop, no daemon) |
| PostgreSQL | 16, Windows service `postgresql-x64-16` = Running, port 5432 listening; `psql` only at `E:\POSTGRESQL\bin\psql.exe` |
| DB credentials | **not available** (`psql -U postgres -w` → `fe_sendauth: no password supplied`) |

Because neither Docker nor a working PostgreSQL password is available, the runtime verification below uses the
documented degradation path: the service starts, detects that the DB is unreachable, falls back to the in-memory
repository implementations, and reports `DEGRADED` on `/health` while still serving every business endpoint.
The PostgreSQL repositories, the schema DDL, `docker-compose.yml` and the CI pipeline stay as the production path.

## 2. Build

```
cd C:\Users\Administrator\IdeaProjects\final-project
gradlew.bat clean build --console=plain
```

```
> Task :test
> Task :build
BUILD SUCCESSFUL in 12s
```

| Metric | Value |
| --- | --- |
| Exit code | 0 |
| Test cases | **49** |
| Failures / errors / skipped | 0 / 0 / 0 |
| Fat JAR | `build/libs/library-service.jar` — 28,434,856 bytes |

### Test breakdown (Gradle XML reports)

| Test class | Type | Cases |
| --- | --- | --- |
| `com.library.service.AuthorServiceTest` | unit | 9 |
| `com.library.service.BookServiceTest` | unit | 12 |
| `com.library.service.LoanServiceTest` | unit | 11 |
| `com.library.service.AuthServiceTest` | unit | 9 |
| `com.library.ApiIntegrationTest` | integration (testApplication + real HTTP + JWT) | 8 |
| **Total** | | **49** (41 unit + 8 integration) |

Requirements were ≥10 unit and ≥5 integration tests; both are exceeded.

## 3. Runtime verification

```
set PORT=8090
E:\lib\bin\java.exe -jar build\libs\library-service.jar
```

Actual responses (curl, port 8090, DB unreachable → in-memory fallback):

| # | Request | Status | Response body |
| --- | --- | --- | --- |
| 1 | `GET /health/live` | 200 | `{"status":"UP","timestamp":1794578676587}` |
| 2 | `GET /health` | 503 | `{"status":"DEGRADED","database":"DOWN","cache":"UP","cacheSize":0,"uptimeMs":2251829,"timestamp":1794580247061}` |
| 3 | `GET /health/ready` | 503 | `NOT_READY` |
| 4 | `GET /api/books` (no token) | 401 | — |
| 5 | `POST /api/auth/login` `{"username":"admin","password":"admin123"}` | 200 | `{"token":"eyJhbGciOiJIUzI1NiIs...","username":"admin","role":"ADMIN","expiresInMs":3600000}` |
| 6 | `GET /api/auth/me` | 200 | `{"id":1,"username":"admin","role":"ADMIN"}` |
| 7 | `POST /api/authors` | 201 | `{"id":1,"name":"Robert C. Martin","nationality":"US","birthYear":1952}` |
| 8 | `POST /api/books` | 201 | `{"id":1,"title":"Clean Code","authorId":1,"isbn":"9780132350884","genre":"Software","totalCopies":3,"availableCopies":3,"publishedYear":2008}` |
| 9 | `GET /api/books/1` (first read) | 200 | — |
| 10 | `GET /api/books/1` (second read) | 200 | served from Caffeine cache |
| 11 | `GET /metrics/cache` | 200 | `{"provider":"Caffeine","hitCount":2,"missCount":0,"hitRate":1.0,"evictionCount":0,"size":1}` |
| 12 | `POST /api/loans` `{"bookId":1,"borrowerName":"Alice","days":14}` | 201 | `{"id":1,"bookId":1,"borrowerName":"Alice","borrowedAt":1794580312908,"dueAt":1795789912908,"returnedAt":null,"status":"ACTIVE"}` |
| 13 | `GET /api/loans` | 200 | list |
| 14 | `GET /api/loans/overdue` | 200 | list |
| 15 | `GET /api/loans/borrower/Alice` | 200 | list |
| 16 | `POST /api/loans/1/return` | 200 | loan closed, copy returned |
| 17 | `POST /api/books` with blank title / bad ISBN / 0 copies | 400 | `{"error":"Validation failed","errors":["title must not be blank","isbn must be a valid ISBN-10 or ISBN-13","genre must be at least 2 characters long","totalCopies must be greater than 0"],"timestamp":1794580312989}` |
| 18 | `GET /api/books/9999` | 404 | not-found envelope |
| 19 | `GET /api/books?q=clean` | 200 | search results |
| 20 | `GET /api/users` as ADMIN | 200 | user list |
| 21 | `POST /api/authors` as `reader` (USER role) | 403 | role guard |
| 22 | `GET /api/users` as `reader` | 403 | role guard |
| 23 | `POST /api/authors` with malformed JSON `{oops` | 400 | unified error envelope |
| 24 | `GET /metrics` | 200 | `library_cache_size 1.0`, `library_cache_hits 2.0`, `ktor_http_server_requests_seconds*` |

## 4. Not executable on this machine

| Requirement | Status |
| --- | --- |
| `docker build` / `docker compose up` | Files present and reviewed; **not executed** — no Docker on this host. Runs in CI or on any Docker-enabled machine. |
| GitHub Actions pipeline | Workflow present; requires a push to GitHub to trigger. |
| PostgreSQL-backed runtime | Repositories and DDL implemented; blocked by missing DB password on this host. Covered indirectly by the in-memory integration tests. |
*（内容由AI生成，仅供参考）*

# Backend Course Kotlin Labs

Kotlin backend assignments for the backend development course. The repository contains all 12 laboratory works,
built on Ktor, Exposed, PostgreSQL, JWT auth, Caffeine caching, Micrometer metrics and Docker.

## Project layout

| Path | Content |
| --- | --- |
| `old_lab3/` | Gradle subproject holding Lab 1-5 sources (console programs and the first Ktor REST service) |
| `src/main/kotlin/` | Lab 7-12 sources of the root module `lab.kt` |
| `src/test/kotlin/` | Unit, route and end-to-end tests (Lab 9, 10, 12) |
| `Dockerfile`, `docker-compose.yml` | Multi-stage image build and PostgreSQL + service stack (Lab 11) |
| `.github/workflows/ci.yml` | CI pipeline: build, test, coverage report (Lab 11) |
| `final-project/` | Standalone Gradle build with the final project: library management microservice (Ktor + Exposed/JDBC + PostgreSQL, JWT roles, Caffeine cache, Micrometer metrics) |
| `DEPLOYMENT.md` | Deployment and operations notes for the containerized service |

## Completed labs

| Lab | Topic | Key deliverables |
| --- | --- | --- |
| Lab 1 | Kotlin basics | `lab1.kt` hello world entry point |
| Lab 2 | Console calculator | arithmetic with validation, division-by-zero guard, loop mode |
| Lab 3 | In-memory task manager | `Task`, `TaskRepository`, `TaskService`, sealed `TaskResult` |
| Lab 4 | First Ktor REST service | Netty engine, routing, JSON content negotiation |
| Lab 5 | Layered service | repository/service split, request models, status-code mapping |
| Lab 6 | Spring Boot variant | separate project with controller/service/repository, global exception handler, server on port 8090 |
| Lab 7 | PostgreSQL persistence | Exposed DSL + DAO tables, HikariCP pool, `PostgresTaskRepository`, repository abstraction, custom `Instant` serializer |
| Lab 8 | Coroutines and dashboards | `DashboardService` with `coroutineScope`/`async` fan-out, external service clients, sequential vs parallel benchmark, metrics |
| Lab 9 | Testing | mockk unit tests, Ktor `testApplication` route tests, JaCoCo coverage report |
| Lab 10 | Authentication | JWT (Auth0 java-jwt), BCrypt password hashing, principal-based authorization, secured routes, auth tests |
| Lab 11 | Containerization and CI | multi-stage `Dockerfile`, `docker-compose` stack, `.dockerignore`, GitHub Actions build/test/coverage workflow, deployment guide |
| Lab 12 | Production readiness | Caffeine response cache with statistics, `/health`, `/health/live`, `/health/ready`, `/metrics`, `/metrics/cache`, unified error handling, end-to-end microservice tests |

## Tech stack

- Kotlin 2.4.20, JVM toolchain 17, Gradle 9.6.0 (wrapper)
- Ktor 2.3.12: Netty, content negotiation, status pages, call logging, auth, auth-jwt, metrics-micrometer
- Exposed 0.50.1 (core/dao/jdbc/java-time), PostgreSQL driver 42.7.2, HikariCP 5.1.0
- Caffeine 3.1.8, Micrometer Prometheus registry 1.12.2, Logback 1.4.11
- Tests: kotlin-test, Ktor test host, mockk 1.13.9, JaCoCo 0.8.12

## Build and run

```bash
./gradlew clean build            # compile, run all tests, build jar
./gradlew test jacocoTestReport  # tests plus HTML/XML coverage report
./gradlew run                    # Lab 7 application (Lab7ApplicationKt)
./gradlew runLab8App             # Lab 8 dashboard server
./gradlew runLab8Perf            # Lab 8 sequential vs parallel benchmark
./gradlew runLab10App            # Lab 10 authenticated service
./gradlew runLab12App -Pport=8090  # Lab 12 service with cache and metrics
```

The Lab 7-12 service expects PostgreSQL on `localhost:5432`. When the database is unreachable the Lab 12 build
degrades gracefully and reports `503` on `/health` with `"database":"DOWN"`.

## Docker

```bash
docker compose up --build
curl http://localhost:8090/health
curl http://localhost:8090/metrics
```

## Final project: library service

`final-project/` contains the course final project, a production-style **library management microservice**
(Ktor 2.3.12 + Exposed/JDBC + PostgreSQL 42.7.2) with JWT authentication and roles, a Caffeine response cache,
Micrometer/Prometheus metrics, health endpoints, Docker images, a Compose stack and a GitHub Actions pipeline.

The subproject is a **separate Gradle build**: it ships its own `settings.gradle.kts`, wrapper, `.gitignore`,
`Dockerfile`, `docker-compose.yml` and `.github/workflows/ci.yml`. It is not aggregated by the root
`settings.gradle.kts`, so the root `./gradlew` never builds it; always run the subproject's own wrapper from
inside the directory.

### Layout

| Path | Content |
| --- | --- |
| `final-project/src/main/kotlin/com/library/Application.kt` | Entry point: wiring, schema bootstrap, seed accounts, DB to in-memory fallback |
| `final-project/src/main/kotlin/com/library/domain/`, `repository/`, `service/` | Data model, repository interfaces with PostgreSQL and in-memory implementations, business logic and validation |
| `final-project/src/main/kotlin/com/library/routes/`, `security/`, `cache/`, `plugins/` | HTTP routes, JWT service, Caffeine cache, serialization/logging/error/metrics plugins |
| `final-project/src/main/kotlin/com/library/persistence/DatabaseFactory.kt` | Connection pool, idempotent schema DDL, `ping()` |
| `final-project/src/test/kotlin/com/library/` | 41 unit tests plus 8 `testApplication` integration tests |
| `final-project/README.md`, `final-project/VERIFICATION.md` | Subproject documentation and the build/runtime verification record |

### Build and run

```bash
cd final-project
gradlew.bat clean build          # compile, run all 49 tests, build the jar
gradlew.bat jar                  # build/libs/library-service.jar (fat jar, main class com.library.ApplicationKt)
java -jar build/libs/library-service.jar
docker compose up --build        # app on :8080, postgres:16 on :5433 (db/user/password = library)
```

Configuration is read from the environment; nothing is hardcoded:

| Variable | Default |
| --- | --- |
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/library` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | `postgres`, `postgres` |
| `JWT_SECRET`, `JWT_ISSUER` | development secret, `library-service` |

When PostgreSQL is unreachable the service still starts on in-memory repositories: `/health` answers `503` with
`"database":"DOWN"` while `/health/live` stays `200`, so degradation is visible without being fatal.
Seed accounts (created only when missing): `admin/admin123` (ADMIN), `librarian/librarian123` (LIBRARIAN),
`reader/reader123` (USER).

### Verification

```bash
curl http://localhost:8080/health                      # status, database, cache and uptime
curl http://localhost:8080/health/live                 # always 200 while the process runs
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'      # returns a JWT
curl http://localhost:8080/api/books -H "Authorization: Bearer <token>"
curl http://localhost:8080/metrics                     # Prometheus exposition
curl http://localhost:8080/metrics/cache               # cache hit/miss snapshot
```

Expected results: 49 green tests (41 unit + 8 integration), 24 business endpoints plus 5 operational ones,
schema `authors` / `books` / `loans` / `users` created on startup, and `400/401/403/404/409` returned as the
unified `{"error": ..., "timestamp": ...}` envelope. Step-by-step checks and the recorded verification run live
in `final-project/README.md` and `final-project/VERIFICATION.md`.

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

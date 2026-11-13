# ---------- Stage 1: build ----------
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Dependency layer first so source changes do not re-download the world
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts build.gradle.kts ./

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --console=plain || true

# Now the sources
COPY src src
RUN ./gradlew --no-daemon clean test jar --console=plain

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app

WORKDIR /app

COPY --from=builder /build/build/libs/library-service.jar /app/library-service.jar

ENV PORT=8080 \
    DATABASE_URL=jdbc:postgresql://db:5432/library \
    DATABASE_USER=library \
    DATABASE_PASSWORD=library \
    JWT_SECRET=library-service-dev-secret-change-me-in-production-0123456789

EXPOSE 8080

USER app

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/health/live || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/library-service.jar"]

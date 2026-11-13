# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Laboratory Work 11 - Step 2: 多阶段构建
#   Build stage：用 JDK 17 + 项目自带 Gradle Wrapper 编译胖 JAR（版本与本地/CI 一致）
#   Run stage  ：仅保留 JRE 与胖 JAR，镜像更小、攻击面更少
# 说明：课程示例用 `gradle:8.5-jdk17` 镜像内置 Gradle；本项目 wrapper 为 9.6.0
#       且 build.gradle.kts 使用 Kotlin 2.4.20，直接进 JDK 镜像跑 ./gradlew 可保证
#       构建工具版本与本地完全一致，避免 "Gradle 版本与 Kotlin 插件不兼容" 问题。
# ---------------------------------------------------------------------------

# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# 先拷贝构建脚本与 wrapper，让依赖解析层可被缓存
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# old_lab3 在 settings.gradle.kts 中被 include，必须一并拷入，否则配置阶段即失败
COPY old_lab3 ./old_lab3
COPY src ./src

# 测试在 CI 中单独跑，镜像构建阶段跳过以缩短构建时间
RUN ./gradlew build -x test --no-daemon --stacktrace

# ---------- Run stage ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl 仅用于容器 HEALTHCHECK 探活
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --create-home app

# 胖 JAR（tasks.jar 已把全部 runtime 依赖打入）
COPY --from=build /app/build/libs/lab.kt-1.0-SNAPSHOT.jar app.jar
RUN chown -R app:app /app
USER app

ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/health/live" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

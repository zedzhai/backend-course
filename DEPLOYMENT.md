# Laboratory Work 11 & 12 — 容器化、CI/CD 与完整微服务部署说明

本文对应当前仓库 `lab2.kt`（Gradle root project 名 `lab.kt`）中 Lab 11 与 Lab 12 的实现，包含：交付文件清单、本机（无 Docker）验证步骤、Docker / docker-compose 验证步骤、GitHub Actions CI/CD 说明、Railway 部署步骤与常见问题排查。

---

## 1. 交付文件清单

| 文件 | 对应步骤 | 作用 |
|---|---|---|
| `build.gradle.kts`（`tasks.jar` 段） | Lab11 Step1 | 胖 JAR 配置：`Main-Class=Lab12ApplicationKt`，把 `runtimeClasspath` 全量打入，`DuplicatesStrategy.EXCLUDE`，剔除 `META-INF/*.SF/DSA/RSA` 防签名校验失败 |
| `build.gradle.kts`（dependencies 段） | Lab12 Step1 | 新增 Caffeine 3.1.8、`ktor-server-status-pages`、`ktor-server-call-logging`、`ktor-server-metrics-micrometer`、`micrometer-registry-prometheus` |
| `Dockerfile` | Lab11 Step2 | 多阶段构建：`eclipse-temurin:17-jdk-jammy` 编译 → `eclipse-temurin:17-jre-jammy` 运行，非 root 用户 `app`，内置 `HEALTHCHECK` 打 `/health/live` |
| `docker-compose.yml` | Lab11 Step3 | `app` + `postgres:16` 两服务，db 带 `pg_isready` 健康检查，app `depends_on: condition: service_healthy`，数据卷持久化 |
| `.dockerignore` | Lab11 Step2 | 排除 `build/`、`.gradle/`、`.git/`、文档与截图，缩小构建上下文 |
| `.github/workflows/ci.yml` | Lab11 Step5 | CI/CD：JDK 17 + Gradle 缓存 + PostgreSQL 16 service 容器 + 测试 + JaCoCo + 胖 JAR + Docker 构建 + 容器冒烟测试 + 产物上传；main 分支额外推送镜像到 GHCR |
| `src/main/kotlin/lab12TaskCache.kt` | Lab12 Step2 | `TaskCache`：Caffeine 封装（`expireAfterWrite=10min`、`maximumSize=1000`、`recordStats`），提供 get/put/invalidate/invalidateAll/estimatedSize/getStats |
| `src/main/kotlin/lab12CachedTaskService.kt` | Lab12 Step3 | `CachedTaskService : TaskService`，Cache-Aside 读写策略，暴露 `getCacheStats()` |
| `src/main/kotlin/lab12Plugins.kt` | Lab12 Step4/5/6 | `configureLogging()` 请求日志、`configureErrorHandling()` 统一错误、`healthRoutes()` 健康检查、`configureMetrics()`/`registerCacheMetrics()`/`prometheusRoutes()` 监控 |
| `src/main/kotlin/lab12Application.kt` | Lab12 Step7 | 完整装配入口：DB 初始化（失败自动降级内存仓库）→ 仓库 → 缓存 → 缓存服务 → 插件与路由 |
| `src/test/kotlin/MicroserviceTest.kt` | Lab12 Step8 | 43 个用例中的 14 个：缓存、缓存服务、健康检查 UP/DOWN、缓存统计端点、删除失效、畸形 JSON 400、未知路径 404、500 兜底、Prometheus 指标 |
| `src/main/kotlin/lab7TaskService.kt` | Lab12 Step3 前置 | `TaskService` 改为 `open class`、6 个方法改为 `open fun`，供 `CachedTaskService` 继承覆写（逻辑零改动） |
| `DEPLOYMENT.md` | Lab11 交付物 | 本文档 |

Lab 12 对外端点：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | 创建任务（201），同时回填缓存 |
| GET | `/api/tasks/{id}` | 读任务，Cache-Aside：先查缓存，未命中回源并写入 |
| GET | `/api/tasks` | 列表（不走缓存，避免脏列表） |
| PUT | `/api/tasks/{id}` | 更新任务，成功后刷新缓存 |
| PATCH | `/api/tasks/{id}/toggle` | 切换完成状态，刷新缓存 |
| DELETE | `/api/tasks/{id}` | 删除任务，成功后失效缓存 |
| GET | `/health` | 综合健康检查：DB 不可用时返回 **503** + `status=DOWN` |
| GET | `/health/live` | 存活探针，永远 200（容器 HEALTHCHECK 使用） |
| GET | `/health/ready` | 就绪探针：DB 可用 200，否则 503 |
| GET | `/metrics/cache` | 缓存统计 JSON（命中数/未命中数/命中率/淘汰数/条目数） |
| GET | `/metrics` | Prometheus 文本格式指标（含 `task_cache_size`、`task_cache_hit_rate`） |

---

## 2. 本机验证（开发机无 Docker 时的替代方案）

> 本次实现的开发机 **未安装 Docker**（无 `docker` CLI、无 Docker Desktop 服务），因此 Lab11 的容器构建/运行在本机无法执行。以下步骤用「Gradle + 胖 JAR + 本机 PostgreSQL」覆盖了容器内会发生的等价验证，容器相关步骤留到装有 Docker 的机器或 GitHub Actions 中执行。

### 2.1 编译与测试（等价于 Dockerfile 的 build stage）

```powershell
cd C:\Users\Administrator\IdeaProjects\lab2.kt
.\gradlew clean test jar --console=plain
```

预期：`BUILD SUCCESSFUL`，43 个测试用例全部通过（AuthRoutesTest 7 / CachedTaskServiceTest 3 / DashboardRoutesTest 2 / DashboardServiceTest 3 / MetricsEndpointTest 1 / MicroserviceRoutesTest 7 / TaskCacheTest 3 / TaskRoutesTest 6 / TaskServiceTest 11）。

### 2.2 胖 JAR 验证（等价于 Dockerfile 交付的 `app.jar`）

```powershell
# 体积约 28.5 MB（含 Netty/Ktor/Caffeine/Exposed/PG 驱动，说明依赖确实打进去了）
Get-ChildItem build\libs\lab.kt-1.0-SNAPSHOT.jar | Select-Object Name, Length

# 用容器内同样的方式启动（容器 entrypoint 即 java -jar app.jar）
$env:PORT="8090"; E:\lib\bin\java.exe -jar build\libs\lab.kt-1.0-SNAPSHOT.jar
```

### 2.3 接口验证（等价于 Step4 的 `curl` 序列）

```powershell
$b="http://127.0.0.1:8090"
curl.exe -s "$b/health"                     # DB 正常:200 status=UP；DB 不可用:503 status=DOWN
curl.exe -s "$b/health/live"                # {"status":"UP","timestamp":...}
curl.exe -s "$b/metrics/cache"              # {"provider":"Caffeine","hitCount":...,"hitRate":...}

# 创建任务（201，并回填缓存）
curl.exe -s -X POST "$b/api/tasks" -H "Content-Type: application/json" --data-raw "{\"title\":\"docker-demo\",\"description\":\"lab12\",\"priority\":3}"

# 连读两次，第二次命中缓存
curl.exe -s "$b/api/tasks/1"
curl.exe -s "$b/api/tasks/1"
curl.exe -s "$b/metrics/cache"              # hitCount 递增，size=1

# 删除后缓存失效
curl.exe -s -X DELETE "$b/api/tasks/1"
curl.exe -s "$b/metrics/cache"              # size=0
```

本机实测结果（2026-11-13）：

| 请求 | 实测响应 |
|---|---|
| `GET /health/live` | `200 {"status":"UP","timestamp":...}` |
| `GET /health` | `503 {"status":"DOWN","database":"DOWN","cache":"UP",...}`（本机 PG 未提供可用凭据，服务已按设计降级为内存仓库） |
| `POST /api/tasks` | `201 {"id":1,"title":"docker-demo",...}` |
| `GET /api/tasks/1` ×2 | `200` ×2，`/metrics/cache` 显示 `"hitCount":2,"size":1` |
| `DELETE /api/tasks/1` 后 | `size=0`，再读返回 `404 {"error":"Resource not found"}` |
| `GET /metrics` | 输出 `task_cache_hit_rate 1.0`、`task_cache_size 1.0` |
| 畸形 JSON / 空标题 | 均返回 `400` + `{"error":...}` |
| 请求日志 | 控制台输出 `GET /api/tasks/999 -> 404` 等 CallLogging 行 |

### 2.4 让 `/health` 在本机变绿（可选）

本机已装 PostgreSQL 16（`E:\POSTGRESQL`，服务 `postgresql-x64-16` 运行中，5432 被占用属正常）。只需准备库与凭据，再以环境变量启动：

```powershell
# 1) 建库（用 pgAdmin 或知道密码的会话执行）
E:\POSTGRESQL\bin\psql.exe -U postgres -c "CREATE DATABASE taskmanager;"

# 2) 用环境变量覆盖连接串（DatabaseFactory 已支持）
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/taskmanager"
$env:DATABASE_USER="postgres"
$env:DATABASE_PASSWORD="<你的密码>"
E:\lib\bin\java.exe -jar build\libs\lab.kt-1.0-SNAPSHOT.jar
```

此时 `GET /health` 返回 `200 {"status":"UP","database":"UP",...}`，任务会真正落库（`tasks` 表由 Exposed 自动创建）。

> 说明：`lab12Application.kt` 在数据库初始化失败时会打印告警并降级为 `InMemoryTaskRepository`，保证服务仍可启动用于接口演示；生产/容器环境（compose、Railway）中数据库一定可达，因此走真实持久化分支。

---

## 3. Docker 环境验证（在装有 Docker 的机器或 CI 中执行）

```bash
# 3.1 直接构建镜像
docker build -t task-manager:lab12 .

# 3.2 单容器运行（需外部可达的数据库；此模式用 compose 更省事）
docker run -d --name task-manager -p 8080:8080 \
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/taskmanager" \
  -e DATABASE_USER=postgres -e DATABASE_PASSWORD=secret \
  task-manager:lab12
curl http://localhost:8080/health

# 3.3 一键编排（推荐）
docker compose up --build -d
docker compose ps                       # app/db 均应为 healthy
curl http://localhost:8080/health        # 200 UP，database=UP
curl http://localhost:8080/metrics/cache
docker compose logs -f app               # 观察 CallLogging 请求日志
docker compose down                      # 保留数据卷；如需清库用 docker compose down -v
```

编排要点：

- 数据库在容器内监听 5432，但对宿主机映射为 **5433**（`"5433:5432"`），避免与本机已运行的 PostgreSQL 16 冲突。
- `app` 通过服务名 `db` 访问数据库（`jdbc:postgresql://db:5432/taskmanager`），不依赖宿主机端口。
- `db` 的 healthcheck 使用 `pg_isready`，`app` 等其 healthy 后再启动，避免启动瞬间连接失败。
- `app` 自身 `HEALTHCHECK` 打 `/health/live`，`start-period=40s` 留出 JVM 与迁移时间。
- 数据卷 `postgres_data` 保证 `docker compose down` 后数据不丢。

---

## 4. GitHub Actions CI/CD

工作流文件：`.github/workflows/ci.yml`，触发条件：`push` / `pull_request` 到 `main` 或 `master`，以及手动 `workflow_dispatch`。

`test` job 步骤：

1. `actions/checkout@v4`
2. `actions/setup-java@v4`（Temurin 17）
3. `gradle/actions/setup-gradle@v4`（依赖缓存；原 `gradle/gradle-build-action@v3` 已废弃）
4. `./gradlew test`（注入 `DATABASE_URL/USER/PASSWORD` 指向 service 容器 `postgres:16`）
5. `./gradlew jacocoTestReport`（`continue-on-error: true`，仅产出覆盖率报告）
6. `./gradlew jar -x test` 生成胖 JAR
7. `docker build -t task-manager:${{ github.sha }} .`
8. 容器冒烟测试：后台启动容器，轮询 `/health/live` 直到 200，打印 `/metrics/cache` 与容器日志，随后清理
9. `actions/upload-artifact@v4` 上传 `build/reports/tests/`、`build/reports/jacoco/`（保留 7 天）与 `build/libs/*.jar`

`docker-image` job（仅 main/master push）：用 Buildx 构建并推送镜像到 GHCR，标签 `latest` 与提交 SHA，权限 `packages: write`。

**查看结果**：仓库 Actions 页 → 对应 run → 绿勾即通过；页面底部 `Artifacts` 可下载 `test-results` 与 `application-jar`，这两处截图即可作为 Lab11 Step5/Step6 的提交证据。

若需在 README 顶部展示状态徽章：

```markdown
![CI](https://github.com/<user>/<repo>/actions/workflows/ci.yml/badge.svg)
```

---

## 5. Railway 部署

1. 新建 Railway 项目 → **Deploy from GitHub repo**，选择本仓库。
2. Railway 会自动识别 `Dockerfile` 并以此构建（无需额外配置 `railway.json`）。
3. 添加数据库：`New → Database → Add PostgreSQL`，Railway 会注入 `DATABASE_URL`（形如 `postgresql://postgres:xxx@containers-us-west-x.railway.app:xxxx/railway`）。
4. 配置服务环境变量（Variables）：

| 变量 | 取值 |
|---|---|
| `DATABASE_URL` | `jdbc:` + Railway 提供的连接串（注意：本项目 `DatabaseFactory` 需要 JDBC 形式，须改成 `jdbc:postgresql://<host>:<port>/<db>`，并在末尾按需要补 `?sslmode=require`） |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Railway PG 的用户名与密码 |
| `JWT_SECRET` | 自定义强随机串（勿用默认值） |
| `PORT` | Railway 会自动注入，**不要手动覆盖**（容器已用 `${PORT}` 监听） |

5. Deploy 后进入 `Settings → Networking → Generate Domain` 拿到公网域名。
6. 验证：

```bash
curl https://<your-app>.up.railway.app/health
curl https://<your-app>.up.railway.app/metrics/cache
curl -X POST https://<your-app>.up.railway.app/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"railway-demo","priority":3}'
```

> `0.0.0.0` 绑定：`embeddedServer(Netty, port = PORT, host = "0.0.0.0")` 已满足 Railway/容器对外访问要求，若绑定 `127.0.0.1` 会 502。

---

## 6. 常见问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 胖 JAR 启动报 `Invalid signature file digest` | 依赖 jar 里带 `.SF/.DSA/.RSA` | `tasks.jar` 已 `exclude("META-INF/*.SF","META-INF/*.DSA","META-INF/*.RSA")` |
| `java -jar` 报 `no main manifest attribute` | jar 未配置 `Main-Class` | 已设 `Lab12ApplicationKt`；若改用其它入口需同步修改 |
| Docker 构建报 `Gradle 版本不兼容 Kotlin 2.4.20` | 直接用镜像自带 Gradle | Dockerfile 用项目自带 `./gradlew`（9.6.0），与本地/CI 保持一致 |
| Docker 构建报 `project ':old_lab3' not found` | `settings.gradle.kts` 中 `include("old_lab3")` 但目录未拷入 | Dockerfile 已 `COPY old_lab3 ./old_lab3`；`.dockerignore` 不要排除该目录 |
| 容器启动后 `/health` 一直 503 | 容器内连不到数据库，服务降级为内存仓库 | 检查 `DATABASE_URL` 是否用服务名 `db`（compose）或 Railway 内网地址；确认 `depends_on` 健康检查生效 |
| 本机 5432 端口冲突 | 本机 PostgreSQL 16 已占用 | compose 中数据库映射改为 `5433:5432` |
| `/metrics` 404 | 未安装 Micrometer 插件或未注册 Prometheus 路由 | 确认 `configureMetrics()` 与 `prometheusRoutes()` 在装配中被调用 |
| 缓存统计一直 `hitCount=0` | 只调列表接口，或每次新建了 `TaskCache` 实例 | 缓存是进程内单例，经 `lab12Application.kt` 持有；读单条 `GET /api/tasks/{id}` 才会命中 |
| GHCR 推送 403 | `packages: write` 未授权或 Token 权限不足 | 检查 job `permissions` 与仓库 Settings → Actions → Workflow permissions |

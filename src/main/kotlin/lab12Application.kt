import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

/**
 * Laboratory Work 12 - Step 7: 完整装配
 *
 * 组件：Caffeine 缓存 -> CachedTaskService -> taskRoutes
 *       + CallLogging 请求日志
 *       + StatusPages 统一错误处理
 *       + /health 健康检查（DB 探活）、/metrics/cache 缓存统计、/metrics Prometheus 指标
 *
 * Docker 部署时通过环境变量注入：PORT / DATABASE_URL / DATABASE_USER / DATABASE_PASSWORD / JWT_SECRET
 * 若数据库不可用，服务会降级为内存仓库继续启动（/health 返回 503），便于无 PG 环境做接口验证。
 */
fun main() {
    val dbReady = runCatching { DatabaseFactory.init() }
        .onFailure { e ->
            println("⚠️ 数据库初始化失败，降级为内存仓库：${e.message}")
        }
        .isSuccess

    val repository: TaskRepository = if (dbReady) {
        println("✅ 使用 PostgreSQL 仓库（持久化）")
        PostgresTaskRepository()
    } else {
        println("ℹ️ 使用内存仓库（仅用于本地演示，服务重启后数据丢失）")
        InMemoryTaskRepository()
    }

    val cache = TaskCache()
    val service = CachedTaskService(repository, cache)
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        configureLogging()
        configureErrorHandling()
        install(ContentNegotiation) { json() }

        val prometheus = configureMetrics()
        registerCacheMetrics(prometheus, cache)
        prometheusRoutes(prometheus)

        routing {
            healthRoutes(cache)
            taskRoutes(service)
        }
    }.start(wait = true)
}

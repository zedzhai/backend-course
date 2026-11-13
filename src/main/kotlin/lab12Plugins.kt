import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Gauge
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level

// ---------------------------------------------------------------------------
// Laboratory Work 12 - Step 5: 请求日志
// ---------------------------------------------------------------------------

/**
 * 统一请求日志：每条请求输出 `METHOD /path -> status`，级别 INFO。
 * 生产环境可把 format 换成结构化 JSON，便于采集到 ELK/Loki。
 */
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()?.value ?: 0
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "$method $path -> $status"
        }
    }
}

// ---------------------------------------------------------------------------
// Laboratory Work 12 - Step 6: 统一错误处理
// ---------------------------------------------------------------------------

@Serializable
data class ErrorResponse(
    val error: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 统一错误出口，保证任何异常/未知路径都返回同一 JSON 结构（不再泄漏堆栈给客户端）。
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Invalid argument")
            )
        }
        // 请求体无法反序列化（JSON 语法错误 / 字段类型不匹配）应返回 400 而非 500
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Invalid request payload")
            )
        }
        exception<ContentTransformationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid request payload: ${cause.message}")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error")
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Resource not found")
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Laboratory Work 12 - Step 4: 健康检查与监控
// ---------------------------------------------------------------------------

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val cache: String,
    val cacheSize: Long,
    val uptimeMs: Long,
    val timestamp: Long
)

@Serializable
data class LiveResponse(
    val status: String,
    val timestamp: Long
)

@Serializable
data class CacheStatsResponse(
    val provider: String,
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double,
    val evictionCount: Long,
    val size: Long
)

private val APP_START_MILLIS = System.currentTimeMillis()

/** 数据库可用性探测：能执行 `SELECT 1` 即视为健康。 */
fun databaseHealthy(): Boolean = runCatching {
    transaction { exec("SELECT 1") }
    true
}.getOrDefault(false)

/**
 * 健康检查与缓存监控端点：
 *  - GET /health        综合健康（DB 不可用返回 503，便于 k8s/Railway 探针判断）
 *  - GET /health/live   存活探针（进程活着即 200）
 *  - GET /health/ready  就绪探针（依赖 DB 就绪）
 *  - GET /metrics/cache 缓存统计（命中数/未命中数/命中率/容量）
 */
fun Route.healthRoutes(
    cache: TaskCache,
    dbCheck: () -> Boolean = { databaseHealthy() }
) {
    fun buildHealth(): HealthResponse {
        val dbOk = dbCheck()
        return HealthResponse(
            status = if (dbOk) "UP" else "DOWN",
            database = if (dbOk) "UP" else "DOWN",
            cache = "UP",
            cacheSize = cache.estimatedSize(),
            uptimeMs = System.currentTimeMillis() - APP_START_MILLIS,
            timestamp = System.currentTimeMillis()
        )
    }

    get("/health") {
        val body = buildHealth()
        val status = if (body.status == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(status, body)
    }

    get("/health/live") {
        call.respond(
            HttpStatusCode.OK,
            LiveResponse(status = "UP", timestamp = System.currentTimeMillis())
        )
    }

    get("/health/ready") {
        val body = buildHealth()
        val status = if (body.status == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(status, body)
    }

    get("/metrics/cache") {
        val stats = cache.getStats()
        call.respond(
            CacheStatsResponse(
                provider = "Caffeine",
                hitCount = (stats["hitCount"] as Long),
                missCount = (stats["missCount"] as Long),
                hitRate = (stats["hitRate"] as Double),
                evictionCount = (stats["evictionCount"] as Long),
                size = (stats["size"] as Long)
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Prometheus 指标（Lab12 Step 1 依赖 Micrometer + Prometheus Registry）
// ---------------------------------------------------------------------------

/**
 * 安装 Ktor Micrometer 插件（自动采集请求数/耗时/状态码），返回可被 /metrics 抓取的 registry。
 */
fun Application.configureMetrics(): PrometheusMeterRegistry {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        this.registry = registry
    }
    return registry
}

/** 把缓存命中率/容量暴露成 Prometheus Gauge（与 /metrics/cache 同源数据）。 */
fun registerCacheMetrics(registry: PrometheusMeterRegistry, cache: TaskCache) {
    Gauge.builder("task_cache_size", cache) { it.estimatedSize().toDouble() }
        .description("Caffeine 缓存当前条目数")
        .register(registry)
    Gauge.builder("task_cache_hit_rate", cache) { it.getStats()["hitRate"] as Double }
        .description("Caffeine 缓存命中率")
        .register(registry)
}

/** GET /metrics：Prometheus 抓取端点（text/plain）。 */
fun Application.prometheusRoutes(registry: PrometheusMeterRegistry) {
    routing {
        get("/metrics") {
            call.respondText(
                registry.scrape(),
                ContentType.parse("text/plain; version=0.0.4; charset=utf-8")
            )
        }
    }
}

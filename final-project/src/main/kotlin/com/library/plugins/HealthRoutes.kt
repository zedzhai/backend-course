package com.library.plugins

import com.library.cache.BookCache
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable

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

/**
 * Health, liveness, readiness and metrics endpoints.
 *
 * /health returns 503 when PostgreSQL is unreachable (Kubernetes-style semantics)
 * while /health/live stays 200 so the process is not killed during a DB blip.
 */
fun Route.healthRoutes(dbCheck: () -> Boolean, cache: BookCache, startedAt: Long) {
    get("/health") {
        val dbUp = dbCheck()
        val body = HealthResponse(
            status = if (dbUp) "UP" else "DEGRADED",
            database = if (dbUp) "UP" else "DOWN",
            cache = "UP",
            cacheSize = cache.size(),
            uptimeMs = System.currentTimeMillis() - startedAt,
            timestamp = System.currentTimeMillis()
        )
        call.respond(if (dbUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, body)
    }
    get("/health/live") {
        call.respond(HttpStatusCode.OK, LiveResponse("UP", System.currentTimeMillis()))
    }
    get("/health/ready") {
        val dbUp = dbCheck()
        call.respond(
            if (dbUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            LiveResponse(if (dbUp) "READY" else "NOT_READY", System.currentTimeMillis())
        )
    }
    get("/metrics/cache") {
        call.respond(cache.stats())
    }
}

/** Prometheus scrape endpoint. */
fun Route.metricsRoutes(registry: PrometheusMeterRegistry) {
    get("/metrics") {
        call.respondText(registry.scrape(), contentType = io.ktor.http.ContentType.parse("text/plain; version=0.0.4"))
    }
}

package com.library.plugins

import com.library.cache.BookCache
import com.library.common.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** JSON content negotiation; `ignoreUnknownKeys` keeps clients forward compatible. */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
}

/** Request logging for every call (method, path, status, duration). */
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.local.uri.startsWith("/") }
    }
}

/**
 * Single error-handling layer: every exception becomes a JSON ErrorResponse so clients
 * never receive an HTML stack trace.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("Route not found: ${call.request.local.uri}"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Malformed request body")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Invalid argument")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error")
            )
        }
    }
}

/**
 * Prometheus registry shared by the Ktor metrics plugin and the cache gauges so the
 * scrape endpoint exposes both HTTP and cache telemetry.
 */
fun prometheusRegistry(cache: BookCache): PrometheusMeterRegistry {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    Gauge.builder("library_cache_size", cache) { it.size().toDouble() }
        .description("Number of books currently held in the Caffeine cache")
        .register(registry)
    Gauge.builder("library_cache_hits", cache) { it.stats().hitCount.toDouble() }
        .description("Cumulative number of cache hits")
        .register(registry)
    return registry
}

/** Registers the Micrometer Ktor plugin against our Prometheus registry. */
fun Application.configureMetrics(registry: MeterRegistry) {
    install(io.ktor.server.metrics.micrometer.MicrometerMetrics) {
        this.registry = registry
    }
}

/** Convenience accessor used by Application.kt and the tests. */
fun Application.routingWith(block: Routing.() -> Unit) = routing(block)

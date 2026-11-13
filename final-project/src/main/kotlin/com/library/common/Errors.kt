package com.library.common

import kotlinx.serialization.Serializable

/** Single-shape error body returned by StatusPages for 4xx/5xx responses. */
@Serializable
data class ErrorResponse(
    val error: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Error body used when business validation rejects a payload. */
@Serializable
data class ValidationErrorResponse(
    val error: String = "Validation failed",
    val errors: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

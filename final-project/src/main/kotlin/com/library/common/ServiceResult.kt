package com.library.common

/**
 * Result type used by every service in the project.
 *
 * Routes translate each variant into an HTTP status, which keeps the service layer
 * completely free of Ktor dependencies and therefore easy to unit test.
 */
sealed class ServiceResult<out T> {

    data class Success<out T>(val data: T) : ServiceResult<T>()

    /** Input rejected by business validation -> HTTP 400. */
    data class ValidationFailure(val errors: List<String>) : ServiceResult<Nothing>()

    /** Entity does not exist -> HTTP 404. */
    data class NotFound(val message: String) : ServiceResult<Nothing>()

    /** Request conflicts with existing state (duplicate, wrong loan state) -> HTTP 409. */
    data class Conflict(val message: String) : ServiceResult<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    fun dataOrNull(): T? = (this as? Success)?.data
}

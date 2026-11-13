package com.library.routes

import com.library.common.ErrorResponse
import com.library.common.ServiceResult
import com.library.common.ValidationErrorResponse
import com.library.domain.UserRole
import com.library.security.JwtService
import com.library.security.Principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Maps a ServiceResult onto the matching HTTP status + body. */
suspend inline fun <reified T : Any> ApplicationCall.respondResult(
    result: ServiceResult<T>,
    successStatus: HttpStatusCode = HttpStatusCode.OK
) {
    when (result) {
        is ServiceResult.Success -> respond(successStatus, result.data)
        is ServiceResult.ValidationFailure -> respond(
            HttpStatusCode.BadRequest,
            ValidationErrorResponse(errors = result.errors)
        )
        is ServiceResult.NotFound -> respond(HttpStatusCode.NotFound, ErrorResponse(result.message))
        is ServiceResult.Conflict -> respond(HttpStatusCode.Conflict, ErrorResponse(result.message))
    }
}

/** Unit-returning variant for DELETE endpoints (204 on success). */
suspend fun ApplicationCall.respondUnitResult(result: ServiceResult<Unit>) {
    when (result) {
        is ServiceResult.Success -> respond(HttpStatusCode.NoContent)
        is ServiceResult.ValidationFailure -> respond(
            HttpStatusCode.BadRequest,
            ValidationErrorResponse(errors = result.errors)
        )
        is ServiceResult.NotFound -> respond(HttpStatusCode.NotFound, ErrorResponse(result.message))
        is ServiceResult.Conflict -> respond(HttpStatusCode.Conflict, ErrorResponse(result.message))
    }
}

/**
 * Validates the `Authorization: Bearer <jwt>` header and, when [roles] is not empty,
 * enforces role-based access. Returns null after responding 401/403 so callers can
 * simply `return@post`.
 */
suspend fun ApplicationCall.authorize(jwt: JwtService, vararg roles: UserRole): Principal? {
    val header = request.headers[HttpHeaders.Authorization]
    if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) {
        respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse("Missing or malformed Authorization header (expected 'Bearer <token>')")
        )
        return null
    }
    val token = header.substringAfter(' ').trim()
    val principal = jwt.verify(token)
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired token"))
        return null
    }
    if (roles.isNotEmpty() && !principal.hasAnyRole(*roles)) {
        respond(
            HttpStatusCode.Forbidden,
            ErrorResponse("Role '${principal.role}' is not allowed here; required one of ${roles.joinToString()}")
        )
        return null
    }
    return principal
}

package com.library.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.library.domain.UserRole
import java.util.Date
import java.util.concurrent.TimeUnit

/** Authenticated caller extracted from a validated bearer token. */
data class Principal(
    val username: String,
    val role: UserRole
) {
    fun hasAnyRole(vararg roles: UserRole): Boolean = roles.contains(role)
}

/**
 * Issues and verifies HS256 tokens.
 *
 * The secret comes from the JWT_SECRET environment variable (see docker-compose /
 * GitHub Actions); a development default keeps local runs frictionless.
 */
class JwtService(
    private val secret: String = System.getenv("JWT_SECRET") ?: DEFAULT_SECRET,
    private val issuer: String = System.getenv("JWT_ISSUER") ?: "library-service",
    private val ttlMillis: Long = TimeUnit.HOURS.toMillis(1)
) {
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    private val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    val tokenTtlMillis: Long get() = ttlMillis

    fun generateToken(username: String, role: UserRole): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(username)
            .withClaim(ROLE_CLAIM, role.name)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ttlMillis))
            .sign(algorithm)
    }

    /** Returns null when the token is missing, malformed, expired or wrongly signed. */
    fun verify(token: String): Principal? = try {
        val decoded: DecodedJWT = verifier.verify(token)
        val username = decoded.subject ?: return null
        val roleName = decoded.getClaim(ROLE_CLAIM).asString() ?: UserRole.USER.name
        val role = runCatching { UserRole.valueOf(roleName) }.getOrDefault(UserRole.USER)
        Principal(username, role)
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val ROLE_CLAIM = "role"
        const val DEFAULT_SECRET = "library-service-dev-secret-change-me-in-production-0123456789"
    }
}

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

// Laboratory Work 10 - Step 3: JWT 配置
object JwtConfig {
    val secret: String = System.getenv("JWT_SECRET") ?: "your-secret-key-change-in-production"
    val issuer = "task-manager"
    val audience = "task-manager-users"
    val realm = "Task Manager API"
    val expirationMs = 3_600_000L // 1 小时
}

fun Application.configureJwt() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(JwtConfig.secret))
                    .withIssuer(JwtConfig.issuer)
                    .withAudience(JwtConfig.audience)
                    .build()
            )
            validate { credential ->
                val username = credential.payload.getClaim("username").asString()
                if (username.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Token is invalid or expired")
                )
            }
        }
    }
}

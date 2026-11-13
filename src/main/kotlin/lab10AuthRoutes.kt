import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.Date

// Laboratory Work 10 - Step 4: 认证路由
@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val username: String, val role: String)

@Serializable
data class RegisterRequest(val username: String, val password: String)

fun Route.authRoutes() {
    route("/api/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = UserRepository.findByUsername(request.username)

            if (user == null || !UserRepository.validatePassword(user, request.password)) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid username or password")
                )
                return@post
            }

            val token = JWT.create()
                .withIssuer(JwtConfig.issuer)
                .withAudience(JwtConfig.audience)
                .withClaim("username", user.username)
                .withClaim("userId", user.id)
                .withClaim("role", user.role)
                .withExpiresAt(Date(System.currentTimeMillis() + JwtConfig.expirationMs))
                .sign(Algorithm.HMAC256(JwtConfig.secret))

            call.respond(LoginResponse(token, user.username, user.role))
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()
            val created = UserRepository.register(request.username, request.password)
            if (created == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("error" to "Username already exists or password shorter than 6 chars")
                )
                return@post
            }
            call.respond(
                HttpStatusCode.Created,
                mapOf("id" to created.id.toString(), "username" to created.username, "role" to created.role)
            )
        }
    }
}

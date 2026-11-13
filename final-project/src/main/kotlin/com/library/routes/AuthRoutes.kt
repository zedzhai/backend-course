package com.library.routes

import com.library.domain.LoginRequest
import com.library.domain.RegisterRequest
import com.library.domain.UserRole
import com.library.security.JwtService
import com.library.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Public auth endpoints plus one protected endpoint that echoes the caller.
 * POST /api/auth/register is public so the demo can create users on a fresh DB;
 * role escalation is limited to the values in UserRole.
 */
fun Route.authRoutes(authService: AuthService, jwt: JwtService) {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            call.respondResult(authService.register(request), HttpStatusCode.Created)
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            call.respondResult(authService.login(request))
        }
        get("/me") {
            val principal = call.authorize(jwt) ?: return@get
            call.respondResult(authService.currentUser(principal.username))
        }
    }

    route("/api/users") {
        get {
            call.authorize(jwt, UserRole.ADMIN) ?: return@get
            call.respond(authService.listUsers())
        }
    }
}

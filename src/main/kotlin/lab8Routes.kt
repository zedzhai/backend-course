import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Laboratory Work 8 - Step 6: Dashboard REST 端点
fun Route.dashboardRoutes(dashboardService: DashboardService) {
    get("/api/dashboard/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Invalid user ID")
            )
            return@get
        }

        val dashboard = dashboardService.getUserDashboard(userId)
        call.respond(dashboard)
    }
}

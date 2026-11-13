import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

// Laboratory Work 8 - Step 6/7: 独立启动 Dashboard REST 服务（端口 8081，不依赖数据库）
fun main() {
    val dashboardService = DashboardService()

    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        routing {
            dashboardRoutes(dashboardService)
        }
    }.start(wait = true)
}

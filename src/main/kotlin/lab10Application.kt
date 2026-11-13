import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

// Laboratory Work 10 - Step 6: 装配认证 + 受保护路由
// 使用内存仓库，便于直接用 curl 验证登录/401/403，无需本机 PostgreSQL
fun main() {
    val repository = InMemoryTaskRepository()
    val service = TaskService(repository)

    embeddedServer(Netty, port = 8082, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        configureJwt()
        routing {
            authRoutes()
            securedTaskRoutes(service)
        }
    }.start(wait = true)
}

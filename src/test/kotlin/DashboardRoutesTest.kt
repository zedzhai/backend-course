import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Laboratory Work 8 - Step 7: /api/dashboard/{userId} 端点验证（等价于 curl 手工验证）
class DashboardRoutesTest {

    private fun ApplicationTestBuilder.setupApplication() {
        application {
            install(ContentNegotiation) { json() }
            routing {
                dashboardRoutes(DashboardService())
            }
        }
    }

    @Test
    fun `GET dashboard should return aggregated data`() = testApplication {
        setupApplication()

        val response = client.get("/api/dashboard/1")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"user1@example.com\""), "响应缺少 profile: $body")
        assertTrue(body.contains("\"totalSpent\":274.49"), "响应缺少 totalSpent: $body")
        assertTrue(body.contains("Product A"), "响应缺少推荐: $body")
    }

    @Test
    fun `GET dashboard with invalid userId should return 400`() = testApplication {
        setupApplication()

        val response = client.get("/api/dashboard/abc")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Invalid user ID"))
    }
}

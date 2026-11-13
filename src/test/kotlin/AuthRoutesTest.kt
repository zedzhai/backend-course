import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Laboratory Work 10 - Step 7: 自动化验证登录 / 401 / 403
class AuthRoutesTest {

    private fun ApplicationTestBuilder.setupApplication() {
        val service = TaskService(InMemoryTaskRepository())
        application {
            install(ContentNegotiation) { json() }
            configureJwt()
            routing {
                authRoutes()
                securedTaskRoutes(service)
            }
        }
    }

    private suspend fun login(
        builder: ApplicationTestBuilder,
        username: String,
        password: String
    ): io.ktor.client.statement.HttpResponse = builder.client.post("/api/auth/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"$username","password":"$password"}""")
    }

    @Test
    fun `login with valid credentials should return token`() = testApplication {
        setupApplication()

        val response = login(this, "alice", "alice123")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<LoginResponse>(response.bodyAsText())
        assertEquals("alice", body.username)
        assertEquals("USER", body.role)
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun `login with wrong password should return 401`() = testApplication {
        setupApplication()

        val response = login(this, "alice", "wrong-password")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `access protected route without token should return 401`() = testApplication {
        setupApplication()

        val response = client.get("/api/tasks")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `access protected route with token should return 200`() = testApplication {
        setupApplication()
        val token = Json.decodeFromString<LoginResponse>(login(this, "alice", "alice123").bodyAsText()).token

        val response = client.get("/api/tasks") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `user can create task`() = testApplication {
        setupApplication()
        val token = Json.decodeFromString<LoginResponse>(login(this, "alice", "alice123").bodyAsText()).token

        val response = client.post("/api/tasks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"User Task","description":"from alice","priority":3}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("User Task"))
    }

    @Test
    fun `user cannot delete task and gets 403`() = testApplication {
        setupApplication()
        val token = Json.decodeFromString<LoginResponse>(login(this, "alice", "alice123").bodyAsText()).token

        client.post("/api/tasks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"To Delete","description":"x","priority":3}""")
        }

        val response = client.delete("/api/tasks/1") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin can delete task and gets 204`() = testApplication {
        setupApplication()
        val adminToken = Json.decodeFromString<LoginResponse>(login(this, "admin", "admin123").bodyAsText()).token

        client.post("/api/tasks") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Admin Delete","description":"x","priority":3}""")
        }

        val response = client.delete("/api/tasks/1") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}

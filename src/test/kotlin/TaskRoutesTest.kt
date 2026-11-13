import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Laboratory Work 9 - Step 3: 路由集成测试（真实 Ktor 测试引擎 + 内存仓库）
class TaskRoutesTest {

    private fun ApplicationTestBuilder.setupApplication() {
        application {
            install(ContentNegotiation) { json() }
            val repository = InMemoryTaskRepository()
            val service = TaskService(repository)
            routing {
                taskRoutes(service)
            }
        }
    }

    @Test
    fun `GET all tasks should return empty list initially`() = testApplication {
        setupApplication()

        val response = client.get("/api/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST task should create task and return 201`() = testApplication {
        setupApplication()

        val response = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test Task","description":"Test Description","priority":3}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("Test Task"))
    }

    @Test
    fun `POST task with blank title should return 400`() = testApplication {
        setupApplication()

        val response = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"","description":"Test","priority":3}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("errors"))
    }

    @Test
    fun `GET task by id should return 404 when not found`() = testApplication {
        setupApplication()

        val response = client.get("/api/tasks/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET task by invalid id should return 400`() = testApplication {
        setupApplication()

        val response = client.get("/api/tasks/abc")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `full CRUD lifecycle should work`() = testApplication {
        setupApplication()

        val createResponse = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Lifecycle Task","description":"Test","priority":3}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val getResponse = client.get("/api/tasks/1")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertTrue(getResponse.bodyAsText().contains("Lifecycle Task"))

        val updateResponse = client.put("/api/tasks/1") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Updated Task","description":"Updated","priority":5}""")
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        assertTrue(updateResponse.bodyAsText().contains("Updated Task"))

        val deleteResponse = client.delete("/api/tasks/1")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val verifyResponse = client.get("/api/tasks/1")
        assertEquals(HttpStatusCode.NotFound, verifyResponse.status)
    }
}

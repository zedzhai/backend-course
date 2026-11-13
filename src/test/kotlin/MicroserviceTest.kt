import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Laboratory Work 12 测试：
 * 覆盖缓存读写/失效/统计、缓存服务命中与回源、健康检查 UP/DOWN、统一错误处理、Prometheus 指标暴露。
 * 全部使用内存仓库，不依赖本机 PostgreSQL 与 Docker，可在 CI 中直接跑。
 */
class TaskCacheTest {

    @Test
    fun `put then get returns cached task`() {
        val cache = TaskCache()
        val task = Task(id = 1, title = "cached", priority = 2)

        assertNull(cache.get(1), "初始为空")
        cache.put(1, task)
        assertEquals(task, cache.get(1))
        assertEquals(1L, cache.estimatedSize())
    }

    @Test
    fun `invalidate and invalidateAll clear entries`() {
        val cache = TaskCache()
        cache.put(1, Task(id = 1, title = "a"))
        cache.put(2, Task(id = 2, title = "b"))

        cache.invalidate(1)
        assertNull(cache.get(1))
        assertNotNull(cache.get(2))

        cache.invalidateAll()
        assertEquals(0L, cache.estimatedSize())
    }

    @Test
    fun `stats record hits and misses`() {
        val cache = TaskCache()
        cache.put(1, Task(id = 1, title = "hit"))

        cache.get(1)   // hit
        cache.get(99)  // miss

        val stats = cache.getStats()
        assertEquals(1L, stats["hitCount"])
        assertEquals(1L, stats["missCount"])
        assertTrue((stats["hitRate"] as Double) > 0.0)
    }
}

class CachedTaskServiceTest {

    private fun newService(): Pair<CachedTaskService, TaskCache> {
        val cache = TaskCache()
        return CachedTaskService(InMemoryTaskRepository(), cache) to cache
    }

    @Test
    fun `getTask caches the value and second call hits cache`() {
        val (service, cache) = newService()
        val created = service.createTask(CreateTaskRequest(title = "task", description = "d", priority = 2))
        val id = (created as ServiceResult.Success<Task>).data.id

        // createTask 已回填缓存，这里先清空以验证「未命中回源 -> 命中缓存」的完整路径
        cache.invalidateAll()

        val first = service.getTask(id)
        assertTrue(first is ServiceResult.Success)
        val second = service.getTask(id)

        assertEquals(first, second)
        assertEquals(1L, cache.getStats()["missCount"], "第一次读取应未命中")
        assertEquals(1L, cache.getStats()["hitCount"], "第二次读取应命中缓存")
    }

    @Test
    fun `update refreshes cache and delete invalidates it`() {
        val (service, cache) = newService()
        val created = service.createTask(CreateTaskRequest(title = "old"))
        val id = (created as ServiceResult.Success<Task>).data.id

        service.getTask(id) // 预热缓存
        service.updateTask(id, CreateTaskRequest(title = "new"))
        val afterUpdate = service.getTask(id) as ServiceResult.Success<Task>
        assertEquals("new", afterUpdate.data.title, "缓存应回填更新后的值")
        assertEquals("new", cache.get(id)?.title)

        service.deleteTask(id)
        assertNull(cache.get(id), "删除后缓存必须失效")
        assertTrue(service.getTask(id) is ServiceResult.NotFound)
    }

    @Test
    fun `create keeps validation behaviour`() {
        val (service, _) = newService()
        val result = service.createTask(CreateTaskRequest(title = "   "))
        assertTrue(result is ServiceResult.ValidationFailure)
    }
}

class MicroserviceRoutesTest {

    private fun ApplicationTestBuilder.installApp(
        cache: TaskCache,
        service: CachedTaskService,
        dbCheck: () -> Boolean
    ) {
        application {
            configureErrorHandling()
            install(ContentNegotiation) { json() }
            routing {
                healthRoutes(cache, dbCheck)
                taskRoutes(service)
            }
        }
    }

    @Test
    fun `health reports UP when database is reachable`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        installApp(cache, service) { true }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\":\"UP\""), "实际响应：$body")
        assertTrue(body.contains("\"database\":\"UP\""))
    }

    @Test
    fun `health reports 503 when database is down`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        installApp(cache, service) { false }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"DOWN\""))
    }

    @Test
    fun `cache stats endpoint reflects traffic`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        installApp(cache, service) { true }

        val created = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"metrics","description":"x","priority":4}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        client.get("/api/tasks/1")
        client.get("/api/tasks/1")

        val stats = client.get("/metrics/cache")
        assertEquals(HttpStatusCode.OK, stats.status)
        val body = stats.bodyAsText()
        assertTrue(body.contains("\"provider\":\"Caffeine\""), "实际响应：$body")
        assertTrue(body.contains("\"hitCount\":2"), "创建时已回填缓存，两次 GET 应全部命中：$body")
    }

    @Test
    fun `delete through routes invalidates cache`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        installApp(cache, service) { true }

        client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"to-delete"}""")
        }
        client.get("/api/tasks/1")
        assertEquals(1L, cache.estimatedSize())

        val deleted = client.delete("/api/tasks/1")
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(0L, cache.estimatedSize(), "路由删除后缓存应失效")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/tasks/1").status)
    }

    @Test
    fun `malformed json body returns 400`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        installApp(cache, service) { true }

        val response = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"error\""))
    }

    @Test
    fun `unknown path returns unified error json`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        installApp(cache, service) { true }

        val response = client.get("/api/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("Resource not found"))
    }

    @Test
    fun `unexpected exception is mapped to 500 with error body`() = testApplication {
        val cache = TaskCache()
        val service = CachedTaskService(InMemoryTaskRepository(), cache)
        application {
            configureErrorHandling()
            install(ContentNegotiation) { json() }
            routing {
                get("/boom") { throw IllegalStateException("boom") }
            }
        }

        val response = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.bodyAsText().contains("Internal server error"))
    }
}

class MetricsEndpointTest {

    @Test
    fun `prometheus endpoint exposes cache gauges`() = testApplication {
        val cache = TaskCache()
        application {
            val registry = configureMetrics()
            registerCacheMetrics(registry, cache)
            prometheusRoutes(registry)
        }

        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("task_cache_size"), "实际响应片段：${body.take(200)}")
        assertTrue(body.contains("task_cache_hit_rate"))
    }
}


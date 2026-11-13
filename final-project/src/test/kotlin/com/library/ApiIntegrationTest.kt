package com.library

import com.library.common.ErrorResponse
import com.library.domain.Author
import com.library.domain.Book
import com.library.domain.Loan
import com.library.domain.LoanStatus
import com.library.domain.LoginResponse
import com.library.plugins.HealthResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests through the real Ktor pipeline (plugins + routing) against the
 * in-memory repositories, so no external services are required.
 */

/** Explicit `HttpResponse` return type avoids the reified generic overload ambiguity. */
private suspend fun HttpClient.httpGet(path: String, token: String? = null): HttpResponse =
    get(path) {
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
    }

private suspend fun HttpClient.httpPost(
    path: String,
    body: String? = null,
    token: String? = null
): HttpResponse = post(path) {
    if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
    if (body != null) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}

private suspend fun HttpClient.httpDelete(path: String, token: String? = null): HttpResponse =
    delete(path) {
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
    }

class ApiIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun login(client: HttpClient, username: String, password: String): String {
        val response = client.httpPost(
            "/api/auth/login",
            """{"username":"$username","password":"$password"}"""
        )
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(LoginResponse.serializer(), response.bodyAsText()).token
    }

    @Test
    fun `health endpoints report status and cache metrics`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }

        val health = client.httpGet("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("UP", json.decodeFromString(HealthResponse.serializer(), health.bodyAsText()).status)

        assertEquals(HttpStatusCode.OK, client.httpGet("/health/live").status)
        assertEquals(HttpStatusCode.OK, client.httpGet("/health/ready").status)

        val cacheMetrics = client.httpGet("/metrics/cache")
        assertEquals(HttpStatusCode.OK, cacheMetrics.status)
        assertTrue(cacheMetrics.bodyAsText().contains("hitCount"))

        val prometheus = client.httpGet("/metrics")
        assertEquals(HttpStatusCode.OK, prometheus.status)
        assertTrue(prometheus.bodyAsText().contains("library_cache_size"))
    }

    @Test
    fun `auth flow registers logs in and returns the current user`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }

        val register = client.httpPost(
            "/api/auth/register",
            """{"username":"newreader","password":"secret123","role":"USER"}"""
        )
        assertEquals(HttpStatusCode.Created, register.status)

        val token = login(client, "newreader", "secret123")

        val me = client.httpGet("/api/auth/me", token)
        assertEquals(HttpStatusCode.OK, me.status)
        assertTrue(me.bodyAsText().contains("newreader"))
    }

    @Test
    fun `author endpoints support create list and read`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }
        val token = login(client, "librarian", "librarian123")

        val created = client.httpPost(
            "/api/authors",
            """{"name":"Isabel Allende","nationality":"Chilean","birthYear":1942}""",
            token
        )
        assertEquals(HttpStatusCode.Created, created.status)
        val author = json.decodeFromString(Author.serializer(), created.bodyAsText())

        val list = client.httpGet("/api/authors", token)
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("Isabel Allende"))

        val byId = client.httpGet("/api/authors/${author.id}", token)
        assertEquals(HttpStatusCode.OK, byId.status)
    }

    @Test
    fun `book read is served from cache on the second request`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }
        val token = login(client, "librarian", "librarian123")

        val authorId = world.newAuthor("Robert Martin")
        val created = client.httpPost(
            "/api/books",
            """{"title":"Clean Code","authorId":$authorId,"isbn":"9780132350884","genre":"Software","totalCopies":4}""",
            token
        )
        assertEquals(HttpStatusCode.Created, created.status)
        val book = json.decodeFromString(Book.serializer(), created.bodyAsText())

        world.cache.invalidateAll()
        repeat(2) {
            val response = client.httpGet("/api/books/${book.id}", token)
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val stats = client.httpGet("/metrics/cache").bodyAsText()
        assertTrue(stats.contains("\"hitCount\":1"), "expected 1 cache hit, got: $stats")
        assertTrue(stats.contains("\"missCount\":1"), "expected 1 cache miss, got: $stats")

        val search = client.httpGet("/api/books?q=clean", token)
        assertEquals(HttpStatusCode.OK, search.status)
        assertTrue(search.bodyAsText().contains("Clean Code"))
    }

    @Test
    fun `loan endpoints borrow and return a book`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }
        val librarian = login(client, "librarian", "librarian123")
        val reader = login(client, "reader", "reader123")

        val authorId = world.newAuthor("Erich Gamma")
        val bookId = world.newBook(
            title = "Design Patterns",
            isbn = "9780201633610",
            copies = 1,
            authorId = authorId
        )

        val borrowed = client.httpPost(
            "/api/loans",
            """{"bookId":$bookId,"borrowerName":"Reader One","days":14}""",
            reader
        )
        assertEquals(HttpStatusCode.Created, borrowed.status)
        val loan = json.decodeFromString(Loan.serializer(), borrowed.bodyAsText())

        val loans = client.httpGet("/api/loans", librarian)
        assertEquals(HttpStatusCode.OK, loans.status)

        val returned = client.httpPost("/api/loans/${loan.id}/return", null, librarian)
        assertEquals(HttpStatusCode.OK, returned.status)
        assertEquals(LoanStatus.RETURNED, json.decodeFromString(Loan.serializer(), returned.bodyAsText()).status)
    }

    @Test
    fun `protected endpoints return 401 without a token and 403 for a wrong role`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }

        assertEquals(HttpStatusCode.Unauthorized, client.httpGet("/api/books").status)

        val badToken = client.httpGet("/api/books", "not-a-real-token")
        assertEquals(HttpStatusCode.Unauthorized, badToken.status)
        val body = json.decodeFromString(ErrorResponse.serializer(), badToken.bodyAsText())
        assertTrue(body.error.contains("Invalid"))

        val reader = login(client, "reader", "reader123")
        val forbidden = client.httpPost("/api/authors", """{"name":"Not Allowed"}""", reader)
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    }

    @Test
    fun `malformed json and unknown routes are handled by the error layer`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }
        val token = login(client, "admin", "admin123")

        val badJson = client.httpPost("/api/authors", "{ this is not json }", token)
        assertEquals(HttpStatusCode.BadRequest, badJson.status)

        val unknown = client.httpGet("/api/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertTrue(unknown.bodyAsText().contains("Route not found"))
    }

    @Test
    fun `delete endpoints remove resources and report 204`() = testApplication {
        val world = TestWorld()
        application { libraryModule(world.dependencies()) }
        val admin = login(client, "admin", "admin123")

        val created = client.httpPost(
            "/api/authors",
            """{"name":"Temporary Author","nationality":"None","birthYear":1970}""",
            admin
        )
        assertEquals(HttpStatusCode.Created, created.status)
        val id = json.decodeFromString(Author.serializer(), created.bodyAsText()).id

        val deleted = client.httpDelete("/api/authors/$id", admin)
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val gone = client.httpGet("/api/authors/$id", admin)
        assertEquals(HttpStatusCode.NotFound, gone.status)
    }
}

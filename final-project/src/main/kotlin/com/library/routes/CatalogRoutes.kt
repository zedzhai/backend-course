package com.library.routes

import com.library.common.ErrorResponse
import com.library.domain.CreateAuthorRequest
import com.library.domain.CreateBookRequest
import com.library.domain.UpdateBookRequest
import com.library.domain.UserRole
import com.library.security.JwtService
import com.library.service.AuthorService
import com.library.service.BookService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * Author CRUD (part of the required 5+ endpoints).
 * Reads are open to any authenticated user; writes need LIBRARIAN or ADMIN.
 */
fun Route.authorRoutes(authorService: AuthorService, jwt: JwtService) {
    route("/api/authors") {
        get {
            call.authorize(jwt) ?: return@get
            call.respond(authorService.list())
        }
        get("/{id}") {
            call.authorize(jwt) ?: return@get
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            call.respondResult(authorService.get(id))
        }
        post {
            call.authorize(jwt, UserRole.ADMIN, UserRole.LIBRARIAN) ?: return@post
            val request = call.receive<CreateAuthorRequest>()
            call.respondResult(authorService.create(request), HttpStatusCode.Created)
        }
        put("/{id}") {
            call.authorize(jwt, UserRole.ADMIN, UserRole.LIBRARIAN) ?: return@put
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            val request = call.receive<CreateAuthorRequest>()
            call.respondResult(authorService.update(id, request))
        }
        delete("/{id}") {
            call.authorize(jwt, UserRole.ADMIN) ?: return@delete
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            call.respondUnitResult(authorService.delete(id))
        }
    }
}

/**
 * Book CRUD, search and the cached single-book read.
 * GET /api/books/{id} is the caching requirement: served from Caffeine on repeat hits.
 */
fun Route.bookRoutes(bookService: BookService, jwt: JwtService) {
    route("/api/books") {
        get {
            call.authorize(jwt) ?: return@get
            val keyword = call.request.queryParameters["q"]
            val books = if (keyword.isNullOrBlank()) bookService.list() else bookService.search(keyword)
            call.respond(books)
        }
        get("/{id}") {
            call.authorize(jwt) ?: return@get
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            call.respondResult(bookService.get(id))
        }
        get("/author/{authorId}") {
            call.authorize(jwt) ?: return@get
            val authorId = call.parameters["authorId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("authorId must be an integer"))
            call.respond(bookService.byAuthor(authorId))
        }
        post {
            call.authorize(jwt, UserRole.ADMIN, UserRole.LIBRARIAN) ?: return@post
            val request = call.receive<CreateBookRequest>()
            call.respondResult(bookService.create(request), HttpStatusCode.Created)
        }
        put("/{id}") {
            call.authorize(jwt, UserRole.ADMIN, UserRole.LIBRARIAN) ?: return@put
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            val request = call.receive<UpdateBookRequest>()
            call.respondResult(bookService.update(id, request))
        }
        delete("/{id}") {
            call.authorize(jwt, UserRole.ADMIN) ?: return@delete
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            call.respondUnitResult(bookService.delete(id))
        }
    }
}

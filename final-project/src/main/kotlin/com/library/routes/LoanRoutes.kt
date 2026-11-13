package com.library.routes

import com.library.common.ErrorResponse
import com.library.domain.CreateLoanRequest
import com.library.domain.UserRole
import com.library.security.JwtService
import com.library.service.LoanService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Loan endpoints: borrow, return, list and overdue report.
 * Borrowing is allowed for every authenticated role; returns require staff.
 */
fun Route.loanRoutes(loanService: LoanService, jwt: JwtService) {
    route("/api/loans") {
        get {
            call.authorize(jwt) ?: return@get
            call.respond(loanService.list())
        }
        get("/overdue") {
            call.authorize(jwt, UserRole.ADMIN, UserRole.LIBRARIAN) ?: return@get
            call.respond(loanService.overdue())
        }
        get("/borrower/{name}") {
            call.authorize(jwt) ?: return@get
            val name = call.parameters["name"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("name is required"))
            call.respond(loanService.activeFor(name))
        }
        get("/{id}") {
            call.authorize(jwt) ?: return@get
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            call.respondResult(loanService.get(id))
        }
        post {
            call.authorize(jwt) ?: return@post
            val request = call.receive<CreateLoanRequest>()
            call.respondResult(loanService.borrow(request), HttpStatusCode.Created)
        }
        post("/{id}/return") {
            call.authorize(jwt, UserRole.ADMIN, UserRole.LIBRARIAN) ?: return@post
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be an integer"))
            call.respondResult(loanService.returnBook(id))
        }
    }
}

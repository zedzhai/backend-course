package com.library.service

import com.library.TestWorld
import com.library.common.ServiceResult
import com.library.domain.CreateLoanRequest
import com.library.domain.Loan
import com.library.domain.LoanStatus
import com.library.domain.LoginRequest
import com.library.domain.LoginResponse
import com.library.domain.RegisterRequest
import com.library.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoanServiceTest {

    private val world = TestWorld(maxActiveLoans = 2)
    private val authorId = world.newAuthor("Kent Beck")

    @Test
    fun `borrow decrements available copies and creates an active loan`() {
        val bookId = world.newBook(isbn = "9780321146533", copies = 2, authorId = authorId)
        val result = world.loanService.borrow(CreateLoanRequest(bookId, "Carol", 14))

        val loan = assertIs<ServiceResult.Success<Loan>>(result).data
        assertEquals(LoanStatus.ACTIVE, loan.status)
        assertEquals(1, world.bookRepository.findById(bookId)!!.availableCopies)
        assertTrue(loan.dueAt > loan.borrowedAt)
    }

    @Test
    fun `borrow with a blank borrower name is rejected`() {
        val bookId = world.newBook(isbn = "9780321146533", authorId = authorId)
        val result = world.loanService.borrow(CreateLoanRequest(bookId, "  ", 14))
        assertIs<ServiceResult.ValidationFailure>(result)
    }

    @Test
    fun `borrow with an out of range duration is rejected`() {
        val bookId = world.newBook(isbn = "9780321146533", authorId = authorId)
        assertIs<ServiceResult.ValidationFailure>(world.loanService.borrow(CreateLoanRequest(bookId, "Dan", 0)))
        assertIs<ServiceResult.ValidationFailure>(world.loanService.borrow(CreateLoanRequest(bookId, "Dan", 365)))
    }

    @Test
    fun `borrow unknown book returns not found`() {
        assertIs<ServiceResult.NotFound>(world.loanService.borrow(CreateLoanRequest(999, "Eve", 7)))
    }

    @Test
    fun `borrow fails when no copy is available`() {
        val bookId = world.newBook(isbn = "9780321146533", copies = 1, authorId = authorId)
        world.loanService.borrow(CreateLoanRequest(bookId, "Frank", 7))
        assertIs<ServiceResult.Conflict>(world.loanService.borrow(CreateLoanRequest(bookId, "Grace", 7)))
    }

    @Test
    fun `borrower cannot exceed the active loan limit`() {
        val first = world.newBook(isbn = "9780321146533", copies = 1, authorId = authorId)
        val second = world.newBook(isbn = "9780321356680", copies = 1, authorId = authorId)
        val third = world.newBook(isbn = "9780596009205", copies = 1, authorId = authorId)

        assertIs<ServiceResult.Success<Loan>>(world.loanService.borrow(CreateLoanRequest(first, "Heidi", 7)))
        assertIs<ServiceResult.Success<Loan>>(world.loanService.borrow(CreateLoanRequest(second, "Heidi", 7)))
        assertIs<ServiceResult.Conflict>(world.loanService.borrow(CreateLoanRequest(third, "Heidi", 7)))
    }

    @Test
    fun `returning a loan restores availability and marks it returned`() {
        val bookId = world.newBook(isbn = "9780321146533", copies = 1, authorId = authorId)
        val loan = assertIs<ServiceResult.Success<Loan>>(
            world.loanService.borrow(CreateLoanRequest(bookId, "Ivan", 7))
        ).data

        val returned = assertIs<ServiceResult.Success<Loan>>(world.loanService.returnBook(loan.id)).data
        assertEquals(LoanStatus.RETURNED, returned.status)
        assertNotNull(returned.returnedAt)
        assertEquals(1, world.bookRepository.findById(bookId)!!.availableCopies)
    }

    @Test
    fun `returning the same loan twice returns conflict`() {
        val bookId = world.newBook(isbn = "9780321146533", copies = 1, authorId = authorId)
        val loan = assertIs<ServiceResult.Success<Loan>>(
            world.loanService.borrow(CreateLoanRequest(bookId, "Judy", 7))
        ).data
        world.loanService.returnBook(loan.id)
        assertIs<ServiceResult.Conflict>(world.loanService.returnBook(loan.id))
    }

    @Test
    fun `returning an unknown loan returns not found`() {
        assertIs<ServiceResult.NotFound>(world.loanService.returnBook(555))
    }

    @Test
    fun `overdue report only lists active loans past their due date`() {
        val bookId = world.newBook(isbn = "9780321146533", copies = 2, authorId = authorId)
        val past = world.loanService.borrow(CreateLoanRequest(bookId, "Ken", 1))
        world.loanService.borrow(CreateLoanRequest(bookId, "Later", 30))
        assertIs<ServiceResult.Success<Loan>>(past)
        // Nothing is overdue yet because due dates are in the future.
        assertEquals(0, world.loanService.overdue().size)
    }

    @Test
    fun `active loans can be listed per borrower`() {
        val bookId = world.newBook(isbn = "9780321146533", copies = 2, authorId = authorId)
        world.loanService.borrow(CreateLoanRequest(bookId, "Mona", 7))
        assertEquals(1, world.loanService.activeFor("mona").size)
        assertEquals(0, world.loanService.activeFor("nobody").size)
    }
}

class AuthServiceTest {

    private val world = TestWorld()

    @Test
    fun `register creates a user with hashed password`() {
        val result = world.authService.register(RegisterRequest("newuser", "secret123", "LIBRARIAN"))
        val user = assertIs<ServiceResult.Success<*>>(result).data.toString()
        assertTrue(user.contains("newuser"))
        assertTrue(world.userRepository.findByUsername("newuser")!!.passwordHash.startsWith("$2"))
    }

    @Test
    fun `register rejects an invalid username`() {
        val result = world.authService.register(RegisterRequest("no spaces allowed", "secret123"))
        assertIs<ServiceResult.ValidationFailure>(result)
    }

    @Test
    fun `register rejects a short password`() {
        val result = world.authService.register(RegisterRequest("shorty", "123"))
        val failure = assertIs<ServiceResult.ValidationFailure>(result)
        assertTrue(failure.errors.any { it.contains("password") })
    }

    @Test
    fun `register rejects an unknown role`() {
        val result = world.authService.register(RegisterRequest("roleuser", "secret123", "SUPERHERO"))
        assertIs<ServiceResult.ValidationFailure>(result)
    }

    @Test
    fun `register rejects a duplicate username`() {
        assertIs<ServiceResult.Conflict>(world.authService.register(RegisterRequest("admin", "secret123")))
    }

    @Test
    fun `login with correct credentials returns a verifiable token`() {
        val result = world.authService.login(LoginRequest("admin", "admin123"))
        val response = assertIs<ServiceResult.Success<LoginResponse>>(result).data
        assertEquals("ADMIN", response.role)

        val principal = world.jwt.verify(response.token)
        assertNotNull(principal)
        assertEquals("admin", principal.username)
        assertEquals(UserRole.ADMIN, principal.role)
    }

    @Test
    fun `login with wrong password is rejected`() {
        assertIs<ServiceResult.Conflict>(world.authService.login(LoginRequest("admin", "wrongpass")))
    }

    @Test
    fun `login with unknown user is rejected`() {
        assertIs<ServiceResult.Conflict>(world.authService.login(LoginRequest("ghost", "whatever")))
    }

    @Test
    fun `tampered token does not verify`() {
        val token = world.tokenFor("reader")
        assertEquals(null, world.jwt.verify(token + "tampered"))
    }
}

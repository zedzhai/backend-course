package com.library.service

import com.library.common.ServiceResult
import com.library.domain.CreateLoanRequest
import com.library.domain.Loan
import com.library.domain.LoanStatus
import com.library.repository.BookRepository
import com.library.repository.LoanRepository
import java.util.concurrent.TimeUnit

/**
 * Loan (borrow / return) business logic.
 *
 * Invariants enforced here: the book must exist, a copy must be available, one
 * borrower cannot hold more than [maxActiveLoans] loans, and a loan can only be
 * returned once.
 */
class LoanService(
    private val loans: LoanRepository,
    private val books: BookRepository,
    private val bookService: BookService,
    private val maxActiveLoans: Long = 5
) {
    fun list(): List<Loan> = loans.findAll()

    fun get(id: Int): ServiceResult<Loan> {
        val loan = loans.findById(id)
            ?: return ServiceResult.NotFound("Loan with id=$id not found")
        return ServiceResult.Success(loan)
    }

    fun activeFor(borrowerName: String): List<Loan> = loans.findActiveByBorrower(borrowerName)

    fun borrow(request: CreateLoanRequest): ServiceResult<Loan> {
        val validator = Validator()
        validator.requirePositive(request.bookId, "bookId")
        validator.requireNotBlank(request.borrowerName, "borrowerName", minLength = 2)
        validator.requireMaxLength(request.borrowerName, "borrowerName", 120)
        validator.requireRange(request.days, "days", 1, 90)
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }

        val book = books.findById(request.bookId)
            ?: return ServiceResult.NotFound("Book with id=${request.bookId} not found")
        if (book.availableCopies <= 0) {
            return ServiceResult.Conflict("No copy of book id=${book.id} is currently available")
        }

        val borrower = request.borrowerName.trim()
        val active = loans.countActiveByBorrower(borrower)
        if (active >= maxActiveLoans) {
            return ServiceResult.Conflict(
                "Borrower '$borrower' already holds $active active loans (limit $maxActiveLoans)"
            )
        }

        val now = System.currentTimeMillis()
        val due = now + TimeUnit.DAYS.toMillis(request.days.toLong())
        val loan = loans.create(book.id, borrower, now, due)
        books.changeAvailableCopies(book.id, -1)
        bookService.refreshCache(book.id)
        return ServiceResult.Success(loan)
    }

    fun returnBook(id: Int): ServiceResult<Loan> {
        val loan = loans.findById(id)
            ?: return ServiceResult.NotFound("Loan with id=$id not found")
        if (loan.status != LoanStatus.ACTIVE) {
            return ServiceResult.Conflict("Loan id=$id is already ${loan.status.name.lowercase()}")
        }
        val returned = loans.markReturned(id, System.currentTimeMillis())
            ?: return ServiceResult.NotFound("Loan with id=$id not found")
        books.findById(loan.bookId)?.let { book ->
            books.changeAvailableCopies(book.id, +1)
            bookService.refreshCache(book.id)
        }
        return ServiceResult.Success(returned)
    }

    /** Loans whose due date has passed but which were never returned. */
    fun overdue(): List<Loan> {
        val now = System.currentTimeMillis()
        return loans.findAll().filter { it.status == LoanStatus.ACTIVE && it.dueAt < now }
    }
}

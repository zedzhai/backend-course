package com.library.repository

import com.library.domain.Author
import com.library.domain.Book
import com.library.domain.CreateAuthorRequest
import com.library.domain.CreateBookRequest
import com.library.domain.Loan
import com.library.domain.UpdateBookRequest
import com.library.domain.User
import com.library.domain.UserRole

/**
 * Repository pattern: services depend only on these interfaces, so the PostgreSQL
 * implementation can be swapped for the in-memory one in tests without touching
 * business logic.
 */
interface AuthorRepository {
    fun findAll(): List<Author>
    fun findById(id: Int): Author?
    fun findByName(name: String): Author?
    fun create(request: CreateAuthorRequest): Author
    fun update(id: Int, request: CreateAuthorRequest): Author?
    fun delete(id: Int): Boolean
}

interface BookRepository {
    fun findAll(): List<Book>
    fun findById(id: Int): Book?
    fun findByIsbn(isbn: String): Book?
    fun findByAuthor(authorId: Int): List<Book>
    fun search(keyword: String): List<Book>
    fun create(request: CreateBookRequest): Book
    fun update(id: Int, request: UpdateBookRequest): Book?
    fun delete(id: Int): Boolean
    fun changeAvailableCopies(id: Int, delta: Int): Book?
    fun countByAuthor(authorId: Int): Long
}

interface LoanRepository {
    fun findAll(): List<Loan>
    fun findById(id: Int): Loan?
    fun findActiveByBorrower(borrowerName: String): List<Loan>
    fun countActiveByBorrower(borrowerName: String): Long
    fun create(bookId: Int, borrowerName: String, borrowedAt: Long, dueAt: Long): Loan
    fun markReturned(id: Int, returnedAt: Long): Loan?
    fun countActive(): Long
}

interface UserRepository {
    fun findByUsername(username: String): User?
    fun create(username: String, passwordHash: String, role: UserRole): User
    fun findAll(): List<User>
}

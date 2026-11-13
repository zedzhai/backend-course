package com.library.repository.memory

import com.library.domain.Author
import com.library.domain.Book
import com.library.domain.CreateAuthorRequest
import com.library.domain.CreateBookRequest
import com.library.domain.Loan
import com.library.domain.LoanStatus
import com.library.domain.UpdateBookRequest
import com.library.domain.User
import com.library.domain.UserRole
import com.library.repository.AuthorRepository
import com.library.repository.BookRepository
import com.library.repository.LoanRepository
import com.library.repository.UserRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe in-memory repositories.
 *
 * Used by the integration tests and as a graceful fallback when PostgreSQL is not
 * reachable, so the service can still start and be demonstrated locally.
 */
class InMemoryAuthorRepository : AuthorRepository {
    private val store = ConcurrentHashMap<Int, Author>()
    private val sequence = AtomicInteger(0)

    override fun findAll(): List<Author> = store.values.sortedBy { it.id }

    override fun findById(id: Int): Author? = store[id]

    override fun findByName(name: String): Author? =
        store.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun create(request: CreateAuthorRequest): Author {
        val author = Author(
            id = sequence.incrementAndGet(),
            name = request.name,
            nationality = request.nationality,
            birthYear = request.birthYear
        )
        store[author.id] = author
        return author
    }

    override fun update(id: Int, request: CreateAuthorRequest): Author? {
        val existing = store[id] ?: return null
        val updated = existing.copy(
            name = request.name,
            nationality = request.nationality,
            birthYear = request.birthYear
        )
        store[id] = updated
        return updated
    }

    override fun delete(id: Int): Boolean = store.remove(id) != null
}

class InMemoryBookRepository : BookRepository {
    private val store = ConcurrentHashMap<Int, Book>()
    private val sequence = AtomicInteger(0)

    override fun findAll(): List<Book> = store.values.sortedBy { it.id }

    override fun findById(id: Int): Book? = store[id]

    override fun findByIsbn(isbn: String): Book? =
        store.values.firstOrNull { it.isbn == isbn }

    override fun findByAuthor(authorId: Int): List<Book> =
        store.values.filter { it.authorId == authorId }.sortedBy { it.id }

    override fun search(keyword: String): List<Book> {
        val needle = keyword.lowercase()
        return store.values.filter {
            it.title.lowercase().contains(needle) || it.genre.lowercase().contains(needle)
        }.sortedBy { it.id }
    }

    override fun create(request: CreateBookRequest): Book {
        val book = Book(
            id = sequence.incrementAndGet(),
            title = request.title,
            authorId = request.authorId,
            isbn = request.isbn,
            genre = request.genre,
            totalCopies = request.totalCopies,
            availableCopies = request.totalCopies,
            publishedYear = request.publishedYear
        )
        store[book.id] = book
        return book
    }

    override fun update(id: Int, request: UpdateBookRequest): Book? {
        val existing = store[id] ?: return null
        val borrowed = existing.totalCopies - existing.availableCopies
        val updated = existing.copy(
            title = request.title,
            isbn = request.isbn,
            genre = request.genre,
            totalCopies = request.totalCopies,
            availableCopies = request.totalCopies - borrowed,
            publishedYear = request.publishedYear
        )
        store[id] = updated
        return updated
    }

    override fun delete(id: Int): Boolean = store.remove(id) != null

    override fun changeAvailableCopies(id: Int, delta: Int): Book? {
        val existing = store[id] ?: return null
        val updated = existing.copy(availableCopies = existing.availableCopies + delta)
        store[id] = updated
        return updated
    }

    override fun countByAuthor(authorId: Int): Long =
        store.values.count { it.authorId == authorId }.toLong()
}

class InMemoryLoanRepository : LoanRepository {
    private val store = ConcurrentHashMap<Int, Loan>()
    private val sequence = AtomicInteger(0)

    override fun findAll(): List<Loan> = store.values.sortedBy { it.id }

    override fun findById(id: Int): Loan? = store[id]

    override fun findActiveByBorrower(borrowerName: String): List<Loan> =
        store.values.filter {
            it.borrowerName.equals(borrowerName, ignoreCase = true) && it.status == LoanStatus.ACTIVE
        }.sortedBy { it.id }

    override fun countActiveByBorrower(borrowerName: String): Long =
        findActiveByBorrower(borrowerName).size.toLong()

    override fun create(bookId: Int, borrowerName: String, borrowedAt: Long, dueAt: Long): Loan {
        val loan = Loan(
            id = sequence.incrementAndGet(),
            bookId = bookId,
            borrowerName = borrowerName,
            borrowedAt = borrowedAt,
            dueAt = dueAt,
            returnedAt = null,
            status = LoanStatus.ACTIVE
        )
        store[loan.id] = loan
        return loan
    }

    override fun markReturned(id: Int, returnedAt: Long): Loan? {
        val existing = store[id] ?: return null
        val updated = existing.copy(
            returnedAt = returnedAt,
            status = LoanStatus.RETURNED
        )
        store[id] = updated
        return updated
    }

    override fun countActive(): Long =
        store.values.count { it.status == LoanStatus.ACTIVE }.toLong()
}

class InMemoryUserRepository(seedUsers: List<Pair<String, Pair<String, UserRole>>> = emptyList()) : UserRepository {
    private val store = ConcurrentHashMap<Int, User>()
    private val sequence = AtomicInteger(0)

    init {
        seedUsers.forEach { (username, credentials) ->
            create(username, credentials.first, credentials.second)
        }
    }

    override fun findByUsername(username: String): User? =
        store.values.firstOrNull { it.username.equals(username, ignoreCase = true) }

    override fun create(username: String, passwordHash: String, role: UserRole): User {
        val user = User(
            id = sequence.incrementAndGet(),
            username = username,
            passwordHash = passwordHash,
            role = role
        )
        store[user.id] = user
        return user
    }

    override fun findAll(): List<User> = store.values.sortedBy { it.id }
}

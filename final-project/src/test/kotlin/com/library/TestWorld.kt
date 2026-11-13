package com.library

import com.library.cache.BookCache
import com.library.domain.UserRole
import com.library.plugins.prometheusRegistry
import com.library.repository.memory.InMemoryAuthorRepository
import com.library.repository.memory.InMemoryBookRepository
import com.library.repository.memory.InMemoryLoanRepository
import com.library.repository.memory.InMemoryUserRepository
import com.library.security.JwtService
import com.library.service.AuthService
import com.library.service.AuthorService
import com.library.service.BookService
import com.library.service.LoanService
import org.mindrot.jbcrypt.BCrypt
import kotlinx.serialization.json.Json

/**
 * Shared in-memory fixture: the same wiring as production, but with the memory
 * repositories so no PostgreSQL instance is required.
 */
class TestWorld(
    val maxActiveLoans: Long = 5
) {
    val authorRepository = InMemoryAuthorRepository()
    val bookRepository = InMemoryBookRepository()
    val loanRepository = InMemoryLoanRepository()
    val userRepository = InMemoryUserRepository()

    val cache = BookCache(ttlMinutes = 5)
    val jwt = JwtService(secret = "test-secret-test-secret-test-secret-0123456789")

    val bookService = BookService(bookRepository, authorRepository, cache)
    val authorService = AuthorService(authorRepository, bookRepository)
    val loanService = LoanService(loanRepository, bookRepository, bookService, maxActiveLoans)
    val authService = AuthService(userRepository, jwt)

    init {
        seedUsers()
    }

    private fun seedUsers() {
        listOf(
            Triple("admin", "admin123", UserRole.ADMIN),
            Triple("librarian", "librarian123", UserRole.LIBRARIAN),
            Triple("reader", "reader123", UserRole.USER)
        ).forEach { (username, password, role) ->
            userRepository.create(username, BCrypt.hashpw(password, BCrypt.gensalt(4)), role)
        }
    }

    fun tokenFor(username: String): String {
        val user = userRepository.findByUsername(username)!!
        return jwt.generateToken(user.username, user.role)
    }

    fun adminToken(): String = tokenFor("admin")
    fun librarianToken(): String = tokenFor("librarian")
    fun readerToken(): String = tokenFor("reader")

    fun dependencies(): AppDependencies = AppDependencies(
        jwt = jwt,
        cache = cache,
        authService = authService,
        authorService = authorService,
        bookService = bookService,
        loanService = loanService,
        registry = prometheusRegistry(cache),
        dbCheck = { true }
    )

    /** Convenience seeders used by several tests. */
    fun newAuthor(name: String = "Ada Lovelace", birthYear: Int = 1815): Int =
        authorService.create(
            com.library.domain.CreateAuthorRequest(name, "British", birthYear)
        ).dataOrNull()!!.id

    fun newBook(
        title: String = "Notes on the Analytical Engine",
        isbn: String = "9783161484100",
        copies: Int = 2,
        authorId: Int = newAuthor()
    ): Int = bookService.create(
        com.library.domain.CreateBookRequest(
            title = title,
            authorId = authorId,
            isbn = isbn,
            genre = "Computing",
            totalCopies = copies
        )
    ).dataOrNull()!!.id
}

/** Lenient JSON for assertions in tests. */
val testJson = Json { ignoreUnknownKeys = true }

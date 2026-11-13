package com.library

import com.library.cache.BookCache
import com.library.domain.UserRole
import com.library.persistence.DatabaseFactory
import com.library.plugins.configureErrorHandling
import com.library.plugins.configureLogging
import com.library.plugins.configureMetrics
import com.library.plugins.configureSerialization
import com.library.plugins.healthRoutes
import com.library.plugins.metricsRoutes
import com.library.plugins.prometheusRegistry
import com.library.repository.AuthorRepository
import com.library.repository.BookRepository
import com.library.repository.LoanRepository
import com.library.repository.UserRepository
import com.library.repository.memory.InMemoryAuthorRepository
import com.library.repository.memory.InMemoryBookRepository
import com.library.repository.memory.InMemoryLoanRepository
import com.library.repository.memory.InMemoryUserRepository
import com.library.repository.postgres.PostgresAuthorRepository
import com.library.repository.postgres.PostgresBookRepository
import com.library.repository.postgres.PostgresLoanRepository
import com.library.repository.postgres.PostgresUserRepository
import com.library.routes.authRoutes
import com.library.routes.authorRoutes
import com.library.routes.bookRoutes
import com.library.routes.loanRoutes
import com.library.security.JwtService
import com.library.service.AuthService
import com.library.service.AuthorService
import com.library.service.BookService
import com.library.service.LoanService
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory

/** Everything the HTTP layer needs, wired once at startup (and injectable in tests). */
class AppDependencies(
    val jwt: JwtService,
    val cache: BookCache,
    val authService: AuthService,
    val authorService: AuthorService,
    val bookService: BookService,
    val loanService: LoanService,
    val registry: PrometheusMeterRegistry,
    val dbCheck: () -> Boolean,
    val startedAt: Long = System.currentTimeMillis()
)

private val log = LoggerFactory.getLogger("com.library.Application")

/**
 * Wires services on top of the PostgreSQL repositories, falling back to the in-memory
 * implementations when the database is unreachable so the service still starts and can
 * be demonstrated on a machine without PostgreSQL/Docker.
 */
fun buildDependencies(): AppDependencies {
    val jwt = JwtService()
    val cache = BookCache()

    val authors: AuthorRepository
    val books: BookRepository
    val loans: LoanRepository
    val users: UserRepository
    var dbUp = false

    val reachable = DatabaseFactory.ping()
    if (reachable && DatabaseFactory.initSchema()) {
        dbUp = true
        authors = PostgresAuthorRepository()
        books = PostgresBookRepository()
        loans = PostgresLoanRepository()
        users = PostgresUserRepository()
        log.info("Using PostgreSQL repositories at {}", DatabaseFactory.jdbcUrl)
    } else {
        authors = InMemoryAuthorRepository()
        books = InMemoryBookRepository()
        loans = InMemoryLoanRepository()
        users = InMemoryUserRepository()
        log.warn("PostgreSQL not reachable - running with in-memory repositories")
    }

    seedAdmin(users)

    val bookService = BookService(books, authors, cache)
    val authorService = AuthorService(authors, books)
    val loanService = LoanService(loans, books, bookService)
    val authService = AuthService(users, jwt)
    val registry = prometheusRegistry(cache)

    return AppDependencies(
        jwt = jwt,
        cache = cache,
        authService = authService,
        authorService = authorService,
        bookService = bookService,
        loanService = loanService,
        registry = registry,
        dbCheck = { if (dbUp) DatabaseFactory.ping() else false }
    )
}

/** Creates the default admin/librarian accounts on an empty database. */
private fun seedAdmin(users: UserRepository) {
    val seeds = listOf(
        Triple("admin", "admin123", UserRole.ADMIN),
        Triple("librarian", "librarian123", UserRole.LIBRARIAN),
        Triple("reader", "reader123", UserRole.USER)
    )
    seeds.forEach { (username, password, role) ->
        if (users.findByUsername(username) == null) {
            users.create(username, BCrypt.hashpw(password, BCrypt.gensalt(10)), role)
            log.info("Seeded default {} account '{}'", role, username)
        }
    }
}

/** Installs plugins and routes. Kept separate so tests can call it with fake dependencies. */
fun Application.libraryModule(deps: AppDependencies) {
    configureSerialization()
    configureLogging()
    configureErrorHandling()
    configureMetrics(deps.registry)

    routing {
        healthRoutes(deps.dbCheck, deps.cache, deps.startedAt)
        metricsRoutes(deps.registry)
        authRoutes(deps.authService, deps.jwt)
        authorRoutes(deps.authorService, deps.jwt)
        bookRoutes(deps.bookService, deps.jwt)
        loanRoutes(deps.loanService, deps.jwt)
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val deps = buildDependencies()
    log.info("Starting library-service on port {}", port)
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        libraryModule(deps)
    }.start(wait = true)
}

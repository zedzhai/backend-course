package com.library.persistence

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

/**
 * Creates the PostgreSQL connection and the schema at startup.
 *
 * Connection settings come from environment variables so the same image works with
 * docker-compose (host `db`) and a local PostgreSQL install alike.
 *
 * Schema (4 tables, requirement asks for at least 3):
 *   authors(id, name, nationality, birth_year)
 *   books(id, title, author_id -> authors.id, isbn, genre, total_copies, available_copies, published_year)
 *   loans(id, book_id -> books.id, borrower_name, borrowed_at, due_at, returned_at, status)
 *   users(id, username, password_hash, role)
 */
object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    val jdbcUrl: String = System.getenv("DATABASE_URL")
        ?: "jdbc:postgresql://localhost:5432/library"
    val user: String = System.getenv("DATABASE_USER") ?: "postgres"
    val password: String = System.getenv("DATABASE_PASSWORD") ?: "postgres"

    /** Opens a plain JDBC connection; the Exposed-free repositories use this. */
    fun connect(): Connection = DriverManager.getConnection(jdbcUrl, user, password)

    /** Verifies the database is reachable; used by the /health endpoint. */
    fun ping(): Boolean = try {
        connect().use { it.isValid(2) }
    } catch (e: Exception) {
        log.warn("Database ping failed: {}", e.message)
        false
    }

    /** Creates the three domain tables plus the users table; safe to re-run. */
    fun initSchema(): Boolean = try {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                schemaStatements().forEach { statement.execute(it) }
            }
        }
        log.info("PostgreSQL schema ready at {}", jdbcUrl)
        true
    } catch (e: Exception) {
        log.warn("PostgreSQL schema initialisation failed ({}): {}", jdbcUrl, e.message)
        false
    }

    fun schemaStatements(): List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS authors (
            id SERIAL PRIMARY KEY,
            name VARCHAR(120) NOT NULL UNIQUE,
            nationality VARCHAR(80),
            birth_year INTEGER
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS books (
            id SERIAL PRIMARY KEY,
            title VARCHAR(200) NOT NULL,
            author_id INTEGER NOT NULL REFERENCES authors(id),
            isbn VARCHAR(20) NOT NULL UNIQUE,
            genre VARCHAR(60) NOT NULL,
            total_copies INTEGER NOT NULL,
            available_copies INTEGER NOT NULL,
            published_year INTEGER
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS loans (
            id SERIAL PRIMARY KEY,
            book_id INTEGER NOT NULL REFERENCES books(id),
            borrower_name VARCHAR(120) NOT NULL,
            borrowed_at TIMESTAMP NOT NULL,
            due_at TIMESTAMP NOT NULL,
            returned_at TIMESTAMP,
            status VARCHAR(20) NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS users (
            id SERIAL PRIMARY KEY,
            username VARCHAR(32) NOT NULL UNIQUE,
            password_hash VARCHAR(100) NOT NULL,
            role VARCHAR(20) NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_books_author ON books(author_id)",
        "CREATE INDEX IF NOT EXISTS idx_loans_book ON loans(book_id)",
        "CREATE INDEX IF NOT EXISTS idx_loans_borrower ON loans(borrower_name)"
    )
}

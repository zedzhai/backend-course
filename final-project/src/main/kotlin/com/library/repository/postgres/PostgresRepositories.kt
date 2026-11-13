package com.library.repository.postgres

import com.library.domain.Author
import com.library.domain.Book
import com.library.domain.CreateAuthorRequest
import com.library.domain.CreateBookRequest
import com.library.domain.Loan
import com.library.domain.LoanStatus
import com.library.domain.UpdateBookRequest
import com.library.domain.User
import com.library.domain.UserRole
import com.library.persistence.DatabaseFactory
import com.library.repository.AuthorRepository
import com.library.repository.BookRepository
import com.library.repository.LoanRepository
import com.library.repository.UserRepository
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * Production repositories backed by PostgreSQL (plain JDBC, no ORM magic).
 * Selected automatically when the database is reachable; see Application.kt.
 */
class PostgresAuthorRepository : AuthorRepository {

    private fun map(rs: ResultSet) = Author(
        id = rs.getInt("id"),
        name = rs.getString("name"),
        nationality = rs.getString("nationality"),
        birthYear = rs.getInt("birth_year").takeUnless { rs.wasNull() }
    )

    private fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T?): T? =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs -> if (rs.next()) mapper(rs) else null }
            }
        }

    override fun findAll(): List<Author> = DatabaseFactory.connect().use { connection ->
        connection.prepareStatement("SELECT * FROM authors ORDER BY id").use { statement ->
            statement.executeQuery().use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
    }

    override fun findById(id: Int): Author? =
        query("SELECT * FROM authors WHERE id = ?", id) { map(it) }

    override fun findByName(name: String): Author? =
        query("SELECT * FROM authors WHERE LOWER(name) = LOWER(?)", name) { map(it) }

    override fun create(request: CreateAuthorRequest): Author =
        query(
            "INSERT INTO authors (name, nationality, birth_year) VALUES (?, ?, ?) RETURNING *",
            request.name,
            request.nationality,
            request.birthYear
        ) { map(it) }!!

    override fun update(id: Int, request: CreateAuthorRequest): Author? =
        query(
            "UPDATE authors SET name = ?, nationality = ?, birth_year = ? WHERE id = ? RETURNING *",
            request.name,
            request.nationality,
            request.birthYear,
            id
        ) { map(it) }

    override fun delete(id: Int): Boolean = DatabaseFactory.connect().use { connection ->
        connection.prepareStatement("DELETE FROM authors WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeUpdate() > 0
        }
    }
}

class PostgresBookRepository : BookRepository {

    private fun map(rs: ResultSet) = Book(
        id = rs.getInt("id"),
        title = rs.getString("title"),
        authorId = rs.getInt("author_id"),
        isbn = rs.getString("isbn"),
        genre = rs.getString("genre"),
        totalCopies = rs.getInt("total_copies"),
        availableCopies = rs.getInt("available_copies"),
        publishedYear = rs.getInt("published_year").takeUnless { rs.wasNull() }
    )

    private fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T?): T? =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs -> if (rs.next()) mapper(rs) else null }
            }
        }

    private fun list(sql: String, vararg params: Any?): List<Book> =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(map(rs)) }
                }
            }
        }

    override fun findAll(): List<Book> = list("SELECT * FROM books ORDER BY id")

    override fun findById(id: Int): Book? =
        query("SELECT * FROM books WHERE id = ?", id) { map(it) }

    override fun findByIsbn(isbn: String): Book? =
        query("SELECT * FROM books WHERE isbn = ?", isbn) { map(it) }

    override fun findByAuthor(authorId: Int): List<Book> =
        list("SELECT * FROM books WHERE author_id = ? ORDER BY id", authorId)

    override fun search(keyword: String): List<Book> =
        list(
            "SELECT * FROM books WHERE LOWER(title) LIKE ? OR LOWER(genre) LIKE ? ORDER BY id",
            "%${keyword.lowercase()}%",
            "%${keyword.lowercase()}%"
        )

    override fun create(request: CreateBookRequest): Book =
        query(
            """
            INSERT INTO books (title, author_id, isbn, genre, total_copies, available_copies, published_year)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *
            """.trimIndent(),
            request.title,
            request.authorId,
            request.isbn,
            request.genre,
            request.totalCopies,
            request.totalCopies,
            request.publishedYear
        ) { map(it) }!!

    override fun update(id: Int, request: UpdateBookRequest): Book? {
        val existing = findById(id) ?: return null
        val borrowed = existing.totalCopies - existing.availableCopies
        return query(
            """
            UPDATE books SET title = ?, isbn = ?, genre = ?, total_copies = ?, available_copies = ?, published_year = ?
            WHERE id = ? RETURNING *
            """.trimIndent(),
            request.title,
            request.isbn,
            request.genre,
            request.totalCopies,
            request.totalCopies - borrowed,
            request.publishedYear,
            id
        ) { map(it) }
    }

    override fun delete(id: Int): Boolean = DatabaseFactory.connect().use { connection ->
        connection.prepareStatement("DELETE FROM books WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeUpdate() > 0
        }
    }

    override fun changeAvailableCopies(id: Int, delta: Int): Book? =
        query(
            "UPDATE books SET available_copies = available_copies + ? WHERE id = ? RETURNING *",
            delta,
            id
        ) { map(it) }

    override fun countByAuthor(authorId: Int): Long =
        query("SELECT COUNT(*) AS total FROM books WHERE author_id = ?", authorId) {
            it.getLong("total")
        } ?: 0L
}

class PostgresLoanRepository : LoanRepository {

    private fun map(rs: ResultSet) = Loan(
        id = rs.getInt("id"),
        bookId = rs.getInt("book_id"),
        borrowerName = rs.getString("borrower_name"),
        borrowedAt = rs.getTimestamp("borrowed_at").time,
        dueAt = rs.getTimestamp("due_at").time,
        returnedAt = rs.getTimestamp("returned_at")?.time,
        status = runCatching { LoanStatus.valueOf(rs.getString("status")) }.getOrDefault(LoanStatus.ACTIVE)
    )

    private fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T?): T? =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs -> if (rs.next()) mapper(rs) else null }
            }
        }

    private fun list(sql: String, vararg params: Any?): List<Loan> =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(map(rs)) }
                }
            }
        }

    override fun findAll(): List<Loan> = list("SELECT * FROM loans ORDER BY id")

    override fun findById(id: Int): Loan? =
        query("SELECT * FROM loans WHERE id = ?", id) { map(it) }

    override fun findActiveByBorrower(borrowerName: String): List<Loan> =
        list(
            "SELECT * FROM loans WHERE LOWER(borrower_name) = LOWER(?) AND status = 'ACTIVE' ORDER BY id",
            borrowerName
        )

    override fun countActiveByBorrower(borrowerName: String): Long =
        query(
            "SELECT COUNT(*) AS total FROM loans WHERE LOWER(borrower_name) = LOWER(?) AND status = 'ACTIVE'",
            borrowerName
        ) { it.getLong("total") } ?: 0L

    override fun create(bookId: Int, borrowerName: String, borrowedAt: Long, dueAt: Long): Loan =
        query(
            """
            INSERT INTO loans (book_id, borrower_name, borrowed_at, due_at, returned_at, status)
            VALUES (?, ?, ?, ?, NULL, 'ACTIVE') RETURNING *
            """.trimIndent(),
            bookId,
            borrowerName,
            Timestamp(borrowedAt),
            Timestamp(dueAt)
        ) { map(it) }!!

    override fun markReturned(id: Int, returnedAt: Long): Loan? =
        query(
            """
            UPDATE loans SET returned_at = ?, status = 'RETURNED' WHERE id = ? AND status = 'ACTIVE' RETURNING *
            """.trimIndent(),
            Timestamp(returnedAt),
            id
        ) { map(it) }

    override fun countActive(): Long =
        query("SELECT COUNT(*) AS total FROM loans WHERE status = 'ACTIVE'") { it.getLong("total") } ?: 0L
}

class PostgresUserRepository : UserRepository {

    private fun map(rs: ResultSet) = User(
        id = rs.getInt("id"),
        username = rs.getString("username"),
        passwordHash = rs.getString("password_hash"),
        role = runCatching { UserRole.valueOf(rs.getString("role")) }.getOrDefault(UserRole.USER)
    )

    override fun findByUsername(username: String): User? =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement("SELECT * FROM users WHERE LOWER(username) = LOWER(?)").use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
            }
        }

    override fun create(username: String, passwordHash: String, role: UserRole): User =
        DatabaseFactory.connect().use { connection ->
            connection.prepareStatement(
                "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?) RETURNING *"
            ).use { statement ->
                statement.setString(1, username)
                statement.setString(2, passwordHash)
                statement.setString(3, role.name)
                statement.executeQuery().use { rs ->
                    rs.next()
                    map(rs)
                }
            }
        }

    override fun findAll(): List<User> = DatabaseFactory.connect().use { connection ->
        connection.prepareStatement("SELECT * FROM users ORDER BY id").use { statement ->
            statement.executeQuery().use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
    }
}

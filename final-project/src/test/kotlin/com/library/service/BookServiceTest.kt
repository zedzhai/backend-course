package com.library.service

import com.library.TestWorld
import com.library.common.ServiceResult
import com.library.domain.Book
import com.library.domain.CreateBookRequest
import com.library.domain.UpdateBookRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookServiceTest {

    private val world = TestWorld()
    private val authorId = world.newAuthor("Martin Fowler")

    @Test
    fun `create book with valid payload returns success`() {
        val result = world.bookService.create(
            CreateBookRequest("Refactoring", authorId, "9780134757599", "Software", 3, 2018)
        )
        val book = assertIs<ServiceResult.Success<Book>>(result).data
        assertEquals(3, book.availableCopies)
        assertEquals(authorId, book.authorId)
    }

    @Test
    fun `create book with invalid isbn is rejected`() {
        val result = world.bookService.create(
            CreateBookRequest("Bad ISBN", authorId, "abc", "Software", 1)
        )
        val failure = assertIs<ServiceResult.ValidationFailure>(result)
        assertTrue(failure.errors.any { it.contains("isbn") })
    }

    @Test
    fun `create book with non positive copies is rejected`() {
        val result = world.bookService.create(
            CreateBookRequest("Zero Copies", authorId, "9780134757599", "Software", 0)
        )
        assertIs<ServiceResult.ValidationFailure>(result)
    }

    @Test
    fun `create book for unknown author returns not found`() {
        val result = world.bookService.create(
            CreateBookRequest("Orphan", 4242, "9780134757599", "Software", 1)
        )
        assertIs<ServiceResult.NotFound>(result)
    }

    @Test
    fun `duplicate isbn returns conflict`() {
        world.newBook(isbn = "9780262033848", authorId = authorId)
        val result = world.bookService.create(
            CreateBookRequest("Duplicate", authorId, "9780262033848", "Software", 1)
        )
        assertIs<ServiceResult.Conflict>(result)
    }

    @Test
    fun `get populates the cache so the second call is a hit`() {
        val bookId = world.newBook(isbn = "9780132350884", authorId = authorId)
        world.cache.invalidateAll()

        world.bookService.get(bookId)   // cache miss -> repository -> put
        world.bookService.get(bookId)   // cache hit -> no repository access

        val stats = world.bookService.cacheStats()
        assertEquals(1, stats.missCount)
        assertEquals(1, stats.hitCount)
        assertEquals(0.5, stats.hitRate)
        assertEquals(1L, stats.size)
    }

    @Test
    fun `get missing book returns not found and is not cached`() {
        assertIs<ServiceResult.NotFound>(world.bookService.get(1234))
        assertNull(world.cache.get(1234))
    }

    @Test
    fun `update refreshes the cached copy`() {
        val bookId = world.newBook(isbn = "9780321356680", authorId = authorId)
        world.bookService.get(bookId)

        world.bookService.update(
            bookId,
            UpdateBookRequest("Effective Java 3rd", "9780321356680", "Software", 5, 2017)
        )

        val cached = assertNotNull(world.cache.get(bookId))
        assertEquals("Effective Java 3rd", cached.title)
        assertEquals(5, cached.totalCopies)
    }

    @Test
    fun `update cannot shrink totalCopies below borrowed amount`() {
        val bookId = world.newBook(isbn = "9780596009205", copies = 2, authorId = authorId)
        world.loanService.borrow(
            com.library.domain.CreateLoanRequest(bookId, "Alice", 7)
        )

        val result = world.bookService.update(
            bookId,
            UpdateBookRequest("Head First", "9780596009205", "Software", 0, 2004)
        )
        assertIs<ServiceResult.ValidationFailure>(result)
    }

    @Test
    fun `delete removes the book and evicts the cache entry`() {
        val bookId = world.newBook(isbn = "9780201633610", authorId = authorId)
        world.bookService.get(bookId)
        assertNotNull(world.cache.get(bookId))

        assertIs<ServiceResult.Success<Unit>>(world.bookService.delete(bookId))
        assertNull(world.cache.get(bookId))
        assertIs<ServiceResult.NotFound>(world.bookService.get(bookId))
    }

    @Test
    fun `delete refuses a book with copies on loan`() {
        val bookId = world.newBook(isbn = "9781449355739", copies = 1, authorId = authorId)
        world.loanService.borrow(com.library.domain.CreateLoanRequest(bookId, "Bob", 7))
        assertIs<ServiceResult.Conflict>(world.bookService.delete(bookId))
    }

    @Test
    fun `search matches title keywords case insensitively`() {
        world.newBook(title = "Kotlin in Action", isbn = "9781617293290", authorId = authorId)
        val hits = world.bookService.search("kotlin")
        assertEquals(1, hits.size)
        assertEquals("Kotlin in Action", hits.first().title)
    }
}

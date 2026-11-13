package com.library.service

import com.library.cache.BookCache
import com.library.cache.CacheStatsSnapshot
import com.library.common.ServiceResult
import com.library.domain.Book
import com.library.domain.CreateBookRequest
import com.library.domain.UpdateBookRequest
import com.library.repository.AuthorRepository
import com.library.repository.BookRepository
import java.time.Year

/**
 * Book business logic plus the cache-aside read path.
 *
 * GET /api/books/{id} is served from Caffeine when possible; every write path
 * refreshes or evicts the matching key so the cache never goes stale.
 */
class BookService(
    private val books: BookRepository,
    private val authors: AuthorRepository,
    private val cache: BookCache
) {
    private val currentYear = Year.now().value

    fun list(): List<Book> = books.findAll()

    fun search(keyword: String): List<Book> =
        if (keyword.isBlank()) books.findAll() else books.search(keyword.trim())

    fun byAuthor(authorId: Int): List<Book> = books.findByAuthor(authorId)

    /**
     * Cache-aside lookup: try the cache, fall back to the repository, then populate.
     * Hits and misses are tracked by Caffeine and surfaced on /metrics/cache.
     */
    fun get(id: Int): ServiceResult<Book> {
        cache.get(id)?.let { return ServiceResult.Success(it) }
        val book = books.findById(id)
            ?: return ServiceResult.NotFound("Book with id=$id not found")
        cache.put(book)
        return ServiceResult.Success(book)
    }

    fun create(request: CreateBookRequest): ServiceResult<Book> {
        val validator = validate(
            title = request.title,
            isbn = request.isbn,
            genre = request.genre,
            totalCopies = request.totalCopies,
            publishedYear = request.publishedYear,
            authorId = request.authorId
        )
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }
        authors.findById(request.authorId)
            ?: return ServiceResult.NotFound("Author with id=${request.authorId} not found")
        books.findByIsbn(request.isbn.trim())?.let {
            return ServiceResult.Conflict("Book with isbn=${request.isbn} already exists (id=${it.id})")
        }
        val created = books.create(normalize(request))
        cache.put(created)
        return ServiceResult.Success(created)
    }

    fun update(id: Int, request: UpdateBookRequest): ServiceResult<Book> {
        val existing = books.findById(id)
            ?: return ServiceResult.NotFound("Book with id=$id not found")

        val validator = validate(
            title = request.title,
            isbn = request.isbn,
            genre = request.genre,
            totalCopies = request.totalCopies,
            publishedYear = request.publishedYear,
            authorId = null
        )
        val borrowed = existing.totalCopies - existing.availableCopies
        if (request.totalCopies < borrowed) {
            validator.add("totalCopies must be at least the $borrowed copies currently on loan")
        }
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }
        val duplicate = books.findByIsbn(request.isbn.trim())
        if (duplicate != null && duplicate.id != id) {
            return ServiceResult.Conflict("Book with isbn=${request.isbn} already exists (id=${duplicate.id})")
        }
        val updated = books.update(
            id,
            request.copy(
                title = request.title.trim(),
                isbn = request.isbn.trim(),
                genre = request.genre.trim()
            )
        ) ?: return ServiceResult.NotFound("Book with id=$id not found")
        cache.put(updated)
        return ServiceResult.Success(updated)
    }

    fun delete(id: Int): ServiceResult<Unit> {
        val existing = books.findById(id)
            ?: return ServiceResult.NotFound("Book with id=$id not found")
        if (existing.availableCopies < existing.totalCopies) {
            return ServiceResult.Conflict(
                "Book id=$id still has ${existing.totalCopies - existing.availableCopies} copy(ies) on loan"
            )
        }
        books.delete(id)
        cache.invalidate(id)
        return ServiceResult.Success(Unit)
    }

    /** Used by the loan service after a borrow/return so availability is never stale. */
    fun refreshCache(id: Int) {
        books.findById(id)?.let { cache.put(it) } ?: cache.invalidate(id)
    }

    fun cacheStats(): CacheStatsSnapshot = cache.stats()

    private fun validate(
        title: String?,
        isbn: String?,
        genre: String?,
        totalCopies: Int,
        publishedYear: Int?,
        authorId: Int?
    ): Validator {
        val validator = Validator()
        validator.requireNotBlank(title, "title", minLength = 1)
        validator.requireMaxLength(title, "title", 200)
        validator.requireNotBlank(isbn, "isbn")
        validator.requireMatches(isbn?.trim(), "isbn", ISBN_REGEX, "must be a valid ISBN-10 or ISBN-13")
        validator.requireNotBlank(genre, "genre", minLength = 2)
        validator.requireMaxLength(genre, "genre", 60)
        validator.requirePositive(totalCopies, "totalCopies")
        validator.requireRange(publishedYear, "publishedYear", 1000, currentYear)
        if (authorId != null) validator.requirePositive(authorId, "authorId")
        return validator
    }

    private fun normalize(request: CreateBookRequest) = request.copy(
        title = request.title.trim(),
        isbn = request.isbn.trim(),
        genre = request.genre.trim()
    )
}

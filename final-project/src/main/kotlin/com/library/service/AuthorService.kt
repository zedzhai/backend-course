package com.library.service

import com.library.common.ServiceResult
import com.library.domain.Author
import com.library.domain.CreateAuthorRequest
import com.library.repository.AuthorRepository
import com.library.repository.BookRepository
import java.time.Year

/**
 * Author business logic. Contains all validation, so the repository stays a dumb
 * storage abstraction and the routes stay a thin HTTP layer.
 */
class AuthorService(
    private val authors: AuthorRepository,
    private val books: BookRepository
) {
    private val currentYear = Year.now().value

    fun list(): List<Author> = authors.findAll()

    fun get(id: Int): ServiceResult<Author> {
        val author = authors.findById(id)
            ?: return ServiceResult.NotFound("Author with id=$id not found")
        return ServiceResult.Success(author)
    }

    fun create(request: CreateAuthorRequest): ServiceResult<Author> {
        val validator = validate(request)
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }
        authors.findByName(request.name.trim())?.let {
            return ServiceResult.Conflict("Author named '${it.name}' already exists (id=${it.id})")
        }
        return ServiceResult.Success(authors.create(normalize(request)))
    }

    fun update(id: Int, request: CreateAuthorRequest): ServiceResult<Author> {
        authors.findById(id)
            ?: return ServiceResult.NotFound("Author with id=$id not found")

        val validator = validate(request)
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }
        val sameName = authors.findByName(request.name.trim())
        if (sameName != null && sameName.id != id) {
            return ServiceResult.Conflict("Author named '${sameName.name}' already exists (id=${sameName.id})")
        }
        val updated = authors.update(id, normalize(request))
            ?: return ServiceResult.NotFound("Author with id=$id not found")
        return ServiceResult.Success(updated)
    }

    fun delete(id: Int): ServiceResult<Unit> {
        authors.findById(id)
            ?: return ServiceResult.NotFound("Author with id=$id not found")
        val bookCount = books.countByAuthor(id)
        if (bookCount > 0) {
            return ServiceResult.Conflict(
                "Author id=$id still has $bookCount book(s); delete them first"
            )
        }
        authors.delete(id)
        return ServiceResult.Success(Unit)
    }

    private fun validate(request: CreateAuthorRequest): Validator {
        val validator = Validator()
        validator.requireNotBlank(request.name, "name", minLength = 2)
        validator.requireMaxLength(request.name, "name", 120)
        validator.requireMaxLength(request.nationality, "nationality", 80)
        validator.requireRange(request.birthYear, "birthYear", 1000, currentYear)
        return validator
    }

    private fun normalize(request: CreateAuthorRequest) = request.copy(
        name = request.name.trim(),
        nationality = request.nationality?.trim()?.ifBlank { null }
    )
}

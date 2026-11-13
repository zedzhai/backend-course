package com.library.service

import com.library.TestWorld
import com.library.common.ServiceResult
import com.library.domain.CreateAuthorRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthorServiceTest {

    private val world = TestWorld()

    @Test
    fun `create author with valid payload returns success`() {
        val result = world.authorService.create(CreateAuthorRequest("George Orwell", "British", 1903))
        val author = assertIs<ServiceResult.Success<*>>(result).data
        assertTrue(author.toString().contains("George Orwell"))
    }

    @Test
    fun `create author with blank name is rejected`() {
        val result = world.authorService.create(CreateAuthorRequest("  ", null, 1903))
        val failure = assertIs<ServiceResult.ValidationFailure>(result)
        assertTrue(failure.errors.any { it.contains("name") })
    }

    @Test
    fun `create author with too short name is rejected`() {
        val result = world.authorService.create(CreateAuthorRequest("A", null, null))
        val failure = assertIs<ServiceResult.ValidationFailure>(result)
        assertTrue(failure.errors.any { it.contains("at least 2") })
    }

    @Test
    fun `create author with future birth year is rejected`() {
        val result = world.authorService.create(CreateAuthorRequest("Future Writer", null, 2999))
        val failure = assertIs<ServiceResult.ValidationFailure>(result)
        assertTrue(failure.errors.any { it.contains("birthYear") })
    }

    @Test
    fun `duplicate author name returns conflict`() {
        world.authorService.create(CreateAuthorRequest("Ursula Le Guin", "American", 1929))
        val result = world.authorService.create(CreateAuthorRequest("ursula le guin", "American", 1929))
        assertIs<ServiceResult.Conflict>(result)
    }

    @Test
    fun `get missing author returns not found`() {
        assertIs<ServiceResult.NotFound>(world.authorService.get(999))
    }

    @Test
    fun `update author changes fields`() {
        val authorId = world.newAuthor("Old Name")
        val result = world.authorService.update(
            authorId,
            CreateAuthorRequest("New Name", "French", 1900)
        )
        val updated = assertIs<ServiceResult.Success<com.library.domain.Author>>(result).data
        assertEquals("New Name", updated.name)
        assertEquals("French", updated.nationality)
    }

    @Test
    fun `delete author with books returns conflict`() {
        val authorId = world.newAuthor("With Books")
        world.newBook(authorId = authorId, isbn = "9783161484999")
        assertIs<ServiceResult.Conflict>(world.authorService.delete(authorId))
    }

    @Test
    fun `delete author without books succeeds`() {
        val authorId = world.newAuthor("Solo Author")
        assertIs<ServiceResult.Success<Unit>>(world.authorService.delete(authorId))
        assertEquals(0, world.authorService.list().size)
    }
}

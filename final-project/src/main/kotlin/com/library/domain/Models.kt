package com.library.domain

import kotlinx.serialization.Serializable

@Serializable
data class Author(
    val id: Int,
    val name: String,
    val nationality: String? = null,
    val birthYear: Int? = null
)

@Serializable
data class CreateAuthorRequest(
    val name: String,
    val nationality: String? = null,
    val birthYear: Int? = null
)

@Serializable
data class Book(
    val id: Int,
    val title: String,
    val authorId: Int,
    val isbn: String,
    val genre: String,
    val totalCopies: Int,
    val availableCopies: Int,
    val publishedYear: Int? = null
)

@Serializable
data class CreateBookRequest(
    val title: String,
    val authorId: Int,
    val isbn: String,
    val genre: String,
    val totalCopies: Int,
    val publishedYear: Int? = null
)

@Serializable
data class UpdateBookRequest(
    val title: String,
    val isbn: String,
    val genre: String,
    val totalCopies: Int,
    val publishedYear: Int? = null
)

@Serializable
enum class LoanStatus {
    ACTIVE,
    RETURNED,
    OVERDUE
}

@Serializable
data class Loan(
    val id: Int,
    val bookId: Int,
    val borrowerName: String,
    /** Epoch millis, keeps JSON free of custom date serializers. */
    val borrowedAt: Long,
    val dueAt: Long,
    val returnedAt: Long? = null,
    val status: LoanStatus
)

@Serializable
data class CreateLoanRequest(
    val bookId: Int,
    val borrowerName: String,
    val days: Int = 14
)

@Serializable
enum class UserRole {
    ADMIN,
    LIBRARIAN,
    USER
}

@Serializable
data class User(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val role: UserRole
)

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val role: UserRole
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val role: String = "USER"
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val username: String,
    val role: String,
    val expiresInMs: Long
)

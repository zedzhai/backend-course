package com.library.service

import com.library.common.ServiceResult
import com.library.domain.LoginRequest
import com.library.domain.LoginResponse
import com.library.domain.RegisterRequest
import com.library.domain.User
import com.library.domain.UserResponse
import com.library.domain.UserRole
import com.library.repository.UserRepository
import com.library.security.JwtService
import org.mindrot.jbcrypt.BCrypt

/**
 * Registration and login. Passwords are BCrypt-hashed (never stored in the clear),
 * and every successful login returns a signed JWT carrying the user's role.
 */
class AuthService(
    private val users: UserRepository,
    private val jwt: JwtService
) {
    fun register(request: RegisterRequest): ServiceResult<UserResponse> {
        val validator = Validator()
        validator.requireMatches(request.username, "username", USERNAME_REGEX, "must be 3-32 letters, digits or underscores")
        validator.requireNotBlank(request.password, "password", minLength = 6)
        validator.requireMaxLength(request.password, "password", 72)

        val role = parseRole(request.role)
        if (role == null) {
            validator.add("role must be one of ADMIN, LIBRARIAN, USER")
        }
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }

        val username = request.username.trim()
        users.findByUsername(username)?.let {
            return ServiceResult.Conflict("Username '$username' is already taken")
        }
        val hash = BCrypt.hashpw(request.password, BCrypt.gensalt(ROUNDS))
        val created = users.create(username, hash, role!!)
        return ServiceResult.Success(UserResponse(created.id, created.username, created.role))
    }

    fun login(request: LoginRequest): ServiceResult<LoginResponse> {
        val validator = Validator()
        validator.requireNotBlank(request.username, "username")
        validator.requireNotBlank(request.password, "password")
        if (validator.hasErrors()) {
            return ServiceResult.ValidationFailure(validator.errors())
        }

        val user: User = users.findByUsername(request.username.trim())
            ?: return ServiceResult.Conflict("Invalid username or password")
        val matches = runCatching { BCrypt.checkpw(request.password, user.passwordHash) }.getOrDefault(false)
        if (!matches) {
            return ServiceResult.Conflict("Invalid username or password")
        }
        val token = jwt.generateToken(user.username, user.role)
        return ServiceResult.Success(
            LoginResponse(
                token = token,
                username = user.username,
                role = user.role.name,
                expiresInMs = jwt.tokenTtlMillis
            )
        )
    }

    fun currentUser(username: String): ServiceResult<UserResponse> {
        val user = users.findByUsername(username)
            ?: return ServiceResult.NotFound("User '$username' not found")
        return ServiceResult.Success(UserResponse(user.id, user.username, user.role))
    }

    fun listUsers(): List<UserResponse> =
        users.findAll().map { UserResponse(it.id, it.username, it.role) }

    private fun parseRole(raw: String?): UserRole? {
        if (raw.isNullOrBlank()) return UserRole.USER
        return runCatching { UserRole.valueOf(raw.trim().uppercase()) }.getOrNull()
    }

    companion object {
        private const val ROUNDS = 10
    }
}

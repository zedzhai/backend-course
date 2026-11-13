package com.library.service

/**
 * Tiny validation accumulator so services can collect every field error at once
 * instead of failing on the first one.
 */
class Validator {
    private val errors = mutableListOf<String>()

    fun requireNotBlank(value: String?, field: String, minLength: Int = 1) {
        if (value.isNullOrBlank()) {
            errors += "$field must not be blank"
        } else if (value.trim().length < minLength) {
            errors += "$field must be at least $minLength characters long"
        }
    }

    fun requireMaxLength(value: String?, field: String, maxLength: Int) {
        if (value != null && value.length > maxLength) {
            errors += "$field must be at most $maxLength characters long"
        }
    }

    fun requirePositive(value: Int, field: String) {
        if (value <= 0) errors += "$field must be greater than 0"
    }

    fun requireRange(value: Int?, field: String, min: Int, max: Int) {
        if (value != null && value !in min..max) {
            errors += "$field must be between $min and $max"
        }
    }

    fun requireMatches(value: String?, field: String, regex: Regex, message: String) {
        if (value != null && !regex.matches(value)) errors += "$field $message"
    }

    fun add(message: String) {
        errors += message
    }

    fun errors(): List<String> = errors.toList()

    fun hasErrors(): Boolean = errors.isNotEmpty()
}

/** ISBN-10 or ISBN-13, hyphens allowed. */
val ISBN_REGEX = Regex("^(?:\\d[- ]?){9}[\\dXx]$|^(?:\\d[- ]?){12}\\d$")

/** Usernames: 3-32 chars, letters/digits/underscore. */
val USERNAME_REGEX = Regex("^[A-Za-z0-9_]{3,32}$")

package com.recipebook.recipe_backend.security

import java.net.URI

/**
 * A request the server refuses because of what was sent, not because something broke. Carries a
 * stable [code] for the client, like [AuthException]; [AuthExceptionResolver] reports it as a
 * BAD_REQUEST instead of an opaque INTERNAL_ERROR.
 */
class InvalidInputException(
    val code: String,
    message: String,
    /** Extra values for the client, placed next to `code` in the error's extensions. */
    val details: Map<String, Any> = emptyMap(),
) : RuntimeException(message)

/**
 * Limits on what users may store. One place, so the GraphQL controllers all apply the same rules.
 */
object InputValidation {
    const val MAX_RECIPE_NAME = 200
    const val MAX_RECIPE_DESCRIPTION = 50_000
    const val MAX_NOTE = 5_000
    const val MAX_URL = 2_000
    const val MAX_INGREDIENT_NAME = 100
    const val MAX_USERNAME = 30
    const val MIN_USERNAME = 3
    const val MAX_EMAIL = 254
    const val MAX_PASSWORD = 200
    const val MAX_PER_100G = 1000f

    // Letters (any script, so Cyrillic names work), digits, dot, underscore, hyphen. No spaces, so
    // "family " cannot pose as "family", and no markup.
    private val USERNAME = Regex("^[\\p{L}\\p{N}._-]+$")

    // Deliberately loose: one @, something on each side, a dot in the domain, no spaces. Reset
    // emails are the real test of an address; this only stops obvious mistakes.
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    private val LOCALES = setOf("en", "ru")

    fun text(value: String, max: Int, field: String, required: Boolean = false): String {
        if (required && value.isBlank()) throw InvalidInputException("EMPTY_FIELD", "$field must not be empty")
        if (value.length > max) throw InvalidInputException("TOO_LONG", "$field is longer than $max characters")
        return value
    }

    /** Trimmed username, or an exception explaining why it is not acceptable. */
    fun username(value: String): String {
        val name = value.trim()
        // Spring's name for a guest. Already harmless (CurrentUserService), but an account called
        // this would make logs and support questions confusing.
        if (name.equals("anonymousUser", ignoreCase = true)) {
            throw InvalidInputException("INVALID_USERNAME", "This username is reserved")
        }
        if (name.length < MIN_USERNAME || name.length > MAX_USERNAME || !USERNAME.matches(name)) {
            throw InvalidInputException(
                "INVALID_USERNAME",
                "Username must be $MIN_USERNAME-$MAX_USERNAME letters, digits, '.', '_' or '-'"
            )
        }
        return name
    }

    fun email(value: String): String {
        val email = value.trim()
        if (email.length > MAX_EMAIL || !EMAIL.matches(email)) {
            throw InvalidInputException("INVALID_EMAIL", "Not a valid email address")
        }
        return email
    }

    fun password(value: String) {
        if (value.length > MAX_PASSWORD) throw InvalidInputException("TOO_LONG", "Password is longer than $MAX_PASSWORD characters")
    }

    fun locale(value: String): String {
        if (value !in LOCALES) throw InvalidInputException("INVALID_LOCALE", "Unsupported language")
        return value
    }

    fun per100g(value: Float, field: String): Float {
        if (value.isNaN() || value < 0f || value > MAX_PER_100G) {
            throw InvalidInputException("OUT_OF_RANGE", "$field must be between 0 and ${MAX_PER_100G.toInt()}")
        }
        return value
    }

    /**
     * A link the apps may open or load: http(s) with a host. Anything else — `javascript:`,
     * `data:`, `file:` — was accepted before, and the web app passes video links straight to
     * window.open, which runs a `javascript:` link as script on the site.
     */
    fun webLink(value: String, field: String): String {
        val url = value.trim()
        if (url.length > MAX_URL || !isWebLink(url)) {
            throw InvalidInputException("INVALID_URL", "$field must be an http:// or https:// link")
        }
        return url
    }

    /**
     * A photo: a web link, or a file served by this backend, `/uploads/<name>` with a plain file
     * name (what the upload endpoint hands out): no folders, no query, so one file has exactly one
     * URL and photo/PhotoFiles can tell when the last recipe let go of it.
     */
    fun photoLink(value: String): String {
        val url = value.trim()
        if (url.startsWith("/uploads/")) {
            if (UPLOADED_PHOTO.matches(url)) return url
            throw InvalidInputException("INVALID_URL", "Photo link is not valid")
        }
        return webLink(url, "Photo link")
    }

    private val UPLOADED_PHOTO = Regex("^/uploads/[A-Za-z0-9][A-Za-z0-9_-]{0,99}(\\.[A-Za-z0-9]{1,10})?$")

    private fun isWebLink(url: String): Boolean {
        val uri = try { URI(url) } catch (e: Exception) { return false }
        val scheme = uri.scheme?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}

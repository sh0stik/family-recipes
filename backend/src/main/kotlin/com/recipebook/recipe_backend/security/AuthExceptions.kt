package com.recipebook.recipe_backend.security

/**
 * Base for the ordinary, expected failures of sign-up and login — as opposed to a genuine server
 * error. Each carries a stable [code] that [AuthExceptionResolver] puts under the GraphQL error's
 * `extensions.code`, which the client maps to a localized string. The exception [message] is
 * English and exists only as a developer aid in logs; it must never be shown to a user directly.
 */
sealed class AuthException(val code: String, message: String) : RuntimeException(message)

class InvalidInviteCodeException :
    AuthException("INVALID_INVITE_CODE", "Invalid invite code")

class UsernameTakenException(username: String) :
    AuthException("USERNAME_TAKEN", "Username already taken: $username")

class EmailTakenException(email: String) :
    AuthException("EMAIL_TAKEN", "Email already registered: $email")

class WeakPasswordException(minLength: Int) :
    AuthException("WEAK_PASSWORD", "Password must be at least $minLength characters")

/**
 * Covers both "no such user" and "wrong password" for login. Deliberately the same code and
 * message for both — telling them apart would let a caller enumerate which usernames exist, which
 * is exactly what the invite gate and this single code are meant to prevent.
 */
class InvalidCredentialsException :
    AuthException("INVALID_CREDENTIALS", "Invalid username or password")

/**
 * login: the password was right, but an admin has suspended the account. Only ever answered after
 * a correct password, so it tells a stranger nothing about which accounts exist or are suspended.
 */
class AccountSuspendedException :
    AuthException("ACCOUNT_SUSPENDED", "This account has been suspended")

/** deleteAccount and changePassword: the current password given is wrong. Nothing was changed. */
class WrongPasswordException :
    AuthException("WRONG_PASSWORD", "Password is incorrect")

/**
 * deleteAccount: admins (including the family account) cannot delete themselves from the app. The
 * lock is lifted by removing the username from RECIPE_ADMINS on the server — a deliberate step,
 * so the family collection cannot be wiped with one tap.
 */
class AdminCannotDeleteException :
    AuthException("ADMIN_CANNOT_DELETE", "Admin accounts cannot be deleted; remove admin rights first")

package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailService: EmailService,
    private val blacklistedTokenRepository: BlacklistedTokenRepository,
    private val adminService: AdminService,
) {
    /**
     * Shared secret required to register. Set RECIPE_INVITE_CODE in the environment; the app is
     * for one family, so open sign-up only ever meant strangers filling the database.
     *
     * Blank disables the check, which is what local development and the existing tests rely on.
     * Anything internet-facing must set it.
     */
    // Defaulted rather than lateinit: unit tests construct this service directly, where Spring
    // never populates @Value, and a lateinit field would throw on first read.
    @Value("\${recipe.invite-code:}")
    private var inviteCode: String = ""

    /**
     * @param acceptedTerms whether the sign-up form's "I accept the content rules" box was ticked.
     * Apps released before the rules do not send it; their accounts are created without an
     * acceptance and are asked for one the first time they share a recipe.
     */
    fun signUp(
        rawUsername: String,
        password: String,
        rawEmail: String,
        providedInviteCode: String?,
        acceptedTerms: Boolean = false,
    ): String {
        if (inviteCode.isNotBlank() && providedInviteCode?.trim() != inviteCode) {
            throw InvalidInviteCodeException()
        }
        val username = InputValidation.username(rawUsername)
        val email = InputValidation.email(rawEmail)
        validatePasswordStrength(password)
        // An admin name with no account yet is reserved (see AdminService): the first person to
        // register it would otherwise become an admin. Compared ignoring case, so nobody can
        // register "Family" next to "family". Existing names that already differ only by case
        // are left alone.
        if (userRepository.existsByUsernameIgnoreCase(username) || adminService.isAdminUsername(username)) {
            throw UsernameTakenException(username)
        }
        if (userRepository.findUserByEmail(email).isPresent) {
            throw EmailTakenException(email)
        }

        val user = User(
            username = username,
            password = passwordEncoder.encode(password)!!,
            role = "ROLE_USER",
            email = email,
            termsAcceptedAt = if (acceptedTerms) Instant.now() else null,
        )
        userRepository.save(user)

        return jwtService.generateToken(CustomUserDetails(user))
    }

    fun login(username: String, password: String): String {
        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(username, password)
            )
        } catch (e: AuthenticationException) {
            // Deliberately the same exception for "no such user" and "wrong password" — see
            // InvalidCredentialsException.
            throw InvalidCredentialsException()
        }

        val user = userRepository.findByUsername(username)
            .orElseThrow { InvalidCredentialsException() }
        if (user.suspended) throw AccountSuspendedException()

        return jwtService.generateToken(CustomUserDetails(user))
    }

    @Transactional // Why: Ensures all DB changes succeed or fail together
    fun requestPasswordReset(email: String): Boolean {
        val user = userRepository.findUserByEmail(email).orElse(null) ?: return true

        val tokenString = UUID.randomUUID().toString()
        // 1 hour, not 15 minutes: this flow is "find the email, select a UUID out of it, switch
        // to the app, paste it in" rather than "click a link", and it's often done on a phone by
        // a family member abroad. Trades off a slightly longer window during which a leaked
        // token is usable.
        val expiry = Instant.now().plusSeconds(60 * 60)

        // Only a hash is stored: a leaked database must not hand out working reset codes.
        val resetToken = PasswordResetToken(
            token = hashResetCode(tokenString),
            expiryDate = expiry,
            user = user
        )

        passwordResetTokenRepository.save(resetToken)
        emailService.sendPasswordResetEmail(user.email, tokenString, user.preferredLocale)

        return true
    }

    @Transactional
    fun resetPassword(token: String, newPassword: String): Boolean {
        val resetToken = passwordResetTokenRepository.findByToken(hashResetCode(token.trim())).orElse(null)
            ?: return false

        if (resetToken.expiryDate.isBefore(Instant.now())) {
            passwordResetTokenRepository.delete(resetToken)
            return false
        }

        validatePasswordStrength(newPassword)

        val user = resetToken.user
        user.password = passwordEncoder.encode(newPassword)!!
        // Invalidates every token issued before now — a reset is often triggered precisely
        // because someone else may hold a session.
        user.passwordChangedAt = Instant.now()
        userRepository.save(user)

        // Every outstanding code for this account, not just the one used: after a reset, an
        // older email must not be able to reset the password again.
        clearResetCodes(user)

        return true
    }

    /**
     * Changes [user]'s password after checking [currentPassword]. One transaction: the new hash,
     * the session cut-off and the cancelled reset codes are saved together or not at all. Clearing
     * the codes is a bulk delete, which fails outright without a transaction — and used to, after
     * the new password was already saved.
     */
    @Transactional
    fun changePassword(user: User, currentPassword: String, newPassword: String) {
        if (!passwordEncoder.matches(currentPassword, user.password)) {
            // A code the apps can explain, instead of an opaque INTERNAL_ERROR.
            throw WrongPasswordException()
        }
        validatePasswordStrength(newPassword)
        user.password = passwordEncoder.encode(newPassword)!!
        // Invalidates tokens issued before now, so a session someone else may hold stops working.
        // The caller's own token predates this too and will be rejected on its next request —
        // that is intended: the app should send the user back to the login screen.
        user.passwordChangedAt = Instant.now()
        userRepository.save(user)
        clearResetCodes(user)
    }

    /**
     * Cancels every password-reset code issued for [user]. Called whenever the password changes,
     * always inside the caller's transaction: it is a bulk delete and fails without one.
     */
    fun clearResetCodes(user: User) {
        passwordResetTokenRepository.deleteAllByUser(user)
    }

    /**
     * Minimum bar only. Deliberately not a complexity ruleset (upper/lower/digit/symbol), which
     * pushes people toward predictable substitutions; length is the property that actually
     * resists guessing.
     */
    fun validatePasswordStrength(password: String) {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw WeakPasswordException(MIN_PASSWORD_LENGTH)
        }
        InputValidation.password(password)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        /** What is stored for a reset code: its SHA-256, hex. The code itself is 122 random bits. */
        fun hashResetCode(code: String): String =
            MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Transactional
    fun logout(token: String): Boolean {
        if (blacklistedTokenRepository.existsByToken(token)) return true

        // 1. Ask JwtService to decode the token and find its expiration date
        val expirationDate = jwtService.extractExpiration(token)

        // 2. Save both the string and the date to the database
        val blacklistedToken = BlacklistedToken(
            token = token,
            expiryDate = expirationDate.toInstant() // Convert java.util.Date to Instant
        )
        blacklistedTokenRepository.save(blacklistedToken)

        return true
    }
}
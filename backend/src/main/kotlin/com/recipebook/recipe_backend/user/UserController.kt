package com.recipebook.recipe_backend.user

import com.recipebook.recipe_backend.moderation.UserBlockRepository
import com.recipebook.recipe_backend.security.AdminCannotDeleteException
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.AuthService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.InputValidation
import com.recipebook.recipe_backend.security.RateLimitService
import com.recipebook.recipe_backend.security.UsernameTakenException
import com.recipebook.recipe_backend.security.WrongPasswordException
import java.time.Duration
import java.time.Instant
import org.springframework.security.crypto.password.PasswordEncoder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class UserController(
    val userRepository: UserRepository,
    val authService: AuthService,
    val currentUserService: CurrentUserService,
    val httpServletRequest: HttpServletRequest,
    val passwordEncoder: PasswordEncoder,
    val rateLimitService: RateLimitService,
    val adminService: AdminService,
    val accountDeletionService: AccountDeletionService,
    // Null only in unit tests that are not about blocking.
    val blockRepository: UserBlockRepository? = null,
) {
    // The four mutations below are the only ones reachable without a token, so they are the only
    // ones worth brute-forcing or abusing to send mail. Each is rate limited per client IP; login
    // and password reset are additionally keyed by the target so that hammering one account
    // cannot be hidden behind attempts spread across many.

    @MutationMapping
    fun createUser(
        @Argument username: String,
        @Argument email: String,
        @Argument password: String,
        @Argument inviteCode: String?,
        @Argument acceptedTerms: Boolean? = null,
    ): String {
        rateLimitService.check(
            "signup:${rateLimitService.clientIp(httpServletRequest)}",
            limit = 5,
            window = Duration.ofHours(1)
        )
        return authService.signUp(username, password, email, inviteCode, acceptedTerms == true)
    }

    @MutationMapping
    fun login(
        @Argument username: String,
        @Argument password: String
    ): String {
        val ip = rateLimitService.clientIp(httpServletRequest)
        rateLimitService.check("login-ip:$ip", limit = 20, window = Duration.ofMinutes(15))
        // Per username AND address. Keyed on the username alone, anyone could lock a known account
        // (say `family`) out by failing ten logins every 15 minutes, and the real owner — from a
        // different address — was refused even with the right password.
        rateLimitService.check("login-user:$ip:$username", limit = 10, window = Duration.ofMinutes(15))
        return authService.login(username, password)
    }

    @MutationMapping
    fun requestPasswordReset(@Argument email: String): Boolean {
        InputValidation.text(email, InputValidation.MAX_EMAIL, "Email")
        // Tighter than the others: each call sends real mail, so abuse both spams the recipient
        // and risks the sending account being flagged.
        val ip = rateLimitService.clientIp(httpServletRequest)
        rateLimitService.check("reset-ip:$ip", limit = 5, window = Duration.ofHours(1))
        rateLimitService.check("reset-email:$email", limit = 3, window = Duration.ofHours(1))
        return authService.requestPasswordReset(email)
    }

    @MutationMapping
    fun resetPassword(
        @Argument token: String,
        @Argument newPassword: String
    ): Boolean {
        // Reset tokens are 122-bit random UUIDs, but throttling removes any hope of guessing one.
        rateLimitService.check(
            "reset-confirm:${rateLimitService.clientIp(httpServletRequest)}",
            limit = 10,
            window = Duration.ofHours(1)
        )
        return authService.resetPassword(token, newPassword)
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun logout(): Boolean {
        val authHeader = httpServletRequest.getHeader("Authorization")
            ?: throw Exception("Missing Authorization header")
        val token = authHeader.removePrefix("Bearer ").trim()
        return authService.logout(token)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun changePassword(
        @Argument currentPassword: String,
        @Argument newPassword: String,
    ): Boolean {
        val currentUser = currentUserService.getCurrentUser()
        // Someone holding a stolen token could otherwise guess the current password without limit.
        rateLimitService.check("change-password-user:${currentUser.id}", limit = 5, window = Duration.ofMinutes(15))
        // Checks the current password and saves everything in one transaction (AuthService).
        authService.changePassword(currentUser, currentPassword, newPassword)
        return true
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun updateUsername(@Argument("newUsername") rawUsername: String): User {
        val currentUser = currentUserService.getCurrentUser()
        val newUsername = InputValidation.username(rawUsername)

        // Reserved admin names (see AdminService) are refused like taken ones, or renaming would be
        // a way to become an admin. The family account keeps its name: it is how the family
        // collection is found.
        // Ignoring case, but your own name is not "taken": changing only its capitalisation is fine.
        val taken = userRepository.findByUsernameIgnoreCase(newUsername).any { it.id != currentUser.id }
        if (taken || adminService.isAdminUsername(newUsername)) {
            // Reserved names answer the same as taken ones, so the code does not reveal which
            // names are admins'.
            throw UsernameTakenException(newUsername)
        }
        if (currentUser.username == adminService.familyAccount) {
            throw Exception("The family account cannot be renamed")
        }

        currentUser.username = newUsername
        return userRepository.save(currentUser)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun updatePreferredLocale(@Argument locale: String): User {
        val currentUser = currentUserService.getCurrentUser()
        currentUser.preferredLocale = InputValidation.locale(locale)
        return userRepository.save(currentUser)
    }

    /**
     * Records that the caller accepts the content rules. Asked once, the first time someone
     * shares a recipe from an account that has not accepted yet; a repeat keeps the first date.
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun acceptTerms(): User {
        val currentUser = currentUserService.getCurrentUser()
        if (currentUser.termsAcceptedAt != null) return currentUser
        currentUser.termsAcceptedAt = Instant.now()
        return userRepository.save(currentUser)
    }

    // Like email: only on the caller's own record.
    @SchemaMapping(typeName = "User", field = "termsAcceptedAt")
    fun termsAcceptedAt(user: User): String? {
        val me = currentUserService.getAuthenticatedUserOrNull() ?: return null
        return if (me.id == user.id) user.termsAcceptedAt?.toString() else null
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun me(): User {
        return currentUserService.getCurrentUser()
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun user(@Argument id: UUID): User? {
        return userRepository.findById(id).orElse(null)
    }

    // The people who can be made editors of a recipe. The family account is left out: it is the shared
    // collection, not a person, and offering it in the share picker made no sense.
    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun users(): List<User> {
        val me = currentUserService.getCurrentUser()
        // Nor anyone on either side of a block with the caller: they cannot be made editors.
        val blocked = blockRepository?.let { it.findBlockedIds(me.id!!) + it.findBlockerIds(me.id!!) }.orEmpty().toSet()
        // Nor suspended accounts: they cannot be made editors either (shareRecipe answers USER_SUSPENDED).
        return userRepository.findAll().filter { it.username != adminService.familyAccount && it.id !in blocked && !it.suspended }
    }

    // An email is visible only to its owner. A User is reachable from many places — `users`, a
    // recipe's owner, `sharedWith`, note authors — and some of them (public recipes) are open to
    // guests, so the check lives on the field itself rather than on every query returning a User.
    @SchemaMapping(typeName = "User", field = "email")
    fun email(user: User): String? {
        val currentUser = currentUserService.getCurrentUserOrNull() ?: return null
        return if (currentUser.id == user.id) user.email else null
    }

    /**
     * Deletes the caller's account and everything it owns (user/AccountDeletionService). Asks for
     * the password again so an unlocked phone is not enough to wipe an account. Admins are
     * refused: the lock is lifted on the server by removing them from RECIPE_ADMINS.
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun deleteAccount(@Argument password: String): Boolean {
        val currentUser = currentUserService.getCurrentUser()
        val ip = rateLimitService.clientIp(httpServletRequest)
        rateLimitService.check("delete-account-ip:$ip", limit = 20, window = Duration.ofMinutes(15))
        rateLimitService.check("delete-account-user:${currentUser.id}", limit = 5, window = Duration.ofMinutes(15))

        if (adminService.isAdmin(currentUser)) throw AdminCannotDeleteException()
        if (!passwordEncoder.matches(password, currentUser.password)) throw WrongPasswordException()

        accountDeletionService.deleteAccount(currentUser)
        return true
    }

    // True only on the caller's own record, like email: who the admins are is nobody else's business.
    @SchemaMapping(typeName = "User", field = "isAdmin")
    fun isAdmin(user: User): Boolean {
        val me = currentUserService.getAuthenticatedUserOrNull() ?: return false
        return me.id == user.id && adminService.isAdmin(user)
    }
}

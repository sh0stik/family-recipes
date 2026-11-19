package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class CurrentUserService(
    private val userRepository: UserRepository
) {

    fun getCurrentUser(): User {
        authenticatedName() ?: throw Exception("Security Context is missing or unauthenticated")

        return findAuthenticatedUser() ?: throw Exception("Security Context User not found in database")
    }

    /** Returns the authenticated user, or null for anonymous/guest requests. */
    fun getCurrentUserOrNull(): User? {
        authenticatedName() ?: return null
        return findAuthenticatedUser()
    }

    /**
     * A fresh copy of the authenticated account. Looked up by the id of the account the JWT filter
     * loaded, not by its name: the name can change during the request (updateUsername), and the
     * field resolvers that run after the rename must still find this account, never one that has
     * since taken the old name. By name only for a principal that is not a CustomUserDetails.
     */
    private fun findAuthenticatedUser(): User? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        val id = (auth.principal as? CustomUserDetails)?.user?.id
        return if (id != null) userRepository.findById(id).orElse(null)
        else userRepository.findByUsername(auth.name).orElse(null)
    }

    /**
     * The user the JWT filter already loaded for this request, without another database query —
     * for per-item field resolvers (Recipe.canEdit runs once per recipe in a list of hundreds).
     * Null for guests: their principal is Spring's "anonymousUser" string, not a CustomUserDetails.
     */
    fun getAuthenticatedUserOrNull(): User? =
        (SecurityContextHolder.getContext().authentication?.principal as? CustomUserDetails)?.user

    /**
     * The logged-in username, or null for a guest. A guest is not "no authentication": Spring gives
     * them an AnonymousAuthenticationToken named "anonymousUser". Reading `.name` without this
     * check made every guest act as a registered user who happened to be called anonymousUser.
     */
    private fun authenticatedName(): String? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        if (auth is AnonymousAuthenticationToken || !auth.isAuthenticated) return null
        return auth.name
    }
}

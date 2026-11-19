package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.user.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CustomUserDetailsService (
    private val userRepository: UserRepository,
    private val adminService: AdminService,
) : UserDetailsService{
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found with username: $username") }

        return CustomUserDetails(user, adminService.isAdmin(user))
    }

    /**
     * The account with this id, or null when there is none (deleted). How JwtAuthenticationFilter
     * finds the account of a token that carries the id: unlike the username, the id never changes.
     */
    fun loadUserById(id: UUID): CustomUserDetails? =
        userRepository.findById(id).orElse(null)?.let { CustomUserDetails(it, adminService.isAdmin(it)) }
}

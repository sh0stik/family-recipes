package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

@ExtendWith(MockitoExtension::class)
class CurrentUserServiceTest {

    @Mock lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns user when authenticated`() {
        val user = User(id = UUID.randomUUID(), username = "chef", email = "chef@test.com", password = "pass")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("chef", null, emptyList())
        whenever(userRepository.findByUsername("chef")).thenReturn(Optional.of(user))

        val service = CurrentUserService(userRepository)
        val result = service.getCurrentUser()

        assertEquals("chef", result.username)
    }

    @Test
    fun `a guest is nobody, even if an account is called anonymousUser`() {
        SecurityContextHolder.getContext().authentication = AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )
        val service = CurrentUserService(userRepository)

        assertNull(service.getCurrentUserOrNull())
        assertThrows(Exception::class.java) { service.getCurrentUser() }
    }

    @Test
    fun `throws when no authentication in context`() {
        SecurityContextHolder.clearContext()
        val service = CurrentUserService(userRepository)

        assertThrows(Exception::class.java) {
            service.getCurrentUser()
        }
    }

    @Test
    fun `throws when user not found in database`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("ghost", null, emptyList())
        whenever(userRepository.findByUsername("ghost")).thenReturn(Optional.empty())

        val service = CurrentUserService(userRepository)

        assertThrows(Exception::class.java) {
            service.getCurrentUser()
        }
    }

    @Test
    fun `finds the account the filter loaded by id, even after it was renamed`() {
        val id = UUID.randomUUID()
        val loaded = User(id = id, username = "old-name", email = "chef@test.com", password = "pass")
        val renamed = loaded.copy(username = "new-name")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(CustomUserDetails(loaded), null, emptyList())
        whenever(userRepository.findById(id)).thenReturn(Optional.of(renamed))

        val service = CurrentUserService(userRepository)

        assertEquals("new-name", service.getCurrentUser().username)
        assertEquals(id, service.getCurrentUserOrNull()?.id)
    }
}

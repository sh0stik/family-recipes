package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.*

@ExtendWith(MockitoExtension::class)
class CustomUserDetailsServiceTest {

    @Mock lateinit var userRepository: UserRepository

    @Test
    fun `loadUserByUsername returns UserDetails for existing user`() {
        val user = User(username = "chef", email = "chef@test.com", password = "encoded", role = "ROLE_USER")
        whenever(userRepository.findByUsername("chef")).thenReturn(Optional.of(user))

        val service = CustomUserDetailsService(userRepository, AdminService(admins = "family", familyAccount = "family"))
        val details = service.loadUserByUsername("chef")

        assertEquals("chef", details.username)
        assertEquals("encoded", details.password)
        assertTrue(details.authorities.any { it.authority == "ROLE_USER" })
    }

    @Test
    fun `loadUserByUsername throws for missing user`() {
        whenever(userRepository.findByUsername("ghost")).thenReturn(Optional.empty())

        val service = CustomUserDetailsService(userRepository, AdminService(admins = "family", familyAccount = "family"))

        assertThrows(UsernameNotFoundException::class.java) {
            service.loadUserByUsername("ghost")
        }
    }

    @Test
    fun `loadUserById finds the account by id, and null when there is none`() {
        val id = UUID.randomUUID()
        val user = User(id = id, username = "family", email = "family@test.com", password = "encoded")
        whenever(userRepository.findById(id)).thenReturn(Optional.of(user))
        val missing = UUID.randomUUID()
        whenever(userRepository.findById(missing)).thenReturn(Optional.empty())

        val service = CustomUserDetailsService(userRepository, AdminService(admins = "family", familyAccount = "family"))

        val details = service.loadUserById(id)
        assertEquals("family", details?.username)
        assertTrue(details!!.authorities.any { it.authority == "ROLE_ADMIN" })
        assertNull(service.loadUserById(missing))
    }
}

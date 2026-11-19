package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.security.AuthService
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.security.PasswordResetToken
import com.recipebook.recipe_backend.security.PasswordResetTokenRepository
import com.recipebook.recipe_backend.user.AccountDeletionService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * changePassword end to end against the real database. It used to save the new password and then
 * fail on clearing the reset codes (a bulk delete that needs a transaction, and there was none):
 * the app was told INTERNAL_ERROR while the password had in fact changed and every session was
 * cut off. The unit test mocks the repositories and could not see it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangePasswordIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var accountDeletionService: AccountDeletionService

    private val api by lazy { Api(mockMvc) }
    private val name = "change-password-user"

    /** An account with password "oldpassword1" and one outstanding reset code. */
    private fun account(): User {
        val user = userRepository.save(
            User(username = name, email = "$name@test.com", password = passwordEncoder.encode("oldpassword1")!!)
        )
        passwordResetTokenRepository.save(
            PasswordResetToken(
                token = AuthService.hashResetCode(UUID.randomUUID().toString()),
                expiryDate = Instant.now().plusSeconds(3600),
                user = user,
            )
        )
        return user
    }

    private fun resetCodes(user: User): Int =
        jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id = ?", Int::class.java, user.id)!!

    @AfterEach
    fun tearDown() {
        userRepository.findByUsername(name).ifPresent { accountDeletionService.deleteAccount(it) }
    }

    @Test
    fun `the right current password changes it, answers true and cancels reset codes`() {
        val user = account()
        val token = jwtService.generateToken(CustomUserDetails(user))

        val response = api.graphql(
            """mutation { changePassword(currentPassword: "oldpassword1", newPassword: "newpassword2") }""",
            token,
        )

        assertFalse(api.failed(response), response)
        assertTrue(response.contains("\"changePassword\":true"), response)
        val saved = userRepository.findByUsername(name).get()
        assertTrue(passwordEncoder.matches("newpassword2", saved.password))
        assertNotNull(saved.passwordChangedAt)
        assertEquals(0, resetCodes(saved), "outstanding reset codes must be cancelled")
    }

    @Test
    fun `a wrong current password changes nothing`() {
        val user = account()
        val token = jwtService.generateToken(CustomUserDetails(user))

        val response = api.graphql(
            """mutation { changePassword(currentPassword: "not-it-at-all", newPassword: "newpassword2") }""",
            token,
        )

        assertEquals("WRONG_PASSWORD", api.code(response), response)
        val saved = userRepository.findByUsername(name).get()
        assertTrue(passwordEncoder.matches("oldpassword1", saved.password))
        assertNull(saved.passwordChangedAt)
        assertEquals(1, resetCodes(saved))
    }

    @Test
    fun `a too-short new password changes nothing`() {
        val user = account()
        val token = jwtService.generateToken(CustomUserDetails(user))

        val response = api.graphql(
            """mutation { changePassword(currentPassword: "oldpassword1", newPassword: "short") }""",
            token,
        )

        assertEquals("WEAK_PASSWORD", api.code(response), response)
        val saved = userRepository.findByUsername(name).get()
        assertTrue(passwordEncoder.matches("oldpassword1", saved.password))
        assertNull(saved.passwordChangedAt)
        assertEquals(1, resetCodes(saved))
    }
}

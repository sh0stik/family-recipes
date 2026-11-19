package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var passwordEncoder: PasswordEncoder
    @Mock lateinit var jwtService: JwtService
    @Mock lateinit var authenticationManager: AuthenticationManager
    @Mock lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    @Mock lateinit var emailService: EmailService
    @Mock lateinit var blacklistedTokenRepository: BlacklistedTokenRepository

    private lateinit var authService: AuthService

    private val testUser = User(
        id = UUID.randomUUID(),
        username = "testuser",
        email = "test@test.com",
        password = "encoded_pass",
        role = "ROLE_USER"
    )

    @BeforeEach
    fun setUp() {
        authService = AuthService(
            userRepository, passwordEncoder, jwtService,
            authenticationManager, passwordResetTokenRepository,
            emailService, blacklistedTokenRepository,
            AdminService(admins = "family,reserved", familyAccount = "family")
        )
    }

    @Test
    fun `signUp records acceptance of the content rules only when the box was ticked`() {
        whenever(passwordEncoder.encode("password")).thenReturn("encoded_pass")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateToken(any<CustomUserDetails>())).thenReturn("jwt-token")
        val saved = argumentCaptor<User>()

        authService.signUp("ticked", "password", "ticked@test.com", null, acceptedTerms = true)
        // An app released before the rules: the argument is simply absent.
        authService.signUp("oldapp", "password", "oldapp@test.com", null)

        verify(userRepository, times(2)).save(saved.capture())
        assertNotNull(saved.allValues[0].termsAcceptedAt)
        assertNull(saved.allValues[1].termsAcceptedAt)
    }

    @Test
    fun `signUp succeeds with new username`() {
        whenever(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false)
        whenever(passwordEncoder.encode("password")).thenReturn("encoded_pass")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateToken(any<CustomUserDetails>())).thenReturn("jwt-token")

        val token = authService.signUp("newuser", "password", "new@test.com", null)

        assertEquals("jwt-token", token)
        verify(userRepository).save(any<User>())
        verify(jwtService).generateToken(any<CustomUserDetails>())
    }

    @Test
    fun `signUp throws when username is taken`() {
        whenever(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(true)

        val ex = assertThrows(UsernameTakenException::class.java) {
            authService.signUp("testuser", "password", "test@test.com", null)
        }
        assertEquals("USERNAME_TAKEN", ex.code)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `signUp throws when email is already registered`() {
        whenever(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false)
        whenever(userRepository.findUserByEmail("test@test.com")).thenReturn(Optional.of(testUser))

        val ex = assertThrows(EmailTakenException::class.java) {
            authService.signUp("newuser", "password", "test@test.com", null)
        }
        assertEquals("EMAIL_TAKEN", ex.code)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `login returns token on valid credentials`() {
        whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser))
        whenever(jwtService.generateToken(any<CustomUserDetails>())).thenReturn("jwt-token")

        val token = authService.login("testuser", "password")

        assertEquals("jwt-token", token)
        verify(authenticationManager).authenticate(any<UsernamePasswordAuthenticationToken>())
    }

    @Test
    fun `login refuses a suspended account, but only after the right password`() {
        whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser.copy(suspended = true)))

        val ex = assertThrows(AccountSuspendedException::class.java) { authService.login("testuser", "password") }

        assertEquals("ACCOUNT_SUSPENDED", ex.code)
        verify(authenticationManager).authenticate(any<UsernamePasswordAuthenticationToken>())
        verify(jwtService, never()).generateToken(any<CustomUserDetails>())
    }

    @Test
    fun `login throws INVALID_CREDENTIALS when username does not exist`() {
        whenever(authenticationManager.authenticate(any()))
            .thenThrow(UsernameNotFoundException("User not found with username: ghost"))

        val ex = assertThrows(InvalidCredentialsException::class.java) {
            authService.login("ghost", "password")
        }
        assertEquals("INVALID_CREDENTIALS", ex.code)
    }

    @Test
    fun `login throws INVALID_CREDENTIALS when password is wrong`() {
        whenever(authenticationManager.authenticate(any()))
            .thenThrow(BadCredentialsException("Bad credentials"))

        val ex = assertThrows(InvalidCredentialsException::class.java) {
            authService.login("testuser", "wrong-password")
        }
        assertEquals("INVALID_CREDENTIALS", ex.code)
    }

    @Test
    fun `requestPasswordReset returns true even if email not found`() {
        whenever(userRepository.findUserByEmail("unknown@test.com")).thenReturn(Optional.empty())

        assertTrue(authService.requestPasswordReset("unknown@test.com"))
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any())
    }

    @Test
    fun `requestPasswordReset sends email when user exists`() {
        whenever(userRepository.findUserByEmail("test@test.com")).thenReturn(Optional.of(testUser))
        whenever(passwordResetTokenRepository.save(any<PasswordResetToken>())).thenAnswer { it.arguments[0] }

        assertTrue(authService.requestPasswordReset("test@test.com"))
        verify(emailService).sendPasswordResetEmail(eq("test@test.com"), any(), eq("en"))
        verify(passwordResetTokenRepository).save(any<PasswordResetToken>())
    }

    @Test
    fun `requestPasswordReset issues a token good for about an hour`() {
        whenever(userRepository.findUserByEmail("test@test.com")).thenReturn(Optional.of(testUser))
        val saved = argumentCaptor<PasswordResetToken>()
        whenever(passwordResetTokenRepository.save(saved.capture())).thenAnswer { it.arguments[0] }

        val before = Instant.now()
        authService.requestPasswordReset("test@test.com")

        val expiry = saved.firstValue.expiryDate
        val secondsUntilExpiry = expiry.epochSecond - before.epochSecond
        assertTrue(secondsUntilExpiry in 3595..3600, "expected ~3600s, got $secondsUntilExpiry")
    }

    @Test
    fun `requestPasswordReset stores only a hash of the code it emails`() {
        whenever(userRepository.findUserByEmail("test@test.com")).thenReturn(Optional.of(testUser))
        val saved = argumentCaptor<PasswordResetToken>()
        whenever(passwordResetTokenRepository.save(saved.capture())).thenAnswer { it.arguments[0] }
        val emailed = argumentCaptor<String>()

        authService.requestPasswordReset("test@test.com")

        verify(emailService).sendPasswordResetEmail(any(), emailed.capture(), any())
        assertNotEquals(emailed.firstValue, saved.firstValue.token)
        assertEquals(AuthService.hashResetCode(emailed.firstValue), saved.firstValue.token)
    }

    @Test
    fun `requestPasswordReset sends email in the user's preferred locale`() {
        val ruUser = testUser.copy(email = "ru@test.com", preferredLocale = "ru")
        whenever(userRepository.findUserByEmail("ru@test.com")).thenReturn(Optional.of(ruUser))
        whenever(passwordResetTokenRepository.save(any<PasswordResetToken>())).thenAnswer { it.arguments[0] }

        assertTrue(authService.requestPasswordReset("ru@test.com"))
        verify(emailService).sendPasswordResetEmail(eq("ru@test.com"), any(), eq("ru"))
    }

    @Test
    fun `resetPassword returns false for invalid token`() {
        whenever(passwordResetTokenRepository.findByToken(AuthService.hashResetCode("bad-token"))).thenReturn(Optional.empty())

        assertFalse(authService.resetPassword("bad-token", "newpass"))
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `resetPassword returns false for expired token`() {
        val expiredToken = PasswordResetToken(
            token = "expired",
            expiryDate = Instant.now().minusSeconds(600),
            user = testUser
        )
        whenever(passwordResetTokenRepository.findByToken(AuthService.hashResetCode("expired"))).thenReturn(Optional.of(expiredToken))

        assertFalse(authService.resetPassword("expired", "newpass"))
        verify(passwordResetTokenRepository).delete(expiredToken)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `resetPassword succeeds with valid token`() {
        val validToken = PasswordResetToken(
            token = "valid",
            expiryDate = Instant.now().plusSeconds(600),
            user = testUser
        )
        whenever(passwordResetTokenRepository.findByToken(AuthService.hashResetCode("valid"))).thenReturn(Optional.of(validToken))
        whenever(passwordEncoder.encode("newpassword")).thenReturn("encoded_new")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        assertTrue(authService.resetPassword("valid", "newpassword"))
        assertEquals("encoded_new", testUser.password)
        verify(passwordResetTokenRepository).deleteAllByUser(testUser)
    }

    @Test
    fun `resetPassword stamps passwordChangedAt so older tokens stop working`() {
        val validToken = PasswordResetToken(
            token = "valid",
            expiryDate = Instant.now().plusSeconds(600),
            user = testUser
        )
        whenever(passwordResetTokenRepository.findByToken(AuthService.hashResetCode("valid"))).thenReturn(Optional.of(validToken))
        whenever(passwordEncoder.encode("newpassword")).thenReturn("encoded_new")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        val before = Instant.now()
        authService.resetPassword("valid", "newpassword")

        val stamped = testUser.passwordChangedAt
        assertNotNull(stamped, "passwordChangedAt must be set, otherwise existing JWTs stay valid")
        assertFalse(stamped!!.isBefore(before.minusSeconds(1)))
    }

    @Test
    fun `resetPassword rejects a password below the minimum length`() {
        val validToken = PasswordResetToken(
            token = "valid",
            expiryDate = Instant.now().plusSeconds(600),
            user = testUser
        )
        whenever(passwordResetTokenRepository.findByToken(AuthService.hashResetCode("valid"))).thenReturn(Optional.of(validToken))

        val ex = assertThrows(WeakPasswordException::class.java) {
            authService.resetPassword("valid", "short")
        }
        assertEquals("WEAK_PASSWORD", ex.code)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `changePassword saves the new hash, stamps passwordChangedAt and cancels reset codes`() {
        whenever(passwordEncoder.matches("current", "encoded_pass")).thenReturn(true)
        whenever(passwordEncoder.encode("longenough1")).thenReturn("encoded_new")

        authService.changePassword(testUser, "current", "longenough1")

        assertEquals("encoded_new", testUser.password)
        assertNotNull(testUser.passwordChangedAt)
        verify(userRepository).save(testUser)
        verify(passwordResetTokenRepository).deleteAllByUser(testUser)
    }

    @Test
    fun `changePassword with a wrong current password changes nothing`() {
        whenever(passwordEncoder.matches("wrong", "encoded_pass")).thenReturn(false)

        val ex = assertThrows(WrongPasswordException::class.java) {
            authService.changePassword(testUser, "wrong", "longenough1")
        }
        assertEquals("WRONG_PASSWORD", ex.code)
        assertEquals("encoded_pass", testUser.password)
        verify(userRepository, never()).save(any())
        verify(passwordResetTokenRepository, never()).deleteAllByUser(any())
    }

    @Test
    fun `signUp rejects a password below the minimum length`() {
        val ex = assertThrows(WeakPasswordException::class.java) {
            authService.signUp("newuser", "short", "new@test.com", null)
        }
        assertEquals("WEAK_PASSWORD", ex.code)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `signUp requires the invite code when one is configured`() {
        ReflectionTestUtils.setField(authService, "inviteCode", "family-secret")

        val ex = assertThrows(InvalidInviteCodeException::class.java) {
            authService.signUp("newuser", "password", "new@test.com", "wrong-code")
        }
        assertEquals("INVALID_INVITE_CODE", ex.code)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `signUp succeeds when the invite code matches`() {
        ReflectionTestUtils.setField(authService, "inviteCode", "family-secret")
        whenever(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false)
        whenever(passwordEncoder.encode("password")).thenReturn("encoded_pass")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateToken(any<CustomUserDetails>())).thenReturn("jwt-token")

        assertEquals("jwt-token", authService.signUp("newuser", "password", "new@test.com", "family-secret"))
    }

    @Test
    fun `logout blacklists token`() {
        whenever(blacklistedTokenRepository.existsByToken("jwt")).thenReturn(false)
        whenever(jwtService.extractExpiration("jwt")).thenReturn(Date())
        whenever(blacklistedTokenRepository.save(any<BlacklistedToken>())).thenAnswer { it.arguments[0] }

        assertTrue(authService.logout("jwt"))
        verify(blacklistedTokenRepository).save(any<BlacklistedToken>())
    }

    @Test
    fun `logout returns true if token already blacklisted`() {
        whenever(blacklistedTokenRepository.existsByToken("jwt")).thenReturn(true)

        assertTrue(authService.logout("jwt"))
        verify(blacklistedTokenRepository, never()).save(any())
    }

    @Test
    fun `signUp refuses a reserved admin name that has no account yet`() {
        whenever(userRepository.existsByUsernameIgnoreCase("reserved")).thenReturn(false)

        assertThrows(UsernameTakenException::class.java) {
            authService.signUp("reserved", "password123", "r@t.com", null)
        }
        verify(userRepository, never()).save(any<User>())
    }
}

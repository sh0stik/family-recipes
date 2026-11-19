package com.recipebook.recipe_backend.user

import com.recipebook.recipe_backend.security.AdminCannotDeleteException
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.AuthService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.RateLimitService
import com.recipebook.recipe_backend.security.WrongPasswordException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class UserControllerTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var authService: AuthService
    @Mock lateinit var currentUserService: CurrentUserService
    @Mock lateinit var httpServletRequest: HttpServletRequest
    @Mock lateinit var passwordEncoder: PasswordEncoder
    @Mock lateinit var rateLimitService: RateLimitService
    @Mock lateinit var accountDeletionService: AccountDeletionService

    // Real, not mocked: it is plain configuration. "admin" is an admin; "reserved" is listed but
    // has no account.
    private val adminService = AdminService(admins = "family, admin, reserved", familyAccount = "family")

    private lateinit var controller: UserController

    @BeforeEach
    fun setUp() {
        controller = UserController(
            userRepository,
            authService,
            currentUserService,
            httpServletRequest,
            passwordEncoder,
            rateLimitService,
            adminService,
            accountDeletionService,
        )
    }

    @Test
    fun `createUser delegates to authService signUp`() {
        whenever(authService.signUp("user", "pass", "e@t.com", null)).thenReturn("jwt-token")

        val result = controller.createUser("user", "e@t.com", "pass", null)

        assertEquals("jwt-token", result)
    }

    @Test
    fun `login delegates to authService`() {
        whenever(authService.login("user", "pass")).thenReturn("jwt-token")

        assertEquals("jwt-token", controller.login("user", "pass"))
    }

    @Test
    fun `logout extracts token from Authorization header`() {
        whenever(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer my-jwt-token")
        whenever(authService.logout("my-jwt-token")).thenReturn(true)

        assertTrue(controller.logout())
        verify(authService).logout("my-jwt-token")
    }

    @Test
    fun `logout throws when no Authorization header`() {
        whenever(httpServletRequest.getHeader("Authorization")).thenReturn(null)

        assertThrows(Exception::class.java) {
            controller.logout()
        }
    }

    @Test
    fun `createUser passes the content-rules box on to sign-up`() {
        whenever(authService.signUp("user", "pass", "e@t.com", null, true)).thenReturn("accepted-token")

        assertEquals("accepted-token", controller.createUser("user", "e@t.com", "pass", null, true))
    }

    @Test
    fun `acceptTerms records the date once and keeps it`() {
        val user = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "p")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        val first = controller.acceptTerms().termsAcceptedAt
        assertNotNull(first)
        assertEquals(first, controller.acceptTerms().termsAcceptedAt)
        verify(userRepository, times(1)).save(any<User>())
    }

    @Test
    fun `termsAcceptedAt is visible only on your own record`() {
        val me = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "p", termsAcceptedAt = java.time.Instant.now())
        val other = User(id = UUID.randomUUID(), username = "other", email = "o@t.com", password = "p", termsAcceptedAt = java.time.Instant.now())

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(me)
        assertNotNull(controller.termsAcceptedAt(me))
        assertNull(controller.termsAcceptedAt(other))

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(null)
        assertNull(controller.termsAcceptedAt(me))
    }

    @Test
    fun `updateUsername succeeds when name is available`() {
        val user = User(id = UUID.randomUUID(), username = "old", email = "e@t.com", password = "p")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(userRepository.findByUsernameIgnoreCase("newname")).thenReturn(emptyList())
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        val result = controller.updateUsername("newname")

        assertEquals("newname", result.username)
    }

    @Test
    fun `updateUsername throws when name is taken`() {
        val user = User(id = UUID.randomUUID(), username = "old", email = "e@t.com", password = "p")
        val existing = User(id = UUID.randomUUID(), username = "taken", email = "x@t.com", password = "p")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(userRepository.findByUsernameIgnoreCase("taken")).thenReturn(listOf(existing))

        val refused = assertThrows(com.recipebook.recipe_backend.security.UsernameTakenException::class.java) {
            controller.updateUsername("taken")
        }
        assertEquals("USERNAME_TAKEN", refused.code)
    }

    @Test
    fun `user returns user by id`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, username = "chef", email = "c@t.com", password = "p")
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

        val result = controller.user(userId)

        assertNotNull(result)
        assertEquals("chef", result!!.username)
    }

    @Test
    fun `user returns null for missing id`() {
        whenever(userRepository.findById(any<UUID>())).thenReturn(Optional.empty())

        assertNull(controller.user(UUID.randomUUID()))
    }

    @Test
    fun `users returns everyone except the family account`() {
        val users = listOf(
            User(username = "a", email = "a@t.com", password = "p"),
            User(username = "b", email = "b@t.com", password = "p"),
            User(username = "family", email = "f@t.com", password = "p"),
        )
        whenever(userRepository.findAll()).thenReturn(users)

        assertEquals(listOf("a", "b"), controller.users().map { it.username })
    }

    @Test
    fun `email is visible to its owner`() {
        val me = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "p")
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(me)

        assertEquals("me@t.com", controller.email(me))
    }

    @Test
    fun `email of another user is hidden`() {
        val me = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "p")
        val other = User(id = UUID.randomUUID(), username = "other", email = "other@t.com", password = "p")
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(me)

        assertNull(controller.email(other))
    }

    @Test
    fun `email is hidden from guests`() {
        val other = User(id = UUID.randomUUID(), username = "other", email = "other@t.com", password = "p")
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(null)

        assertNull(controller.email(other))
    }

    @Test
    fun `updateUsername refuses a reserved admin name`() {
        val user = User(id = UUID.randomUUID(), username = "old", email = "e@t.com", password = "p")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(userRepository.findByUsernameIgnoreCase("reserved")).thenReturn(emptyList())

        val refused = assertThrows(com.recipebook.recipe_backend.security.UsernameTakenException::class.java) { controller.updateUsername("reserved") }
        assertEquals("USERNAME_TAKEN", refused.code)
        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun `the family account cannot be renamed`() {
        val family = User(id = UUID.randomUUID(), username = "family", email = "f@t.com", password = "p")
        whenever(currentUserService.getCurrentUser()).thenReturn(family)
        whenever(userRepository.findByUsernameIgnoreCase("newname")).thenReturn(emptyList())

        assertThrows(Exception::class.java) { controller.updateUsername("newname") }
        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun `deleteAccount deletes with the right password`() {
        val user = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "hash")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(rateLimitService.clientIp(httpServletRequest)).thenReturn("1.2.3.4")
        whenever(passwordEncoder.matches("secret", "hash")).thenReturn(true)

        assertTrue(controller.deleteAccount("secret"))
        verify(accountDeletionService).deleteAccount(user)
    }

    @Test
    fun `deleteAccount with a wrong password deletes nothing`() {
        val user = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "hash")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(rateLimitService.clientIp(httpServletRequest)).thenReturn("1.2.3.4")
        whenever(passwordEncoder.matches("wrong", "hash")).thenReturn(false)

        assertThrows(WrongPasswordException::class.java) { controller.deleteAccount("wrong") }
        verify(accountDeletionService, never()).deleteAccount(any())
    }

    @Test
    fun `deleteAccount refuses admins and the family account`() {
        for (name in listOf("admin", "family")) {
            val user = User(id = UUID.randomUUID(), username = name, email = "$name@t.com", password = "hash")
            whenever(currentUserService.getCurrentUser()).thenReturn(user)
            whenever(rateLimitService.clientIp(httpServletRequest)).thenReturn("1.2.3.4")

            assertThrows(AdminCannotDeleteException::class.java) { controller.deleteAccount("secret") }
        }
        verify(accountDeletionService, never()).deleteAccount(any())
    }

    @Test
    fun `deleteAccount is rate limited per user`() {
        val user = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "hash")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(rateLimitService.clientIp(httpServletRequest)).thenReturn("1.2.3.4")
        whenever(passwordEncoder.matches(any(), any())).thenReturn(false)

        assertThrows(WrongPasswordException::class.java) { controller.deleteAccount("wrong") }
        verify(rateLimitService).check(eq("delete-account-user:${user.id}"), eq(5), any())
        verify(rateLimitService).check(eq("delete-account-ip:1.2.3.4"), eq(20), any())
    }

    @Test
    fun `login is limited per username and address, so another address can still log in`() {
        whenever(rateLimitService.clientIp(httpServletRequest)).thenReturn("1.2.3.4")
        whenever(authService.login("family", "pw")).thenReturn("jwt")

        controller.login("family", "pw")
        verify(rateLimitService).check(eq("login-ip:1.2.3.4"), eq(20), any())
        verify(rateLimitService).check(eq("login-user:1.2.3.4:family"), eq(10), any())
    }

    @Test
    fun `changePassword hands the whole change to the transactional AuthService method`() {
        val user = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "hash")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)

        assertTrue(controller.changePassword("current", "longenough1"))
        verify(authService).changePassword(user, "current", "longenough1")
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `changePassword is rate limited per user`() {
        val user = User(id = UUID.randomUUID(), username = "me", email = "me@t.com", password = "hash")
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(authService.changePassword(any(), any(), any())).thenThrow(WrongPasswordException())

        val refused = assertThrows(WrongPasswordException::class.java) { controller.changePassword("wrong", "longenough1") }
        assertEquals("WRONG_PASSWORD", refused.code)
        verify(rateLimitService).check(eq("change-password-user:${user.id}"), eq(5), any())
    }

    @Test
    fun `isAdmin is true only on an admin's own record`() {
        val admin = User(id = UUID.randomUUID(), username = "admin", email = "a@t.com", password = "p")
        val other = User(id = UUID.randomUUID(), username = "other", email = "o@t.com", password = "p")
        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(admin)

        assertTrue(controller.isAdmin(admin))
        assertFalse(controller.isAdmin(other))
    }

    @Test
    fun `isAdmin hides another admin's status`() {
        val admin = User(id = UUID.randomUUID(), username = "admin", email = "a@t.com", password = "p")
        val me = User(id = UUID.randomUUID(), username = "me", email = "m@t.com", password = "p")
        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(me)

        assertFalse(controller.isAdmin(admin))
        assertFalse(controller.isAdmin(me))
    }

    @Test
    fun `isAdmin is false for guests`() {
        val admin = User(id = UUID.randomUUID(), username = "admin", email = "a@t.com", password = "p")
        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(null)

        assertFalse(controller.isAdmin(admin))
    }
}

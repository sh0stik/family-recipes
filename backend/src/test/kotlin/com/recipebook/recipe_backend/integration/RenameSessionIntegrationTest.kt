package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.security.BlacklistedTokenRepository
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.AccountDeletionService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renaming an account keeps the device signed in: tokens name the account by its id, which a
 * rename does not change. Everything else a token is checked against must still hold after a
 * rename — logout, password change, suspension, expiry, account deletion — and a token must never
 * reach another account that takes the old name, in either direction. Tokens from before the id
 * claim existed name the account only by username and behave as they always did.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RenameSessionIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var blacklistedTokenRepository: BlacklistedTokenRepository
    @Autowired lateinit var accountDeletionService: AccountDeletionService

    @Value("\${jwt.secret}")
    lateinit var secret: String

    private val api by lazy { Api(mockMvc) }
    private val accounts = mutableListOf<UUID>()

    /** Deleted by id: most of these accounts no longer have the name they were made with. */
    @AfterEach
    fun tearDown() {
        accounts.forEach { id -> userRepository.findById(id).ifPresent { accountDeletionService.deleteAccount(it) } }
        accounts.clear()
    }

    private fun newUser(name: String): User =
        userRepository.save(
            User(username = name, email = "$name-${System.nanoTime()}@test.com", password = passwordEncoder.encode(PASSWORD)!!)
        ).also { accounts += it.id!! }

    private fun token(user: User): String = jwtService.generateToken(CustomUserDetails(user))

    private fun key() = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))

    /** A token in the format issued before 2026-11: no account id, no token id. */
    private fun oldFormatToken(username: String, issuedAt: Date = Date()): String =
        Jwts.builder()
            .setSubject(username)
            .setIssuedAt(issuedAt)
            .setExpiration(Date(issuedAt.time + 30L * 24 * 60 * 60 * 1000))
            .signWith(key(), SignatureAlgorithm.HS256)
            .compact()

    private fun rename(newName: String, token: String): String =
        api.graphql("""mutation { updateUsername(newUsername: "$newName") { username email } }""", token)

    /** `{ me { username } }` with [token]: the HTTP status and the body. */
    private fun me(token: String): Pair<Int, String> {
        val query = "{ me { username } }"
        return api.status(query, token) to api.graphql(query, token)
    }

    private fun assertSignedInAs(name: String, token: String) {
        val (status, body) = me(token)
        assertEquals(200, status, body)
        assertTrue(body.contains("\"me\":{\"username\":\"$name\"}"), body)
    }

    /** Answered, but as a guest: the request runs and `me`, which needs an account, fails. */
    private fun assertGuest(token: String) {
        val (status, body) = me(token)
        assertEquals(200, status, body)
        assertTrue(api.failed(body), body)
        assertFalse(body.contains("\"username\""), body)
    }

    /** createUser through the API, from its own address so the per-address sign-up limit is not shared. */
    private fun signUp(name: String): String {
        val response = mockMvc.perform(
            post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .header("CF-Connecting-IP", "rename-test-${UUID.randomUUID()}")
                .content(
                    """{"query":"mutation { createUser(username: \"$name\", email: \"$name-${System.nanoTime()}@test.com\", password: \"$PASSWORD\") }"}"""
                )
        ).andReturn().response.getContentAsString(Charsets.UTF_8)
        assertFalse(api.failed(response), response)
        userRepository.findByUsername(name).ifPresent { accounts += it.id!! }
        return response.substringAfter("\"createUser\":\"").substringBefore("\"")
    }

    @Test
    fun `after a rename the same token still works and me returns the new name`() {
        val user = newUser("rename-keeps-session")
        val token = token(user)

        val response = rename("rename-keeps-session-2", token)
        assertFalse(api.failed(response), response)
        assertTrue(response.contains("\"username\":\"rename-keeps-session-2\""), response)
        // The rename's own answer still knows whose account it is (email is shown only to its owner).
        assertTrue(response.contains("\"email\":\"rename-keeps-session-"), response)

        assertSignedInAs("rename-keeps-session-2", token)
        // And renaming again with it works too.
        assertFalse(api.failed(rename("rename-keeps-session-3", token)))
        assertSignedInAs("rename-keeps-session-3", token)
    }

    @Test
    fun `logout after a rename blacklists the token and it stops working`() {
        val user = newUser("rename-then-logout")
        val token = token(user)
        val otherSession = token(user)
        rename("rename-then-logout-2", token)

        val response = api.graphql("mutation { logout }", token)
        assertTrue(response.contains("\"logout\":true"), response)
        assertTrue(blacklistedTokenRepository.existsByToken(token))
        assertEquals(401, me(token).first)
        // Only that session: the other device stays signed in.
        assertSignedInAs("rename-then-logout-2", otherSession)
    }

    @Test
    fun `a password change after a rename still revokes the tokens issued before it`() {
        val user = newUser("rename-then-password")
        val token = token(user)
        val otherSession = token(user)
        rename("rename-then-password-2", token)
        // Tokens have whole-second issue times and the check allows a second of slack.
        Thread.sleep(2100)

        val response = api.graphql(
            """mutation { changePassword(currentPassword: "$PASSWORD", newPassword: "newpassword2") }""",
            token,
        )
        assertTrue(response.contains("\"changePassword\":true"), response)

        assertEquals(401, me(token).first)
        assertEquals(401, me(otherSession).first)
        val login = api.graphql("""mutation { login(username: "rename-then-password-2", password: "newpassword2") }""")
        assertFalse(api.failed(login), login)
        assertSignedInAs("rename-then-password-2", login.substringAfter("\"login\":\"").substringBefore("\""))
    }

    @Test
    fun `a new account with the old name and the renamed account never share a token`() {
        val renamed = newUser("rename-name-reused")
        val renamedToken = token(renamed)
        rename("rename-name-reused-2", renamedToken)

        val newcomerToken = signUp("rename-name-reused")
        val newcomerId = userRepository.findByUsername("rename-name-reused").get().id
        assertTrue(newcomerId != renamed.id)

        // The old token, whose username is now the newcomer's, still opens only the renamed account.
        assertSignedInAs("rename-name-reused-2", renamedToken)
        // And the newcomer's token opens only theirs.
        assertSignedInAs("rename-name-reused", newcomerToken)

        // A recipe made with the old token belongs to the renamed account, not to the newcomer.
        val recipe = api.graphql("""mutation { createRecipe(name: "Rename soup") { id user { username } } }""", renamedToken)
        assertTrue(recipe.contains("\"username\":\"rename-name-reused-2\""), recipe)
    }

    @Test
    fun `a renamed account that is suspended is refused`() {
        val user = newUser("rename-then-suspend")
        val token = token(user)
        rename("rename-then-suspend-2", token)

        val saved = userRepository.findById(user.id!!).get()
        saved.suspended = true
        userRepository.save(saved)

        assertEquals(401, me(token).first)
    }

    @Test
    fun `an expired token with an account id is not accepted`() {
        val user = newUser("rename-expired")
        val issuedAt = Date(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000)
        val expired = Jwts.builder()
            .claim(JwtService.USER_ID_CLAIM, user.id.toString())
            .setId(UUID.randomUUID().toString())
            .setSubject(user.username)
            .setIssuedAt(issuedAt)
            .setExpiration(Date(issuedAt.time + 30L * 24 * 60 * 60 * 1000))
            .signWith(key(), SignatureAlgorithm.HS256)
            .compact()

        assertGuest(expired)
    }

    @Test
    fun `a renamed and then deleted account's token opens nothing, not even the old name's new owner`() {
        val user = newUser("rename-deleted")
        val token = token(user)
        rename("rename-deleted-2", token)
        accountDeletionService.deleteAccount(userRepository.findById(user.id!!).get())
        assertGuest(token)

        signUp("rename-deleted")
        signUp("rename-deleted-2")
        assertGuest(token)
    }

    @Test
    fun `deleting a renamed account removes the tokens it logged out of under its old name`() {
        val user = newUser("rename-then-delete")
        val loggedOut = token(user)
        val current = token(user)
        api.graphql("mutation { logout }", loggedOut)
        assertTrue(blacklistedTokenRepository.existsByToken(loggedOut))
        rename("rename-then-delete-2", current)

        val response = api.graphql("""mutation { deleteAccount(password: "$PASSWORD") }""", current)
        assertTrue(response.contains("\"deleteAccount\":true"), response)

        assertFalse(blacklistedTokenRepository.existsByToken(loggedOut))
    }

    @Test
    fun `a token without an account id behaves as before a rename and after it`() {
        val user = newUser("rename-old-format")
        Thread.sleep(1100)
        val oldFormat = oldFormatToken("rename-old-format")
        assertSignedInAs("rename-old-format", oldFormat)

        // It names the account only by its old username, which no account has now: a guest.
        rename("rename-old-format-2", token(user))
        assertGuest(oldFormat)

        // When someone registers the old name, the token predates their account and is refused.
        Thread.sleep(1100)
        signUp("rename-old-format")
        assertEquals(401, me(oldFormat).first)
    }

    private companion object {
        const val PASSWORD = "password123"
    }
}

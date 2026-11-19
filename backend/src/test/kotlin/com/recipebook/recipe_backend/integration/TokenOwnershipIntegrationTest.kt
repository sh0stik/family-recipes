package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A token must only ever open the account it was issued to. Before tokens carried the account id,
 * a username freed by account deletion or renaming could be registered by someone else, and the
 * previous owner's token (valid for 30 days) logged straight into the new account. Tokens with the
 * id find their account by it, so they also survive a rename (see RenameSessionIntegrationTest).
 *
 * Tokens issued before the id claim existed must keep working for their real owner, so they are
 * judged by age instead: a token older than the account cannot belong to it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenOwnershipIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var jwtService: JwtService

    @Value("\${jwt.secret}")
    lateinit var secret: String

    private fun newUser(name: String): User =
        userRepository.save(User(username = name, email = "$name-${System.nanoTime()}@test.com", password = "x"))

    /** A token in the format issued before 2026-11: no account id, no token id. */
    private fun oldFormatToken(username: String, issuedAt: Date): String =
        Jwts.builder()
            .setSubject(username)
            .setIssuedAt(issuedAt)
            .setExpiration(Date(issuedAt.time + 30L * 24 * 60 * 60 * 1000))
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), SignatureAlgorithm.HS256)
            .compact()

    /** Status of `{ me { username } }` with [token], and the body. */
    private fun me(token: String): Pair<Int, String> {
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"{ me { username } }"}""")
                .header("Authorization", "Bearer $token")
        ).andReturn()
        return result.response.status to result.response.contentAsString
    }

    @Test
    fun `new token does not open the account that re-registers its username after deletion`() {
        val first = newUser("token-reuse-deleted")
        val token = jwtService.generateToken(CustomUserDetails(first))
        assertEquals(200, me(token).first)

        userRepository.delete(first)
        newUser("token-reuse-deleted")

        // Its account is gone, so the request is a guest's, as for any deleted account's token.
        val (status, body) = me(token)
        assertEquals(200, status)
        assertTrue(body.contains("\"errors\"") && !body.contains("\"username\""), body)
    }

    @Test
    fun `new token follows its account through a rename, not the old username`() {
        val renamed = newUser("token-reuse-renamed")
        val token = jwtService.generateToken(CustomUserDetails(renamed))

        renamed.username = "token-reuse-renamed-2"
        userRepository.save(renamed)
        newUser("token-reuse-renamed")

        val (status, body) = me(token)
        assertEquals(200, status)
        assertTrue(body.contains("\"username\":\"token-reuse-renamed-2\""), body)
    }

    @Test
    fun `old-format token issued before the account existed is refused`() {
        // Same moment as the delete/re-register and rename cases with a pre-upgrade token: the
        // token was issued to whoever held the name earlier, then the current account was created.
        val issuedToPreviousOwner = Date(System.currentTimeMillis() - 60_000)
        newUser("token-old-format-reused")

        assertEquals(401, me(oldFormatToken("token-old-format-reused", issuedToPreviousOwner)).first)
    }

    @Test
    fun `old-format token issued after the account was created keeps working`() {
        newUser("token-old-format-owner")
        Thread.sleep(1100)

        val (status, body) = me(oldFormatToken("token-old-format-owner", Date()))
        assertEquals(200, status)
        assertTrue(body.contains("token-old-format-owner"), body)
    }

    @Test
    fun `old-format token issued in the same second as the account is accepted`() {
        // Sign-up creates the account and issues its token within the same second, and iat has no
        // milliseconds, so this must not be mistaken for an older token.
        val user = newUser("token-old-format-same-second")
        val sameSecond = Date(user.createdAt!!.toEpochMilli() / 1000 * 1000)

        assertEquals(200, me(oldFormatToken("token-old-format-same-second", sameSecond)).first)
    }

    @Test
    fun `two logins in the same second get different tokens`() {
        val user = newUser("token-same-second")
        val first = jwtService.generateToken(CustomUserDetails(user))
        val second = jwtService.generateToken(CustomUserDetails(user))
        assertNotEquals(first, second)

        // Logging out one session must leave the other working.
        mockMvc.perform(
            post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"mutation { logout }"}""")
                .header("Authorization", "Bearer $first")
        )
        assertEquals(401, me(first).first)
        assertEquals(200, me(second).first)
    }
}

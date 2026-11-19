package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Covers the bug where every sign-up/login failure — invalid invite code, duplicate username,
 * duplicate email, a too-short password, even a plain wrong password — surfaced to the client as
 * an opaque `INTERNAL_ERROR for <uuid>`, because `AuthService` threw bare `java.lang.Exception`
 * and Spring GraphQL treats any unclassified exception as a server crash. `AuthException` +
 * `AuthExceptionResolver` now classify these as `BAD_REQUEST` and attach a stable
 * `extensions.code` the client can map to a localized string.
 *
 * Asserts on `extensions.code` only, never on the English message, since that text is a developer
 * aid and is not what should reach a user.
 *
 * Uses its own `recipe.invite-code` so it does not share the Spring context (and therefore the
 * in-memory RateLimitService counters) with test classes that run under the default blank code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["recipe.invite-code=test-invite-code"])
class AuthErrorIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val validInvite = "test-invite-code"

    private fun buildBody(query: String): String {
        val escaped = query.replace("\"", "\\\"").replace("\n", " ")
        return """{"query":"$escaped"}"""
    }

    private fun graphql(query: String): String {
        val request = post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildBody(query))
        return mockMvc.perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString
    }

    @Test
    fun `sign up with wrong invite code returns INVALID_INVITE_CODE`() {
        val response = graphql(
            """mutation { createUser(username: "nobody1", email: "nobody1@test.com", password: "password123", inviteCode: "wrong-code") }"""
        )
        assertHasErrorCode(response, "INVALID_INVITE_CODE")
    }

    @Test
    fun `sign up with a too-short password returns WEAK_PASSWORD`() {
        val response = graphql(
            """mutation { createUser(username: "nobody2", email: "nobody2@test.com", password: "short", inviteCode: "$validInvite") }"""
        )
        assertHasErrorCode(response, "WEAK_PASSWORD")
    }

    @Test
    fun `sign up with a username already in use returns USERNAME_TAKEN`() {
        val response = graphql(
            """mutation { createUser(username: "family", email: "not-family@test.com", password: "password123", inviteCode: "$validInvite") }"""
        )
        assertHasErrorCode(response, "USERNAME_TAKEN")
    }

    @Test
    fun `sign up with an email already registered returns EMAIL_TAKEN`() {
        val response = graphql(
            """mutation { createUser(username: "not-family", email: "family@recipes.local", password: "password123", inviteCode: "$validInvite") }"""
        )
        assertHasErrorCode(response, "EMAIL_TAKEN")
    }

    @Test
    fun `login with an unknown username returns INVALID_CREDENTIALS`() {
        val response = graphql("""mutation { login(username: "no-such-user", password: "whatever123") }""")
        assertHasErrorCode(response, "INVALID_CREDENTIALS")
    }

    @Test
    fun `login with a wrong password returns INVALID_CREDENTIALS`() {
        val response = graphql("""mutation { login(username: "family", password: "wrong-password") }""")
        assertHasErrorCode(response, "INVALID_CREDENTIALS")
    }

    private fun assertHasErrorCode(response: String, code: String) {
        org.junit.jupiter.api.Assertions.assertTrue(
            response.contains(""""code":"$code""""),
            "Expected extensions.code=$code, got: $response"
        )
        org.junit.jupiter.api.Assertions.assertFalse(
            response.contains("INTERNAL_ERROR"),
            "Must not be classified INTERNAL_ERROR: $response"
        )
    }
}

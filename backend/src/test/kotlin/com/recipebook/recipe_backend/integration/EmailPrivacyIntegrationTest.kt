package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the leak where `User.email` was returned to anyone who asked for it: every account's
 * email to any logged-in user via `users`, and the owner's email of every public recipe to
 * unauthenticated guests via `publicRecipes { user { email } }`. Email is now resolved only for
 * the account's owner and is null everywhere else.
 *
 * Registers a second user, so it uses its own `recipe.invite-code` to get a separate Spring
 * context — the extra account would otherwise leak into the shared context's seed data, and the
 * signup would count against the shared in-memory rate limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["recipe.invite-code=email-privacy-invite"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailPrivacyIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    lateinit var familyToken: String
    lateinit var otherToken: String

    @BeforeAll
    fun setUp() {
        familyToken = graphql("""mutation { login(username: "family", password: "password") }""")
            .substringAfter("\"login\":\"").substringBefore("\"")
        otherToken = graphql(
            """mutation { createUser(username: "privacy-other", email: "privacy-other@test.com", password: "password123", inviteCode: "email-privacy-invite") }"""
        ).substringAfter("\"createUser\":\"").substringBefore("\"")
    }

    private fun buildBody(query: String): String {
        val escaped = query.replace("\"", "\\\"").replace("\n", " ")
        return """{"query":"$escaped"}"""
    }

    private fun graphql(query: String, authToken: String? = null): String {
        val request = post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildBody(query))
        if (authToken != null) {
            request.header("Authorization", "Bearer $authToken")
        }
        return mockMvc.perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString
    }

    @Test
    fun `owner still sees their own email through me`() {
        val response = graphql("query { me { email } }", familyToken)
        assertTrue(response.contains("\"email\":\"family@recipes.local\""), response)
    }

    @Test
    fun `users does not reveal other accounts' emails`() {
        // Asked as family, which `users` itself leaves out, so the other account's row is someone
        // else's — its email must come back null.
        val response = graphql("query { users { username email } }", familyToken)
        assertFalse(response.contains("\"errors\""), response)
        assertTrue(response.contains("\"username\":\"privacy-other\",\"email\":null"), response)
        assertFalse(response.contains("privacy-other@test.com"), response)
    }

    @Test
    fun `users keeps the caller's own email`() {
        val response = graphql("query { users { username email } }", otherToken)
        assertFalse(response.contains("\"errors\""), response)
        assertTrue(response.contains("\"email\":\"privacy-other@test.com\""), response)
    }

    @Test
    fun `users leaves out the family account`() {
        val response = graphql("query { users { username } }", otherToken)
        assertFalse(response.contains("\"errors\""), response)
        assertFalse(response.contains("\"username\":\"family\""), response)
    }

    @Test
    fun `guest cannot read recipe owners' emails from public recipes`() {
        val response = graphql("query { publicRecipes { user { email } } }")
        assertFalse(response.contains("\"errors\""), response)
        assertTrue(response.contains("\"user\":{\"email\":null}"), response)
        assertFalse(response.contains("@"), response)
    }

    @Test
    fun `logged-in user cannot read another owner's email from public recipes`() {
        val response = graphql("query { publicRecipes { user { email } } }", otherToken)
        assertFalse(response.contains("\"errors\""), response)
        assertFalse(response.contains("family@recipes.local"), response)
    }
}

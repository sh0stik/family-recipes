package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit findings S-03 and S-08 through the GraphQL endpoint: what the server used to store without
 * question, and must now refuse with a client error code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputLimitsIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var jwtService: JwtService

    private var address = 0

    private fun token(name: String): String {
        val user = userRepository.save(User(username = name, email = "$name-${System.nanoTime()}@test.com", password = "x"))
        return jwtService.generateToken(CustomUserDetails(user))
    }

    /** Sends [variables] separately, so large values need no escaping. */
    private fun graphql(query: String, token: String? = null, variables: String = "{}"): String {
        val escaped = query.replace("\"", "\\\"").replace("\n", " ")
        val request = post("/graphql").contentType(MediaType.APPLICATION_JSON)
            .content("""{"query":"$escaped","variables":$variables}""")
            // A fresh address per request, so sign-up attempts here never hit the rate limiter.
            .header("CF-Connecting-IP", "192.0.2.${++address}")
        if (token != null) request.header("Authorization", "Bearer $token")
        return mockMvc.perform(request).andReturn().response.contentAsString
    }

    private fun assertRefused(body: String, code: String) =
        assertTrue(body.contains("\"code\":\"$code\""), "expected $code in $body")

    @Test
    fun `script and file links are refused`() {
        val t = token("limits-links")
        val id = graphql("""mutation { createRecipe(name: "links") { id } }""", t).substringAfter("\"id\":\"").substringBefore("\"")
        try {
            assertRefused(graphql("""mutation { addVideoLinkToRecipe(recipeId: "$id", url: "javascript:alert(document.domain)") { id } }""", t), "INVALID_URL")
            assertRefused(graphql("""mutation { addPhotoToRecipe(recipeId: "$id", url: "file:///etc/passwd") { id } }""", t), "INVALID_URL")
            assertFalse(graphql("""mutation { addVideoLinkToRecipe(recipeId: "$id", url: "https://youtu.be/x") { id } }""", t).contains("errors"))
        } finally {
            graphql("""mutation { moveToTrash(recipeId: "$id") { id } }""", t)
            graphql("""mutation { deletePermanently(recipeId: "$id") }""", t)
        }
    }

    @Test
    fun `a 15 MB description is refused`() {
        val t = token("limits-big")
        val body = graphql(
            """mutation(${'$'}d: String) { createRecipe(name: "big", description: ${'$'}d) { id } }""", t,
            """{"d":"${"z".repeat(15_000_000)}"}"""
        )
        assertRefused(body, "TOO_LONG")
    }

    @Test
    fun `out-of-range ingredient values are refused`() {
        val body = graphql(
            """mutation { createIngredient(name: "evil", caloriesPer100g: -5, proteinPer100g: 1, fatPer100g: 0, carbsPer100g: 0) { id } }""",
            token("limits-ingredient")
        )
        assertRefused(body, "OUT_OF_RANGE")
    }

    @Test
    fun `look-alike and malformed usernames are refused on rename`() {
        val t = token("limits-rename")
        for (bad in listOf("", "   ", "Family", "family ", "<script>alert(1)</script>")) {
            val body = graphql("""mutation(${'$'}n: String!) { updateUsername(newUsername: ${'$'}n) { username } }""", t, """{"n":"$bad"}""")
            assertTrue(body.contains("errors") && !body.contains("\"username\":"), "accepted '$bad': $body")
        }
    }

    @Test
    fun `changing only the capitalisation of your own name is allowed`() {
        val t = token("limits-case")
        assertTrue(graphql("""mutation { updateUsername(newUsername: "Limits-Case") { username } }""", t).contains("Limits-Case"))
    }

    @Test
    fun `sign-up refuses a bad email and a name that differs only by case`() {
        token("limits-existing")
        assertRefused(graphql("""mutation { createUser(username: "limits-noemail", email: "notanemail", password: "longenough1") }"""), "INVALID_EMAIL")
        assertRefused(graphql("""mutation { createUser(username: "Limits-Existing", email: "le@test.com", password: "longenough1") }"""), "USERNAME_TAKEN")
    }
}

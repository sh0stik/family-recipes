package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.AfterAll
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
 * `sharedWithMeRecipes` feeds the main list's "Shared with you" section. Before it existed a shared
 * recipe only turned up in search, so sharing looked like it had done nothing.
 *
 * Registers users, so it uses its own `recipe.invite-code` for a separate Spring context, and
 * deletes both accounts (and with them their recipes) in [tearDown]: contexts share one database,
 * and SeedDataIntegrationTest asserts exact seed counts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["recipe.invite-code=shared-with-me-invite"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SharedWithMeIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    lateinit var ownerToken: String
    lateinit var friendToken: String
    lateinit var sharedRecipe: String
    lateinit var trashedRecipe: String
    lateinit var privateRecipe: String

    @BeforeAll
    fun setUp() {
        ownerToken = signUp("shared-owner")
        friendToken = signUp("shared-friend")
        val friendId = graphql("query { me { id } }", friendToken).substringAfter("\"id\":\"").substringBefore("\"")

        sharedRecipe = id(graphql("""mutation { createRecipe(name: "Shared borscht") { id } }""", ownerToken))
        graphql("""mutation { shareRecipe(recipeId: "$sharedRecipe", userId: "$friendId") { id } }""", ownerToken)

        // Shared, then trashed by its owner: gone from the friend's list too.
        trashedRecipe = id(graphql("""mutation { createRecipe(name: "Trashed pie") { id } }""", ownerToken))
        graphql("""mutation { shareRecipe(recipeId: "$trashedRecipe", userId: "$friendId") { id } }""", ownerToken)
        graphql("""mutation { moveToTrash(recipeId: "$trashedRecipe") { id } }""", ownerToken)

        privateRecipe = id(graphql("""mutation { createRecipe(name: "Never shared") { id } }""", ownerToken))
    }

    @AfterAll
    fun tearDown() {
        graphql("""mutation { deleteAccount(password: "password123") }""", friendToken)
        graphql("""mutation { deleteAccount(password: "password123") }""", ownerToken)
    }

    private fun signUp(username: String): String =
        graphql(
            """mutation { createUser(username: "$username", email: "$username@test.com", password: "password123", inviteCode: "shared-with-me-invite") }"""
        ).substringAfter("\"createUser\":\"").substringBefore("\"")

    private fun id(response: String): String = response.substringAfter("\"id\":\"").substringBefore("\"")

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
    fun `friend sees the recipe shared with them, and only that one`() {
        val response = graphql("query { sharedWithMeRecipes { id } }", friendToken)
        assertFalse(response.contains("\"errors\""), response)
        assertTrue(response.contains(sharedRecipe), response)
        assertFalse(response.contains(trashedRecipe), "a trashed recipe must not be listed: $response")
        assertFalse(response.contains(privateRecipe), "an unshared recipe must not be listed: $response")
    }

    @Test
    fun `owner does not see their own recipes as shared with them`() {
        val response = graphql("query { sharedWithMeRecipes { id } }", ownerToken)
        assertFalse(response.contains("\"errors\""), response)
        assertTrue(response.contains("\"sharedWithMeRecipes\":[]"), response)
    }

    @Test
    fun `guest is refused`() {
        val response = graphql("query { sharedWithMeRecipes { id } }")
        assertTrue(response.contains("\"errors\""), response)
    }
}

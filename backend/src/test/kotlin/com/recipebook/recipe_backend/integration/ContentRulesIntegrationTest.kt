package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Google's UGC policy: users accept the terms before their content is shown to others, and the
 * terms define what is not allowed. Here: the acceptance itself, the refusal without it, and the
 * blocked-word check.
 */
class ContentRulesIntegrationTest : ModerationTestBase() {

    private lateinit var newAppToken: String
    private lateinit var oldAppToken: String

    private fun signUp(name: String, extra: String = ""): String {
        track(name)
        val response = api.graphql(
            """mutation { createUser(username: "$name", email: "$name@test.com", password: "password123", inviteCode: "moderation-invite"$extra) }"""
        )
        check(!api.failed(response)) { "sign-up failed: $response" }
        return response.substringAfter("\"createUser\":\"").substringBefore("\"")
    }

    private fun acceptedAt(token: String): String? =
        api.graphql("{ me { termsAcceptedAt } }", token).let {
            if (it.contains("\"termsAcceptedAt\":null")) null else it.substringAfter("\"termsAcceptedAt\":\"").substringBefore("\"")
        }

    @BeforeAll
    fun setUp() {
        newAppToken = signUp("rules-new-app", ", acceptedTerms: true")
        // What an app released before the rules sends: no acceptedTerms at all.
        oldAppToken = signUp("rules-old-app")
    }

    @Test
    fun `ticking the box at sign-up records the acceptance`() {
        assertNotNull(acceptedAt(newAppToken))
        assertEquals(null, acceptedAt(oldAppToken))
    }

    @Test
    fun `an account that has not accepted cannot share a recipe until it does`() {
        val id = createRecipe("Rules pie", oldAppToken)
        for (change in listOf("visibility: FAMILY", "visibility: PUBLIC", "isPublic: true")) {
            val refused = api.graphql("""mutation { updateRecipe(recipeId: "$id", $change) { id } }""", oldAppToken)
            assertEquals("TERMS_REQUIRED", api.code(refused), "$change -> $refused")
        }
        val created = api.graphql("""mutation { createRecipe(name: "Rules straight to public", visibility: PUBLIC) { id } }""", oldAppToken)
        assertEquals("TERMS_REQUIRED", api.code(created), created)
        assertFalse(readable(id, null))

        // Private recipes are not affected.
        assertFalse(api.failed(api.graphql("""mutation { updateRecipe(recipeId: "$id", name: "Rules pie 2") { id } }""", oldAppToken)))

        val accepted = api.graphql("mutation { acceptTerms { username termsAcceptedAt } }", oldAppToken)
        assertFalse(api.failed(accepted), accepted)
        val firstDate = acceptedAt(oldAppToken)
        assertNotNull(firstDate)

        val shared = api.graphql("""mutation { updateRecipe(recipeId: "$id", visibility: PUBLIC) { visibility } }""", oldAppToken)
        assertTrue(shared.contains("\"visibility\":\"PUBLIC\""), shared)
        assertTrue(readable(id, null))

        // Accepting again keeps the first date.
        api.graphql("mutation { acceptTerms { username } }", oldAppToken)
        assertEquals(firstDate, acceptedAt(oldAppToken))
    }

    @Test
    fun `nobody else can see when you accepted`() {
        val id = createRecipe("Rules visible author", newAppToken, "PUBLIC")
        val query = """{ recipe(id: "$id") { user { username termsAcceptedAt } } }"""
        assertTrue(api.graphql(query, oldAppToken).contains("\"termsAcceptedAt\":null"))
        assertTrue(api.graphql(query).contains("\"termsAcceptedAt\":null"))
        assertFalse(api.graphql(query, newAppToken).contains("\"termsAcceptedAt\":null"))
    }

    @Test
    fun `a guest cannot accept the rules`() {
        assertTrue(api.failed(api.graphql("mutation { acceptTerms { username } }")))
    }

    @Test
    fun `a blocked word stops sharing and the refusal carries the word`() {
        val id = createRecipe("Rules борщ", newAppToken)
        api.graphql("""mutation { updateRecipe(recipeId: "$id", description: "Сварить, блядь, свёклу") { id } }""", newAppToken)

        val refused = api.graphql("""mutation { updateRecipe(recipeId: "$id", visibility: FAMILY) { id } }""", newAppToken)
        assertEquals("BLOCKED_WORD", api.code(refused), refused)
        assertTrue(refused.contains("\"word\":\"блядь\""), refused)
        assertTrue(api.graphql("""{ recipe(id: "$id") { visibility } }""", newAppToken).contains("ONLY_ME"))

        // Reworded, it goes through; and the word cannot be put back while the recipe is shared.
        api.graphql("""mutation { updateRecipe(recipeId: "$id", description: "Сварить свёклу") { id } }""", newAppToken)
        assertFalse(api.failed(api.graphql("""mutation { updateRecipe(recipeId: "$id", visibility: FAMILY) { id } }""", newAppToken)))
        val putBack = api.graphql("""mutation { updateRecipe(recipeId: "$id", name: "Fucking борщ") { id } }""", newAppToken)
        assertEquals("BLOCKED_WORD", api.code(putBack), putBack)
        assertTrue(putBack.contains("\"word\":\"Fucking\""), putBack)
    }

    @Test
    fun `ordinary recipe words are not mistaken for blocked ones`() {
        val created = api.graphql(
            """mutation { createRecipe(name: "Хачапури и spotted dick", description: "Хрен, блины, сукачёв", visibility: PUBLIC) { visibility } }""",
            newAppToken
        )
        assertTrue(created.contains("\"visibility\":\"PUBLIC\""), created)
    }
}

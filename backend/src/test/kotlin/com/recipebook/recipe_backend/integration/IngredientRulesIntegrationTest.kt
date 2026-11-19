package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ingredient list is one list for everyone, guests included, so adding to it follows the same
 * rules as sharing a recipe: the content rules must be accepted, and blocked words are refused.
 */
class IngredientRulesIntegrationTest : ModerationTestBase() {

    private val names = listOf("Rules okra", "Rules refused okra", "Сука соус")

    private fun create(name: String, token: String): String =
        api.graphql(
            """mutation { createIngredient(name: "$name", caloriesPer100g: 30, proteinPer100g: 2, fatPer100g: 0, carbsPer100g: 7) { id name } }""",
            token,
        )

    private fun stored(name: String): Boolean =
        jdbc.queryForObject("SELECT count(*) FROM ingredients WHERE name = ?", Int::class.java, name)!! > 0

    @AfterAll
    fun removeIngredients() {
        // Contexts share one database and SeedDataIntegrationTest counts the seeded ingredients.
        for (name in names) jdbc.update("DELETE FROM ingredients WHERE name = ?", name)
    }

    @Test
    fun `an account that has not accepted the content rules cannot add an ingredient`() {
        val response = create("Rules refused okra", token(user("ingredient-not-accepted", accepted = false)))
        assertEquals("TERMS_REQUIRED", api.code(response), response)
        assertFalse(stored("Rules refused okra"))
    }

    @Test
    fun `a blocked word in an ingredient name is refused and named`() {
        val response = create("Сука соус", token(user("ingredient-blocked-word")))
        assertEquals("BLOCKED_WORD", api.code(response), response)
        assertTrue(response.contains("\"word\":\"Сука\""), response)
        assertFalse(stored("Сука соус"))
    }

    @Test
    fun `an ordinary name from an account that accepted is created`() {
        val response = create("Rules okra", token(user("ingredient-accepted")))
        assertFalse(api.failed(response), response)
        assertTrue(response.contains("\"name\":\"Rules okra\""), response)
        assertTrue(stored("Rules okra"))
    }
}

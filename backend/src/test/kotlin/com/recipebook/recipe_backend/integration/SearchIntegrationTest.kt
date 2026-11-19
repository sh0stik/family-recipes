package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * searchRecipes filters by what was asked. A keyword on its own used to return nearly every
 * recipe: the ingredient branch, with no ingredient name given, matched any recipe that had an
 * ingredient. The apps send the same text as keyword and ingredientName, which hid it.
 */
class SearchIntegrationTest : ModerationTestBase() {

    private lateinit var ownerToken: String
    private lateinit var otherToken: String
    private lateinit var publicId: String
    private lateinit var hiddenId: String

    /** The ids searchRecipes([args]) answers, in any order. */
    private fun search(args: String, token: String?): Set<String> {
        val response = api.graphql("{ searchRecipes($args) { id } }", token)
        check(!api.failed(response)) { "searchRecipes failed: $response" }
        return Regex("\"id\":\"([^\"]+)\"").findAll(response).map { it.groupValues[1] }.toSet()
    }

    @BeforeAll
    fun setUp() {
        ownerToken = token(user("search-owner"))
        otherToken = token(user("search-other"))
        publicId = createRecipe("Search Zanzibarpie", ownerToken, "PUBLIC")
        hiddenId = createRecipe("Search Quokkastew", ownerToken)
        // A seeded ingredient, so these recipes are among the ones the old query matched on any keyword.
        val ingredientId = api.id(api.graphql("{ allIngredients { id } }"))
        for (id in listOf(publicId, hiddenId)) {
            val added = api.graphql("""mutation { addIngredientToRecipe(recipeId: "$id", ingredientId: "$ingredientId", quantity: 1) { id } }""", ownerToken)
            check(!api.failed(added)) { "addIngredientToRecipe failed: $added" }
        }
    }

    @Test
    fun `a keyword matching one recipe returns only that recipe, ignoring case`() {
        assertEquals(setOf(publicId), search("""keyword: "zanzibarPIE"""", null))
        assertEquals(setOf(publicId), search("""keyword: "Zanzibarpie"""", otherToken))
        assertEquals(setOf(publicId), search("""keyword: "Zanzibarpie"""", ownerToken))
    }

    @Test
    fun `a keyword that matches nothing returns nothing`() {
        assertEquals(emptySet(), search("""keyword: "nosuchrecipexyz"""", null))
        assertEquals(emptySet(), search("""keyword: "nosuchrecipexyz"""", ownerToken))
    }

    @Test
    fun `an ingredient name that matches nothing returns nothing`() {
        assertEquals(emptySet(), search("""ingredientName: "nosuchingredientxyz"""", ownerToken))
    }

    @Test
    fun `a hidden recipe whose name matches is still found only by those who may see it`() {
        assertEquals(emptySet(), search("""keyword: "Quokkastew"""", null))
        assertEquals(emptySet(), search("""keyword: "Quokkastew"""", otherToken))
        assertEquals(setOf(hiddenId), search("""keyword: "Quokkastew"""", ownerToken))
    }

    @Test
    fun `the apps' search, the same text as keyword and ingredient name, still finds by recipe name`() {
        assertEquals(setOf(publicId), search("""keyword: "Zanzibarpie", ingredientName: "Zanzibarpie"""", null))
    }
}

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

/**
 * Covers the bug where `publicRecipes` returned all public recipes (with ingredients) to a guest,
 * but `recipe(id:)` unconditionally required auth and refused the very same data. The client's
 * detail screen calls `recipe(id:)`, so a signed-out user saw "No ingredients added yet" for every
 * recipe even though the list screen had just shown them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipeAccessIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    lateinit var token: String
    lateinit var publicRecipeId: String
    lateinit var privateRecipeId: String

    @BeforeAll
    fun setUp() {
        val loginResponse = graphql("""mutation { login(username: "family", password: "password") }""")
        token = loginResponse.substringAfter("\"login\":\"").substringBefore("\"")

        // Any seeded recipe is public and has ingredients.
        val listResponse = graphql("query { publicRecipes { id ingredients { id } } }", token)
        val idPattern = Regex(""""id":"([^"]+)","ingredients":\[\{""")
        publicRecipeId = idPattern.find(listResponse)!!.groupValues[1]

        // A freshly created recipe defaults to private (isPublic = false).
        val createResponse = graphql(
            """mutation { createRecipe(name: "Guest access test private recipe") { id isPublic } }""",
            token
        )
        privateRecipeId = createResponse.substringAfter("\"id\":\"").substringBefore("\"")
        assertTrue(createResponse.contains("\"isPublic\":false"), "Expected new recipe to default to private: $createResponse")
    }

    // The Spring test context (and its schema/seed data) is shared across test classes with
    // matching configuration, so a recipe created here would otherwise pollute the fixed seed
    // counts asserted in SeedDataIntegrationTest (160 recipes, every recipe has ingredients and
    // categories).
    @AfterAll
    fun tearDown() {
        graphql("""mutation { moveToTrash(recipeId: "$privateRecipeId") { id } }""", token)
        graphql("""mutation { deletePermanently(recipeId: "$privateRecipeId") }""", token)
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
    fun `guest can fetch a public recipe by id and sees its ingredients`() {
        val query = """query { recipe(id: "$publicRecipeId") { id isPublic ingredients { id } } }"""
        val request = post("/graphql").contentType(MediaType.APPLICATION_JSON).content(buildBody(query))
        // No Authorization header at all: a true guest request.
        mockMvc.perform(request)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.recipe.id").value(publicRecipeId))
            .andExpect(jsonPath("$.data.recipe.isPublic").value(true))
            .andExpect(jsonPath("$.data.recipe.ingredients").isNotEmpty)
    }

    @Test
    fun `guest fetching a private recipe by id is still refused`() {
        val query = """query { recipe(id: "$privateRecipeId") { id } }"""
        val request = post("/graphql").contentType(MediaType.APPLICATION_JSON).content(buildBody(query))
        mockMvc.perform(request)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.data.recipe").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `owner can still fetch their own private recipe`() {
        val query = """query { recipe(id: "$privateRecipeId") { id isPublic } }"""
        val response = graphql(query, token)
        assertTrue(response.contains("\"isPublic\":false"), "Owner should see their own private recipe: $response")
        assertTrue(!response.contains("\"errors\""), "Owner fetch should not error: $response")
    }
}

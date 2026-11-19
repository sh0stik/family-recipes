package com.recipebook.recipe_backend.integration

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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeedDataIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    lateinit var token: String

    @BeforeAll
    fun setUp() {
        val response = graphqlRaw("""mutation { login(username: "family", password: "password") }""")
        // Extract token from: {"data":{"login":"eyJ..."}}
        token = response.substringAfter("\"login\":\"").substringBefore("\"")
    }

    private fun buildBody(query: String, variables: Map<String, Any>? = null): String {
        val escapedQuery = query.replace("\"", "\\\"").replace("\n", " ")
        if (variables == null) {
            return """{"query":"$escapedQuery"}"""
        }
        // Build variables JSON manually
        val varsJson = variables.entries.joinToString(",") { (k, v) ->
            """"$k":"$v""""
        }
        return """{"query":"$escapedQuery","variables":{$varsJson}}"""
    }

    private fun graphqlRaw(query: String, authToken: String? = null, variables: Map<String, Any>? = null): String {
        val request = post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildBody(query, variables))

        if (authToken != null) {
            request.header("Authorization", "Bearer $authToken")
        }

        return mockMvc.perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString
    }

    private fun graphqlExpect(query: String, authToken: String? = null): org.springframework.test.web.servlet.ResultActions {
        val request = post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildBody(query))

        if (authToken != null) {
            request.header("Authorization", "Bearer $authToken")
        }

        return mockMvc.perform(request).andExpect(status().isOk)
    }

    private fun authedExpect(query: String) = graphqlExpect(query, token)
    private fun authedRaw(query: String, variables: Map<String, Any>? = null) = graphqlRaw(query, token, variables)

    // ===== User Seed =====

    @Test
    fun `seed user can login`() {
        graphqlExpect("""mutation { login(username: "family", password: "password") }""")
            .andExpect(jsonPath("$.data.login").isNotEmpty)
    }

    @Test
    fun `seed user has preferred locale ru`() {
        authedExpect("query { recipes { user { preferredLocale } } }")
            .andExpect(jsonPath("$.data.recipes[0].user.preferredLocale").value("ru"))
    }

    // ===== Units Seed =====

    @Test
    fun `10 measurement units are seeded`() {
        authedExpect("query { allUnits { id } }")
            .andExpect(jsonPath("$.data.allUnits.length()").value(10))
    }

    @Test
    fun `expected unit abbreviations exist`() {
        val expected = setOf("г", "кг", "мл", "л", "стакан", "ст. л.", "ч. л.", "шт", "банка", "щепотка")
        val response = authedRaw("query { allUnits { abbreviation } }")
        expected.forEach { abbr ->
            assertTrue(response.contains("\"$abbr\""), "Missing unit abbreviation: $abbr")
        }
    }

    // ===== Ingredients Seed =====

    @Test
    fun `212 ingredients are seeded`() {
        authedExpect("query { allIngredients { id } }")
            .andExpect(jsonPath("$.data.allIngredients.length()").value(212))
    }

    // ===== Categories Seed =====

    @Test
    fun `12 categories are seeded`() {
        graphqlExpect("query { allCategories { id } }")
            .andExpect(jsonPath("$.data.allCategories.length()").value(12))
    }

    @Test
    fun `expected category names exist`() {
        val expected = listOf(
            "Торты", "Выпечка", "Десерты", "Блины и оладьи", "Заготовки",
            "Салаты", "Закуски", "Соусы", "Вторые блюда", "Супы", "Каши", "Кремы"
        )
        val response = graphqlRaw("query { allCategories { name } }")
        expected.forEach { name ->
            assertTrue(response.contains(name), "Missing category: $name")
        }
    }

    // ===== Recipes Seed =====

    @Test
    fun `4 visible recipes are seeded`() {
        // test_recipes_seed.sql has 5 recipes; one of them is in the trash.
        authedExpect("query { recipes { id } }")
            .andExpect(jsonPath("$.data.recipes.length()").value(4))
    }

    @Test
    fun `every recipe has at least one ingredient`() {
        val response = authedRaw("query { recipes { name ingredients { id } } }")
        // Check no recipe has empty ingredients array
        // Pattern: "ingredients":[] means empty
        val emptyPattern = Regex(""""name":"([^"]+)"[^}]*"ingredients":\[\]""")
        val matches = emptyPattern.findAll(response).map { it.groupValues[1] }.toList()
        assertTrue(matches.isEmpty(), "Recipes without ingredients: $matches")
    }

    // ===== Recipe-Category Assignments =====

    @Test
    fun `every recipe has at least one category`() {
        val response = authedRaw("query { recipes { name categories { id } } }")
        val emptyPattern = Regex(""""name":"([^"]+)"[^}]*"categories":\[\]""")
        val matches = emptyPattern.findAll(response).map { it.groupValues[1] }.toList()
        assertTrue(matches.isEmpty(), "Recipes without categories: $matches")
    }

    // ===== Search by Category =====

    @Test
    fun `search by category returns recipes`() {
        val catResponse = graphqlRaw("query { allCategories { id name } }")
        // A category the test seed assigns recipes to (not every category has one).
        val idPattern = Regex(""""id":"([^"]+)","name":"Вторые блюда"""")
        val categoryId = idPattern.find(catResponse)!!.groupValues[1]

        val query = """query SearchByCategory(${'$'}categoryId: ID!) { searchRecipes(categoryId: ${'$'}categoryId) { id } }"""
        val response = authedRaw(query, mapOf("categoryId" to categoryId))
        val hasResults = !response.contains("\"searchRecipes\":[]")
        assertTrue(hasResults, "searchRecipes returned empty for category $categoryId")
    }

    // ===== Nutrition =====

    @Test
    fun `recipes have computed nutrition info`() {
        val response = authedRaw("query { recipes { name nutrition { caloriesPer100g totalWeight } } }")
        assertTrue(!response.contains("\"errors\""), "Nutrition query returned errors: ${response.take(500)}")
        val nonNullCount = Regex(""""caloriesPer100g":\d""").findAll(response).count()
        assertTrue(nonNullCount > 0, "No recipes have computed nutrition info")
    }
}

package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Taking something off a recipe must stay off. Each test adds two, removes one through its
 * mutation, and then looks again from scratch — the stored rows, and a new `recipe(id:)` request —
 * rather than trusting the object the mutation returned: that one always looked right, while the
 * removed ingredient's row stayed in the database and came back on the next read.
 *
 * Shares the default test context; everything it creates is removed in [tearDown].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipeCollectionRemovalIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var recipeRepository: RecipeRepository
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var jdbc: JdbcTemplate

    private val api by lazy { Api(mockMvc) }
    lateinit var owner: User
    lateinit var editorA: User
    lateinit var editorB: User
    lateinit var ownerToken: String

    private fun user(name: String): User =
        userRepository.save(User(username = name, email = "$name-${System.nanoTime()}@test.com", password = "x"))

    @BeforeAll
    fun setUp() {
        owner = user("removal-owner")
        editorA = user("removal-editor-a")
        editorB = user("removal-editor-b")
        ownerToken = jwtService.generateToken(CustomUserDetails(owner))
    }

    @AfterAll
    fun tearDown() {
        recipeRepository.deleteAll(recipeRepository.findAllByUser(owner))
        userRepository.deleteAll(listOf(owner, editorA, editorB))
    }

    private fun newRecipe(name: String): String {
        val response = api.graphql("""mutation { createRecipe(name: "$name") { id } }""", ownerToken)
        check(!api.failed(response)) { "createRecipe failed: $response" }
        return api.id(response)
    }

    private fun mutate(mutation: String) {
        val response = api.graphql("mutation { $mutation { id } }", ownerToken)
        check(!api.failed(response)) { "$mutation failed: $response" }
    }

    /** A fresh read of the recipe, in its own request and transaction. */
    private fun reread(recipeId: String, fields: String): String =
        api.graphql("""{ recipe(id: "$recipeId") { $fields } }""", ownerToken)

    private fun rows(sql: String, recipeId: String): Int =
        jdbc.queryForObject(sql, Int::class.java, java.util.UUID.fromString(recipeId))!!

    private fun seededId(table: String, offset: Int): String =
        jdbc.queryForObject("SELECT id FROM $table ORDER BY name LIMIT 1 OFFSET $offset", String::class.java)!!

    @Test
    fun `a removed ingredient stays removed`() {
        val recipeId = newRecipe("Removal ingredients")
        val kept = seededId("ingredients", 0)
        val removed = seededId("ingredients", 1)
        mutate("""addIngredientToRecipe(recipeId: "$recipeId", ingredientId: "$kept", quantity: 1)""")
        mutate("""addIngredientToRecipe(recipeId: "$recipeId", ingredientId: "$removed", quantity: 2)""")

        mutate("""removeIngredientFromRecipe(recipeId: "$recipeId", ingredientId: "$removed")""")

        assertEquals(1, rows("SELECT count(*) FROM recipe_ingredients WHERE recipe_id = ?", recipeId))
        val body = reread(recipeId, "ingredients { ingredient { id } }")
        assertTrue(body.contains(kept), body)
        assertFalse(body.contains(removed), body)
    }

    @Test
    fun `a removed category stays removed`() {
        val recipeId = newRecipe("Removal categories")
        val kept = seededId("categories", 0)
        val removed = seededId("categories", 1)
        mutate("""addCategoryToRecipe(recipeId: "$recipeId", categoryId: "$kept")""")
        mutate("""addCategoryToRecipe(recipeId: "$recipeId", categoryId: "$removed")""")

        mutate("""removeCategoryFromRecipe(recipeId: "$recipeId", categoryId: "$removed")""")

        assertEquals(1, rows("SELECT count(*) FROM recipe_categories WHERE recipe_id = ?", recipeId))
        val body = reread(recipeId, "categories { id }")
        assertTrue(body.contains(kept), body)
        assertFalse(body.contains(removed), body)
    }

    @Test
    fun `a removed video link stays removed`() {
        val recipeId = newRecipe("Removal videos")
        mutate("""addVideoLinkToRecipe(recipeId: "$recipeId", url: "https://example.com/kept")""")
        mutate("""addVideoLinkToRecipe(recipeId: "$recipeId", url: "https://example.com/removed")""")

        mutate("""removeVideoLinkFromRecipe(recipeId: "$recipeId", url: "https://example.com/removed")""")

        assertEquals(1, rows("SELECT count(*) FROM recipe_video_links WHERE recipe_id = ?", recipeId))
        val body = reread(recipeId, "videoLinks")
        assertTrue(body.contains("https://example.com/kept"), body)
        assertFalse(body.contains("https://example.com/removed"), body)
    }

    @Test
    fun `a removed photo stays removed`() {
        // One photo per recipe, so it is the only one: removing it must leave none.
        val recipeId = newRecipe("Removal photo")
        mutate("""addPhotoToRecipe(recipeId: "$recipeId", url: "https://example.com/removed.jpg")""")

        mutate("""removePhotoFromRecipe(recipeId: "$recipeId", url: "https://example.com/removed.jpg")""")

        assertEquals(0, rows("SELECT count(*) FROM recipe_photos WHERE recipe_id = ?", recipeId))
        val body = reread(recipeId, "photoUrls")
        assertTrue(body.contains("\"photoUrls\":[]"), body)
    }

    @Test
    fun `a removed editor stays removed`() {
        val recipeId = newRecipe("Removal editors")
        mutate("""shareRecipe(recipeId: "$recipeId", userId: "${editorA.id}")""")
        mutate("""shareRecipe(recipeId: "$recipeId", userId: "${editorB.id}")""")

        mutate("""unshareRecipe(recipeId: "$recipeId", userId: "${editorB.id}")""")

        assertEquals(1, rows("SELECT count(*) FROM recipe_shared_users WHERE recipe_id = ?", recipeId))
        val body = reread(recipeId, "sharedWith { username }")
        assertTrue(body.contains("removal-editor-a"), body)
        assertFalse(body.contains("removal-editor-b"), body)
    }

    @Test
    fun `a deleted note stays deleted`() {
        val recipeId = newRecipe("Removal notes")
        api.graphql("""mutation { addNote(recipeId: "$recipeId", note: "kept note") { id } }""", ownerToken)
        val noteId = api.id(api.graphql("""mutation { addNote(recipeId: "$recipeId", note: "removed note") { id } }""", ownerToken))

        val response = api.graphql("""mutation { deleteNote(noteId: "$noteId") }""", ownerToken)
        assertFalse(api.failed(response), response)

        assertEquals(1, rows("SELECT count(*) FROM recipe_notes WHERE recipe_id = ?", recipeId))
        val body = reread(recipeId, "notes { note }")
        assertTrue(body.contains("kept note"), body)
        assertFalse(body.contains("removed note"), body)
    }

    @Test
    fun `a copy keeps its own ingredients when one is removed from the original`() {
        // copyRecipe builds new ingredient rows for the copy; removing from one must not touch the other.
        val recipeId = newRecipe("Removal copy source")
        val first = seededId("ingredients", 2)
        val second = seededId("ingredients", 3)
        mutate("""addIngredientToRecipe(recipeId: "$recipeId", ingredientId: "$first", quantity: 1)""")
        mutate("""addIngredientToRecipe(recipeId: "$recipeId", ingredientId: "$second", quantity: 1)""")
        val copyId = api.id(api.graphql("""mutation { copyRecipe(recipeId: "$recipeId") { id } }""", ownerToken))

        mutate("""removeIngredientFromRecipe(recipeId: "$recipeId", ingredientId: "$second")""")

        assertEquals(1, rows("SELECT count(*) FROM recipe_ingredients WHERE recipe_id = ?", recipeId))
        assertEquals(2, rows("SELECT count(*) FROM recipe_ingredients WHERE recipe_id = ?", copyId))
    }
}

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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Who can edit": the people a recipe's owner picks may read it at any visibility and change what
 * it says, but deleting it, changing who sees it and changing the editor list stay with the owner.
 *
 * Shares the default test context; everything it creates is removed in [tearDown].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EditorsIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var recipeRepository: RecipeRepository
    @Autowired lateinit var jwtService: JwtService

    private val api by lazy { Api(mockMvc) }
    lateinit var owner: User
    lateinit var editor: User
    lateinit var other: User
    lateinit var ownerToken: String
    lateinit var editorToken: String
    lateinit var otherToken: String
    lateinit var recipeId: String

    private fun user(name: String): User =
        userRepository.save(User(username = name, email = "$name-${System.nanoTime()}@test.com", password = "x"))

    @BeforeAll
    fun setUp() {
        owner = user("editors-owner")
        editor = user("editors-editor")
        other = user("editors-other")
        ownerToken = jwtService.generateToken(CustomUserDetails(owner))
        editorToken = jwtService.generateToken(CustomUserDetails(editor))
        otherToken = jwtService.generateToken(CustomUserDetails(other))

        // Only me: the editor's access must come from the editor list alone.
        recipeId = api.id(api.graphql("""mutation { createRecipe(name: "Editors borscht") { id } }""", ownerToken))
        api.graphql("""mutation { addNote(recipeId: "$recipeId", note: "owner note") { id } }""", ownerToken)
        api.graphql("""mutation { shareRecipe(recipeId: "$recipeId", userId: "${editor.id}") { id } }""", ownerToken)
    }

    @AfterAll
    fun tearDown() {
        recipeRepository.deleteAll(listOf(owner, editor, other).flatMap { recipeRepository.findAllByUser(it) })
        userRepository.deleteAll(listOf(owner, editor, other))
    }

    private val detail get() = """{ recipe(id: "$recipeId") { name visibility canEdit canManage notes { note } sharedWith { username } } }"""

    @Test
    fun `an editor reads an only-me recipe with its notes and editor list`() {
        val body = api.graphql(detail, editorToken)
        assertTrue(body.contains("\"visibility\":\"ONLY_ME\""), body)
        assertTrue(body.contains("\"canEdit\":true"), body)
        assertTrue(body.contains("\"canManage\":false"), body)
        assertTrue(body.contains("owner note"), body)
        assertTrue(body.contains("editors-editor"), body)
    }

    @Test
    fun `it is listed under shared with me for the editor only`() {
        assertTrue(api.graphql("{ sharedWithMeRecipes { id } }", editorToken).contains(recipeId))
        assertTrue(api.graphql("{ recipes { id } }", editorToken).contains(recipeId))
        assertFalse(api.graphql("{ myRecipes { id } }", editorToken).contains(recipeId), "an edited recipe is not \"mine\"")
        assertFalse(api.graphql("{ sharedWithMeRecipes { id } }", otherToken).contains(recipeId))
        assertFalse(api.graphql("{ recipes { id } }", otherToken).contains(recipeId))
    }

    @Test
    fun `someone who is not an editor cannot read or change it`() {
        assertTrue(api.failed(api.graphql(detail, otherToken)))
        assertTrue(api.failed(api.graphql("""mutation { updateRecipe(recipeId: "$recipeId", name: "hacked") { id } }""", otherToken)))
        assertTrue(api.failed(api.graphql("""mutation { addNote(recipeId: "$recipeId", note: "x") { id } }""", otherToken)))
        assertTrue(api.failed(api.graphql("""mutation { unshareRecipe(recipeId: "$recipeId", userId: "${editor.id}") { id } }""", otherToken)))
        assertTrue(api.graphql(detail, ownerToken).contains("Editors borscht"))
    }

    @Test
    fun `an editor changes the content`() {
        val renamed = api.graphql("""mutation { updateRecipe(recipeId: "$recipeId", description: "edited by the editor") { description } }""", editorToken)
        assertTrue(renamed.contains("edited by the editor"), renamed)
        assertFalse(api.failed(api.graphql("""mutation { addVideoLinkToRecipe(recipeId: "$recipeId", url: "https://example.com/v") { id } }""", editorToken)))
        val note = api.graphql("""mutation { addNote(recipeId: "$recipeId", note: "editor note") { id } }""", editorToken)
        assertFalse(api.failed(note), note)

        val seenByOwner = api.graphql("""{ recipe(id: "$recipeId") { description videoLinks notes { note user { username } } } }""", ownerToken)
        assertTrue(seenByOwner.contains("edited by the editor") && seenByOwner.contains("https://example.com/v"), seenByOwner)
        assertTrue(seenByOwner.contains("editor note"), seenByOwner)
    }

    @Test
    fun `an editor cannot trash it, change who sees it, or add editors`() {
        for (mutation in listOf(
            """moveToTrash(recipeId: "$recipeId") { id }""",
            """updateRecipe(recipeId: "$recipeId", visibility: PUBLIC) { id }""",
            """updateRecipe(recipeId: "$recipeId", isPublic: true) { id }""",
            """shareRecipe(recipeId: "$recipeId", userId: "${other.id}") { id }""",
        )) {
            val response = api.graphql("mutation { $mutation }", editorToken)
            assertEquals("OWNER_ONLY", api.code(response), "$mutation -> $response")
        }
        val after = api.graphql(detail, ownerToken)
        assertTrue(after.contains("\"visibility\":\"ONLY_ME\""), after)
        assertFalse(after.contains("editors-other"), after)
        assertFalse(api.graphql("{ recipe(id: \"$recipeId\") { isTrashed } }", ownerToken).contains("\"isTrashed\":true"))
    }

    @Test
    fun `an editor can leave, and then has no access`() {
        val id = api.id(api.graphql("""mutation { createRecipe(name: "Editors leave me") { id } }""", ownerToken))
        api.graphql("""mutation { shareRecipe(recipeId: "$id", userId: "${editor.id}") { id } }""", ownerToken)
        assertTrue(api.graphql("""{ recipe(id: "$id") { id } }""", editorToken).contains(id))

        assertFalse(api.failed(api.graphql("""mutation { unshareRecipe(recipeId: "$id", userId: "${editor.id}") { id } }""", editorToken)))

        assertTrue(api.failed(api.graphql("""{ recipe(id: "$id") { id } }""", editorToken)))
        assertFalse(api.graphql("{ sharedWithMeRecipes { id } }", editorToken).contains(id))
    }
}

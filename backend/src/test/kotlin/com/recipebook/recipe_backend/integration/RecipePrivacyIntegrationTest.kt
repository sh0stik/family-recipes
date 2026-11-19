package com.recipebook.recipe_backend.integration

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
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit finding S-07: a public recipe exposed its owner's notes and the usernames it was shared
 * with to every reader, guests included, and a public recipe moved to trash stayed readable by id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipePrivacyIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var jwtService: JwtService

    lateinit var ownerToken: String
    lateinit var readerToken: String
    lateinit var editorToken: String
    lateinit var recipeId: String

    private fun user(name: String): User =
        userRepository.save(User(username = name, email = "$name-${System.nanoTime()}@test.com", password = "x", termsAcceptedAt = java.time.Instant.now()))

    private fun graphql(query: String, token: String? = null): String {
        val escaped = query.replace("\"", "\\\"").replace("\n", " ")
        val request = post("/graphql").contentType(MediaType.APPLICATION_JSON).content("""{"query":"$escaped"}""")
        if (token != null) request.header("Authorization", "Bearer $token")
        return mockMvc.perform(request).andReturn().response.contentAsString
    }

    @BeforeAll
    fun setUp() {
        val owner = user("privacy-owner")
        val reader = user("privacy-reader")
        val editor = user("privacy-editor")
        ownerToken = jwtService.generateToken(CustomUserDetails(owner))
        readerToken = jwtService.generateToken(CustomUserDetails(reader))
        editorToken = jwtService.generateToken(CustomUserDetails(editor))

        recipeId = graphql("""mutation { createRecipe(name: "Privacy check") { id } }""", ownerToken)
            .substringAfter("\"id\":\"").substringBefore("\"")
        graphql("""mutation { addNote(recipeId: "$recipeId", note: "owner kitchen note") { id } }""", ownerToken)
        graphql("""mutation { shareRecipe(recipeId: "$recipeId", userId: "${editor.id}") { id } }""", ownerToken)
        graphql("""mutation { updateRecipe(recipeId: "$recipeId", isPublic: true) { id } }""", ownerToken)
    }

    // The test context is shared with SeedDataIntegrationTest, which counts recipes.
    @AfterAll
    fun tearDown() {
        graphql("""mutation { moveToTrash(recipeId: "$recipeId") { id } }""", ownerToken)
        graphql("""mutation { deletePermanently(recipeId: "$recipeId") }""", ownerToken)
    }

    private val detail get() = """{ recipe(id: "$recipeId") { name notes { note } sharedWith { username } } }"""

    @Test
    fun `owner sees notes and the share list`() {
        val body = graphql(detail, ownerToken)
        assertTrue(body.contains("owner kitchen note"), body)
        assertTrue(body.contains("privacy-editor"), body)
    }

    // An editor works on the recipe, so they see its notes and who else edits it.
    @Test
    fun `an editor sees notes and the editor list`() {
        val body = graphql(detail, editorToken)
        assertTrue(body.contains("owner kitchen note"), body)
        assertTrue(body.contains("privacy-editor"), body)
    }

    @Test
    fun `guests and other readers see neither`() {
        for (token in listOf(null, readerToken)) {
            val body = graphql(detail, token)
            assertTrue(body.contains("Privacy check"), body)
            assertFalse(body.contains("owner kitchen note"), body)
            assertFalse(body.contains("privacy-editor"), body)
        }
    }

    @Test
    fun `a trashed public recipe is gone for everyone but its owner`() {
        graphql("""mutation { moveToTrash(recipeId: "$recipeId") { id } }""", ownerToken)
        try {
            assertFalse(graphql(detail).contains("Privacy check"))
            assertFalse(graphql(detail, readerToken).contains("Privacy check"))
            assertFalse(graphql(detail, editorToken).contains("Privacy check"), "an editor loses a trashed recipe too")
            assertTrue(graphql(detail, ownerToken).contains("Privacy check"))
        } finally {
            graphql("""mutation { restoreFromTrash(recipeId: "$recipeId") { id } }""", ownerToken)
        }
    }
}

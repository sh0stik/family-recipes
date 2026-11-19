package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.user.User
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Blocking, end to end (Apple Guideline 1.2: "the ability to block abusive users"; Google UGC
 * policy: in-app blocking of users for publicly accessible content).
 *
 * Ann blocks Ben. Ann must stop getting anything of Ben's; Ben must lose Ann's Family recipes but
 * keep her Public ones; neither may edit the other's recipes. Cat is the control: nothing changes
 * for her. Ordered, because the tests walk through block, effects, unblock.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BlocksIntegrationTest : ModerationTestBase() {

    private lateinit var ann: User
    private lateinit var ben: User
    private lateinit var cat: User
    private lateinit var annToken: String
    private lateinit var benToken: String
    private lateinit var catToken: String
    private lateinit var annFamily: String
    private lateinit var annPublic: String
    private lateinit var annEditedByBen: String
    private lateinit var benFamily: String
    private lateinit var benPublic: String
    private lateinit var benEditedByAnn: String
    private lateinit var catRecipeWithBensNote: String

    private val lists = listOf("recipes", "publicRecipes", "sharedWithMeRecipes", """searchRecipes(keyword: "Blocks", ingredientName: "Blocks")""")
    private fun anywhere(recipeId: String, token: String) = lists.any { listed(it, recipeId, token) } || readable(recipeId, token)
    private fun share(recipeId: String, user: User, token: String) =
        api.graphql("""mutation { shareRecipe(recipeId: "$recipeId", userId: "${user.id}") { id } }""", token)
    private fun editors(recipeId: String, token: String) =
        api.graphql("""{ recipe(id: "$recipeId") { sharedWith { username } } }""", token)

    @BeforeAll
    fun setUp() {
        ann = user("blocks-ann"); ben = user("blocks-ben"); cat = user("blocks-cat")
        annToken = token(ann); benToken = token(ben); catToken = token(cat)

        annFamily = createRecipe("Blocks ann family", annToken, "FAMILY")
        annPublic = createRecipe("Blocks ann public", annToken, "PUBLIC")
        annEditedByBen = createRecipe("Blocks ann edited by ben", annToken)
        share(annEditedByBen, ben, annToken)

        benFamily = createRecipe("Blocks ben family", benToken, "FAMILY")
        benPublic = createRecipe("Blocks ben public", benToken, "PUBLIC")
        benEditedByAnn = createRecipe("Blocks ben edited by ann", benToken)
        share(benEditedByAnn, ann, benToken)

        // Cat lets both edit one recipe; Ben leaves a note on it.
        catRecipeWithBensNote = createRecipe("Blocks cat shared", catToken)
        share(catRecipeWithBensNote, ann, catToken)
        share(catRecipeWithBensNote, ben, catToken)
        api.graphql("""mutation { addNote(recipeId: "$catRecipeWithBensNote", note: "note by ben") { id } }""", benToken)
        api.graphql("""mutation { addNote(recipeId: "$catRecipeWithBensNote", note: "note by cat") { id } }""", catToken)
    }

    @Test
    @Order(1)
    fun `before the block everyone sees what the levels allow`() {
        for (id in listOf(benFamily, benPublic, benEditedByAnn)) assertTrue(readable(id, annToken))
        for (id in listOf(annFamily, annPublic, annEditedByBen)) assertTrue(readable(id, benToken))
        assertTrue(api.graphql("{ users { username } }", annToken).contains("blocks-ben"))
    }

    @Test
    @Order(2)
    fun `what cannot be blocked`() {
        assertEquals("CANNOT_BLOCK", api.code(api.graphql("""mutation { blockUser(userId: "${ann.id}") }""", annToken)))
        val family = userRepository.findByUsername("family").get()
        assertEquals("CANNOT_BLOCK", api.code(api.graphql("""mutation { blockUser(userId: "${family.id}") }""", annToken)))
        assertTrue(api.failed(api.graphql("""mutation { blockUser(userId: "${ben.id}") }""")), "a guest cannot block")
        assertTrue(api.graphql("{ blockedUsers { username } }", annToken).contains("\"blockedUsers\":[]"))
    }

    @Test
    @Order(3)
    fun `ann blocks ben, twice`() {
        repeat(2) {
            val response = api.graphql("""mutation { blockUser(userId: "${ben.id}") }""", annToken)
            assertTrue(response.contains("\"blockUser\":true"), response)
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?", Int::class.java, ann.id, ben.id))
        val blocked = api.graphql("{ blockedUsers { username email } }", annToken)
        assertTrue(blocked.contains("blocks-ben"), blocked)
        assertFalse(blocked.contains("@test.com"), "a blocked person's email stays private: $blocked")
        assertTrue(api.graphql("{ blockedUsers { username } }", benToken).contains("\"blockedUsers\":[]"), "the list is the caller's own")
    }

    @Test
    @Order(4)
    fun `ann no longer gets anything of ben's`() {
        for (id in listOf(benFamily, benPublic, benEditedByAnn)) assertFalse(anywhere(id, annToken), "ann still gets $id")
        assertTrue(api.failed(api.graphql("""mutation { copyRecipe(recipeId: "$benPublic") { id } }""", annToken)))

        val notes = api.graphql("""{ recipe(id: "$catRecipeWithBensNote") { notes { note } } }""", annToken)
        assertTrue(notes.contains("note by cat"), notes)
        assertFalse(notes.contains("note by ben"), "a blocked person's notes are hidden: $notes")
    }

    @Test
    @Order(5)
    fun `ben loses ann's family recipes but keeps her public ones`() {
        assertFalse(anywhere(annFamily, benToken))
        assertFalse(anywhere(annEditedByBen, benToken), "being an editor ended with the block")
        assertTrue(readable(annPublic, benToken))
        assertTrue(listed("publicRecipes", annPublic, benToken))
        assertTrue(api.failed(api.graphql("""mutation { updateRecipe(recipeId: "$annEditedByBen", name: "hacked") { id } }""", benToken)))
    }

    @Test
    @Order(6)
    fun `each is off the other's editor lists and cannot be put back`() {
        assertFalse(editors(annEditedByBen, annToken).contains("blocks-ben"))
        assertFalse(editors(benEditedByAnn, benToken).contains("blocks-ann"))
        assertEquals("BLOCKED", api.code(share(annEditedByBen, ben, annToken)))
        assertEquals("BLOCKED", api.code(share(benEditedByAnn, ann, benToken)))
        assertFalse(api.graphql("{ users { username } }", annToken).contains("blocks-ben"), "not offered in the editor picker")
        assertFalse(api.graphql("{ users { username } }", benToken).contains("blocks-ann"))
        // Someone else's recipe is not touched: Cat's editors are Cat's business.
        assertTrue(editors(catRecipeWithBensNote, catToken).let { it.contains("blocks-ann") && it.contains("blocks-ben") })
    }

    @Test
    @Order(7)
    fun `nothing changed for anyone else, or for guests`() {
        for (id in listOf(annFamily, annPublic, benFamily, benPublic)) assertTrue(readable(id, catToken), id)
        assertTrue(readable(annPublic, null) && readable(benPublic, null))
        assertTrue(api.graphql("""{ recipe(id: "$catRecipeWithBensNote") { notes { note } } }""", catToken).contains("note by ben"))
        // Ben can still see his own things, and Ann hers.
        assertTrue(listed("myRecipes", benFamily, benToken) && listed("myRecipes", annFamily, annToken))
    }

    @Test
    @Order(8)
    fun `ben cannot lift ann's block, ann can`() {
        api.graphql("""mutation { unblockUser(userId: "${ann.id}") }""", benToken)
        assertFalse(readable(annFamily, benToken))

        val response = api.graphql("""mutation { unblockUser(userId: "${ben.id}") }""", annToken)
        assertTrue(response.contains("\"unblockUser\":true"), response)

        assertTrue(readable(annFamily, benToken))
        assertTrue(readable(benFamily, annToken) && readable(benPublic, annToken))
        assertTrue(api.graphql("""{ recipe(id: "$catRecipeWithBensNote") { notes { note } } }""", annToken).contains("note by ben"))
        // Editor rights do not come back by themselves, but can be given again.
        assertFalse(readable(annEditedByBen, benToken))
        assertFalse(api.failed(share(annEditedByBen, ben, annToken)))
        assertTrue(readable(annEditedByBen, benToken))
    }
}

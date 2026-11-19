package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.user.User
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What suspending an account does besides hiding its recipes: its notes on other people's recipes
 * stop being returned, it drops out of the editor picker (`users`) and it cannot be newly made an
 * editor (USER_SUSPENDED). Unsuspending restores all three; nothing is deleted.
 *
 * Suspension is the documented SQL statement. Sam is the account that gets suspended; every test
 * leaves him unsuspended again.
 */
class SuspensionVisibilityIntegrationTest : ModerationTestBase() {

    private lateinit var owner: User
    private lateinit var editor: User
    private lateinit var sam: User
    private lateinit var admin: User
    private lateinit var ownerToken: String
    private lateinit var editorToken: String
    private lateinit var samToken: String
    private lateinit var adminToken: String
    private lateinit var recipe: String

    private fun suspend(user: User) = assertEquals(1, jdbc.update("UPDATE users SET suspended = true WHERE username = ?", user.username))
    private fun unsuspend(user: User) = assertEquals(1, jdbc.update("UPDATE users SET suspended = false WHERE username = ?", user.username))
    private fun notes(token: String) = api.graphql("""{ recipe(id: "$recipe") { notes { note } } }""", token)
    private fun picker(token: String) = api.graphql("{ users { username } }", token)
    private fun share(user: User, token: String) =
        api.graphql("""mutation { shareRecipe(recipeId: "$recipe", userId: "${user.id}") { id } }""", token)
    private fun editors(token: String) = api.graphql("""{ recipe(id: "$recipe") { sharedWith { username } } }""", token)

    @BeforeAll
    fun setUp() {
        admin = userRepository.findByUsername("moderation-admin").orElseGet { user("moderation-admin") }
        owner = user("susp-owner"); editor = user("susp-editor"); sam = user("susp-sam")
        adminToken = token(admin); ownerToken = token(owner); editorToken = token(editor); samToken = token(sam)

        // The owner's recipe, edited by Sam and by another editor, each of whom left a note.
        recipe = createRecipe("Suspension shared recipe", ownerToken)
        assertFalse(api.failed(share(sam, ownerToken)))
        assertFalse(api.failed(share(editor, ownerToken)))
        api.graphql("""mutation { addNote(recipeId: "$recipe", note: "note by sam") { id } }""", samToken)
        api.graphql("""mutation { addNote(recipeId: "$recipe", note: "note by editor") { id } }""", editorToken)
    }

    @Test
    fun `a suspended account's notes on other people's recipes are not returned, and come back`() {
        for (token in listOf(ownerToken, editorToken)) {
            val before = notes(token)
            assertTrue(before.contains("note by sam") && before.contains("note by editor"), before)
        }

        suspend(sam)
        for (token in listOf(ownerToken, editorToken)) {
            val during = notes(token)
            assertFalse(during.contains("note by sam"), "a suspended account's note is hidden: $during")
            assertTrue(during.contains("note by editor"), "other notes stay: $during")
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM recipe_notes WHERE note = 'note by sam'", Int::class.java), "nothing is deleted")

        unsuspend(sam)
        for (token in listOf(ownerToken, editorToken)) {
            assertTrue(notes(token).contains("note by sam"), "unsuspending restores the note")
        }
    }

    @Test
    fun `admins do not get a suspended account's notes either`() {
        // Admins can edit, so read the notes of, family-collection recipes only. Seed counts are
        // asserted elsewhere, so the recipe is deleted again.
        val familyToken = token(userRepository.findByUsername("family").get())
        val id = createRecipe("Suspension family recipe", familyToken)
        try {
            assertFalse(api.failed(api.graphql("""mutation { shareRecipe(recipeId: "$id", userId: "${sam.id}") { id } }""", familyToken)))
            api.graphql("""mutation { addNote(recipeId: "$id", note: "family note by sam") { id } }""", samToken)
            val read = { api.graphql("""{ recipe(id: "$id") { notes { note } } }""", adminToken) }
            assertTrue(read().contains("family note by sam"))

            suspend(sam)
            assertFalse(read().contains("family note by sam"), read())
            unsuspend(sam)
            assertTrue(read().contains("family note by sam"))
        } finally {
            unsuspend(sam)
            api.graphql("""mutation { moveToTrash(recipeId: "$id") { id } }""", familyToken)
            api.graphql("""mutation { deletePermanently(recipeId: "$id") }""", familyToken)
        }
    }

    @Test
    fun `a suspended account is left out of the users query, and comes back`() {
        for (token in listOf(ownerToken, editorToken, adminToken)) assertTrue(picker(token).contains("susp-sam"), "before")

        suspend(sam)
        for (token in listOf(ownerToken, editorToken, adminToken)) {
            val during = picker(token)
            assertFalse(during.contains("susp-sam"), during)
            assertTrue(during.contains("susp-editor"), "everyone else is still offered: $during")
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE username = 'susp-sam'", Int::class.java))

        unsuspend(sam)
        assertTrue(picker(ownerToken).contains("susp-sam"))
    }

    @Test
    fun `a suspended account cannot be newly made an editor, and can again once unsuspended`() {
        val other = createRecipe("Suspension second recipe", ownerToken)

        suspend(sam)
        val refused = api.graphql("""mutation { shareRecipe(recipeId: "$other", userId: "${sam.id}") { id } }""", ownerToken)
        assertEquals("USER_SUSPENDED", api.code(refused), refused)
        assertFalse(api.graphql("""{ recipe(id: "$other") { sharedWith { username } } }""", ownerToken).contains("susp-sam"), "not added")

        unsuspend(sam)
        val allowed = api.graphql("""mutation { shareRecipe(recipeId: "$other", userId: "${sam.id}") { id } }""", ownerToken)
        assertFalse(api.failed(allowed), allowed)
        assertTrue(api.graphql("""{ recipe(id: "$other") { sharedWith { username } } }""", ownerToken).contains("susp-sam"))
    }

    @Test
    fun `an editor who is later suspended stays on the list, so unsuspending restores their access`() {
        suspend(sam)
        assertTrue(editors(ownerToken).contains("susp-sam"), "nothing is removed by suspending")
        // Sharing again with someone already on the list is the usual no-op, not an error.
        assertFalse(api.failed(share(sam, ownerToken)))
        unsuspend(sam)
        assertTrue(editors(ownerToken).contains("susp-sam"))
        assertEquals(200, api.status("{ me { username } }", samToken))
    }
}

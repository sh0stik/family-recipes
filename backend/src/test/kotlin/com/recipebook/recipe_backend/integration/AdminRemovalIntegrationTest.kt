package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.user.User
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the admins can do about a report: take a recipe out of view, and stop an account. ("It is
 * your responsibility to remove content that violates this guideline" — Apple 1.2.)
 */
class AdminRemovalIntegrationTest : ModerationTestBase() {

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    private lateinit var admin: User
    private lateinit var author: User
    private lateinit var reader: User
    private lateinit var adminToken: String
    private lateinit var authorToken: String
    private lateinit var readerToken: String

    @BeforeAll
    fun setUp() {
        admin = userRepository.findByUsername("moderation-admin").orElseGet { user("moderation-admin") }
        author = user("removal-author"); reader = user("removal-reader")
        adminToken = token(admin); authorToken = token(author); readerToken = token(reader)
    }

    private fun unpublish(id: String, token: String?, reason: String = "advertising") =
        api.graphql("""mutation { adminUnpublishRecipe(recipeId: "$id", reason: "$reason") }""", token)

    @Test
    fun `an admin takes someone's public recipe out of view and the owner cannot put it back`() {
        val id = createRecipe("Removal advert", authorToken, "PUBLIC")
        assertTrue(readable(id, null))

        val response = unpublish(id, adminToken)
        assertTrue(response.contains("\"adminUnpublishRecipe\":true"), response)

        assertFalse(readable(id, null))
        assertFalse(readable(id, readerToken))
        assertFalse(listed("publicRecipes", id, null))
        assertEquals(false, jdbc.queryForObject("SELECT is_public FROM recipes WHERE id = ?::uuid", Boolean::class.java, id))

        // The owner keeps it, is told why, and can still edit it.
        val own = api.graphql("""{ recipe(id: "$id") { visibility removedByAdmin removalReason } }""", authorToken)
        assertTrue(own.contains("\"visibility\":\"ONLY_ME\"") && own.contains("\"removedByAdmin\":true") && own.contains("\"removalReason\":\"advertising\""), own)
        assertFalse(api.failed(api.graphql("""mutation { updateRecipe(recipeId: "$id", name: "Removal advert, reworded") { id } }""", authorToken)))

        for (change in listOf("visibility: PUBLIC", "visibility: FAMILY", "isPublic: true")) {
            val refused = api.graphql("""mutation { updateRecipe(recipeId: "$id", $change) { id } }""", authorToken)
            assertEquals("REMOVED_BY_ADMIN", api.code(refused), "$change -> $refused")
        }
        assertFalse(readable(id, null))

        // Lifted: still private, until the owner shares it again.
        assertTrue(api.graphql("""mutation { adminAllowPublish(recipeId: "$id") }""", adminToken).contains("\"adminAllowPublish\":true"))
        assertFalse(readable(id, null))
        val after = api.graphql("""mutation { updateRecipe(recipeId: "$id", visibility: PUBLIC) { visibility removedByAdmin removalReason } }""", authorToken)
        assertTrue(after.contains("\"visibility\":\"PUBLIC\"") && after.contains("\"removedByAdmin\":false") && after.contains("\"removalReason\":null"), after)
        assertTrue(readable(id, null))
    }

    @Test
    fun `only admins can remove or allow`() {
        val id = createRecipe("Removal not yours", authorToken, "PUBLIC")
        for (token in listOf(readerToken, authorToken)) {
            assertEquals("ADMIN_ONLY", api.code(unpublish(id, token)))
            assertEquals("ADMIN_ONLY", api.code(api.graphql("""mutation { adminAllowPublish(recipeId: "$id") }""", token)))
        }
        assertTrue(api.failed(unpublish(id, null)))
        assertEquals("EMPTY_FIELD", api.code(unpublish(id, adminToken, reason = " ")))
        assertTrue(readable(id, null))
        assertTrue(api.graphql("""{ recipe(id: "$id") { removedByAdmin } }""", readerToken).contains("\"removedByAdmin\":false"))
    }

    @Test
    fun `a suspended account cannot sign in, its token dies, and its recipes disappear`() {
        val name = "removal-spammer"
        track(name)
        val spammer = userRepository.save(
            User(username = name, email = "$name@test.com", password = passwordEncoder.encode("password123")!!, termsAcceptedAt = java.time.Instant.now())
        )
        val login = """mutation { login(username: "$name", password: "password123") }"""
        val spammerToken = api.graphql(login).substringAfter("\"login\":\"").substringBefore("\"")
        val public = createRecipe("Removal spam", spammerToken, "PUBLIC")
        val family = createRecipe("Removal spam family", spammerToken, "FAMILY")
        assertTrue(readable(public, null) && readable(family, readerToken))

        // The documented way: one SQL statement.
        assertEquals(1, jdbc.update("UPDATE users SET suspended = true WHERE username = ?", name))

        assertEquals(401, api.status("{ me { username } }", spammerToken), "the token must stop working at once")
        assertEquals("ACCOUNT_SUSPENDED", api.code(api.graphql(login)))
        // A wrong password learns nothing about the suspension.
        assertEquals("INVALID_CREDENTIALS", api.code(api.graphql("""mutation { login(username: "$name", password: "wrong-password") }""")))

        assertFalse(readable(public, null))
        assertFalse(readable(public, readerToken))
        assertFalse(readable(family, readerToken))
        for (list in listOf("publicRecipes", """searchRecipes(keyword: "Removal spam", ingredientName: "Removal spam")""")) {
            assertFalse(listed(list, public, null), list)
            assertFalse(listed(list, public, readerToken), list)
        }
        assertFalse(listed("sharedWithMeRecipes", family, readerToken))
        assertTrue(readable(public, adminToken), "admins can still look at it")

        // And back.
        jdbc.update("UPDATE users SET suspended = false WHERE username = ?", name)
        assertEquals(200, api.status("{ me { username } }", spammerToken))
        assertTrue(readable(public, null))
        assertFalse(api.failed(api.graphql(login)))
    }
}

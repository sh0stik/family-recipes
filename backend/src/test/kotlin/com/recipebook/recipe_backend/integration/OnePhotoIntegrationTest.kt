package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A recipe has one photo: a second is refused, replacing is remove-then-add, older recipes with more keep working. */
class OnePhotoIntegrationTest : PhotoTestBase() {

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `a second photo is refused with PHOTO_LIMIT and the first stays`() {
        val token = token(user("one-limit"))
        val recipe = createRecipe("One", token)
        val first = upload(token)
        assertFalse(api.failed(addPhoto(recipe, first, token)))

        val link = addPhoto(recipe, "https://example.com/second.jpg", token)
        assertEquals("PHOTO_LIMIT", api.code(link), link)
        val upload = upload(token)
        assertEquals("PHOTO_LIMIT", api.code(addPhoto(recipe, upload, token)))
        // Adding the photo it already has is not a second one.
        assertFalse(api.failed(addPhoto(recipe, first, token)))
        assertEquals("""{"data":{"recipe":{"photoUrls":["$first"]}}}""", photos(recipe, token))
    }

    @Test
    fun `replacing removes the old photo first, and an old upload's file goes`() {
        val token = token(user("one-replace"))
        val recipe = createRecipe("Swap", token)
        val old = upload(token)
        addPhoto(recipe, old, token)

        // What the app's Replace does on save: remove, then add.
        assertFalse(api.failed(removePhoto(recipe, old, token)))
        val link = "https://example.com/new.jpg"
        assertFalse(api.failed(addPhoto(recipe, link, token)))
        assertTrue(photos(recipe, token).contains(link))
        assertFalse(Files.exists(fileOf(old)))

        // And back from a link to an upload.
        removePhoto(recipe, link, token)
        val again = upload(token)
        assertFalse(api.failed(addPhoto(recipe, again, token)))
        assertEquals("""{"data":{"recipe":{"photoUrls":["$again"]}}}""", photos(recipe, token))
    }

    @Test
    fun `a recipe from before the rule keeps its photos and can be trimmed to one`() {
        val token = token(user("one-legacy"))
        val recipe = createRecipe("Old", token)
        // Stored before the limit existed: two photos, written the way the old backend did.
        jdbc.update("INSERT INTO recipe_photos (recipe_id, url) VALUES (?::uuid, ?)", recipe, "https://example.com/a.jpg")
        jdbc.update("INSERT INTO recipe_photos (recipe_id, url) VALUES (?::uuid, ?)", recipe, "https://example.com/b.jpg")

        val shown = photos(recipe, token)
        assertTrue(shown.contains("a.jpg") && shown.contains("b.jpg"), shown)
        val listed = api.graphql("{ myRecipes { id photoUrls } }", token)
        assertTrue(listed.contains("a.jpg") && listed.contains("b.jpg"), listed)
        // Editing the text still works.
        assertFalse(api.failed(api.graphql("""mutation { updateRecipe(recipeId: "$recipe", name: "Old v2") { name } }""", token)))
        // A third is refused, and so is anything until only one is left.
        assertEquals("PHOTO_LIMIT", api.code(addPhoto(recipe, "https://example.com/c.jpg", token)))
        assertFalse(api.failed(removePhoto(recipe, "https://example.com/b.jpg", token)))
        assertEquals("""{"data":{"recipe":{"photoUrls":["https://example.com/a.jpg"]}}}""", photos(recipe, token))
        assertEquals("PHOTO_LIMIT", api.code(addPhoto(recipe, "https://example.com/c.jpg", token)))
    }
}

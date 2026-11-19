package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An uploaded photo goes on a recipe only by the hand of whoever uploaded it, or of someone who
 * can already see a recipe showing it (copying). Links to other websites work as before.
 */
class PhotoOwnershipIntegrationTest : PhotoTestBase() {

    @Test
    fun `the uploader may attach their own upload`() {
        val token = token(user("own-anna"))
        val recipe = createRecipe("Anna's", token)
        val url = upload(token)
        val response = addPhoto(recipe, url, token)
        assertFalse(api.failed(response), response)
        assertTrue(response.contains(url))
    }

    @Test
    fun `someone else's unattached upload is refused with PHOTO_NOT_YOURS`() {
        val anna = token(user("own-anna2"))
        val boris = token(user("own-boris"))
        val stolen = upload(anna)
        val borisRecipe = createRecipe("Boris'", boris)
        val response = addPhoto(borisRecipe, stolen, boris)
        assertEquals("PHOTO_NOT_YOURS", api.code(response), response)
        assertFalse(photos(borisRecipe, boris).contains(stolen))
    }

    @Test
    fun `a file nobody uploaded through the endpoint is refused`() {
        val token = token(user("own-guess"))
        Files.writeString(uploads.resolve("legacy.jpg"), "x")
        val recipe = createRecipe("Guess", token)
        assertEquals("PHOTO_NOT_YOURS", api.code(addPhoto(recipe, "/uploads/legacy.jpg", token)))
        assertEquals("PHOTO_NOT_YOURS", api.code(addPhoto(recipe, "/uploads/no-such-file.jpg", token)))
    }

    @Test
    fun `a photo on a recipe the caller can see may go on their copy`() {
        val anna = token(user("own-anna3"))
        val boris = token(user("own-boris3"))
        val shared = api.id(api.graphql("""mutation { createRecipe(name: "Family pie", visibility: FAMILY) { id } }""", anna))
        val url = upload(anna)
        addPhoto(shared, url, anna)

        // What the apps do to copy: a new recipe of Boris's own, then the original's photos.
        val copy = createRecipe("Family pie (copy)", boris)
        val response = addPhoto(copy, url, boris)
        assertFalse(api.failed(response), response)

        // Server-side copyRecipe keeps it too.
        val copied = api.graphql("""mutation { copyRecipe(recipeId: "$shared") { photoUrls } }""", boris)
        assertTrue(copied.contains(url), copied)
    }

    @Test
    fun `a photo on a recipe the caller cannot see stays out of reach`() {
        val anna = token(user("own-anna4"))
        val boris = token(user("own-boris4"))
        val private = createRecipe("Only me", anna)
        val url = upload(anna)
        addPhoto(private, url, anna)
        val borisRecipe = createRecipe("Boris tries", boris)
        assertEquals("PHOTO_NOT_YOURS", api.code(addPhoto(borisRecipe, url, boris)))
    }

    @Test
    fun `a second name for the same file is refused`() {
        val token = token(user("own-alias"))
        val recipe = createRecipe("Alias", token)
        val url = upload(token)
        assertEquals("INVALID_URL", api.code(addPhoto(recipe, "$url?v=2", token)))
        assertEquals("INVALID_URL", api.code(addPhoto(recipe, "/uploads/sub/" + url.removePrefix("/uploads/"), token)))
    }

    @Test
    fun `links to other websites work as before`() {
        val token = token(user("own-link"))
        val recipe = createRecipe("Linked", token)
        val response = addPhoto(recipe, "https://upload.wikimedia.org/wikipedia/commons/c/c9/Syrniki_with_fruits.jpg", token)
        assertFalse(api.failed(response), response)
        assertEquals("INVALID_URL", api.code(addPhoto(createRecipe("Bad", token), "javascript:alert(1)", token)))
    }
}

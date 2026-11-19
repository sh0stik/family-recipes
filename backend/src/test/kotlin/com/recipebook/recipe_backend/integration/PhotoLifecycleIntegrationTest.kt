package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An uploaded file lives exactly as long as some recipe shows it: removed from the last recipe,
 * deleted with the last recipe, or never attached for a day, it is deleted — and nothing outside
 * the uploads folder or still in use is ever touched.
 */
class PhotoLifecycleIntegrationTest : PhotoTestBase() {

    @Test
    fun `removing a photo from its only recipe deletes the file and its upload record`() {
        val token = token(user("life-remove"))
        val recipe = createRecipe("Pie", token)
        val url = upload(token)
        assertFalse(api.failed(addPhoto(recipe, url, token)))
        assertTrue(Files.exists(fileOf(url)))

        val response = removePhoto(recipe, url, token)
        assertFalse(api.failed(response), response)
        assertFalse(Files.exists(fileOf(url)), "the file outlived its last recipe")
        assertFalse(photoUploadRepository.existsById(url.removePrefix("/uploads/")))
    }

    @Test
    fun `a file a copy still shows stays until the copy lets go of it too`() {
        val token = token(user("life-copy"))
        val original = createRecipe("Soup", token)
        val url = upload(token)
        addPhoto(original, url, token)
        val copy = api.id(api.graphql("""mutation { copyRecipe(recipeId: "$original") { id } }""", token))
        assertTrue(photos(copy, token).contains(url))

        removePhoto(original, url, token)
        assertTrue(Files.exists(fileOf(url)), "deleted while the copy still shows it")
        removePhoto(copy, url, token)
        assertFalse(Files.exists(fileOf(url)))
    }

    @Test
    fun `deleting a recipe from the trash deletes its uploaded photo`() {
        val token = token(user("life-trash"))
        val recipe = createRecipe("Cake", token)
        val url = upload(token)
        addPhoto(recipe, url, token)

        api.graphql("""mutation { moveToTrash(recipeId: "$recipe") { id } }""", token)
        assertTrue(Files.exists(fileOf(url)), "a trashed recipe can still be restored with its photo")
        val response = api.graphql("""mutation { deletePermanently(recipeId: "$recipe") }""", token)
        assertTrue(response.contains("\"deletePermanently\":true"), response)
        assertFalse(Files.exists(fileOf(url)))
    }

    @Test
    fun `emptying the trash deletes the photos of the recipes in it`() {
        val token = token(user("life-empty"))
        val first = createRecipe("One", token)
        val second = createRecipe("Two", token)
        val a = upload(token)
        val b = upload(token)
        addPhoto(first, a, token)
        addPhoto(second, b, token)
        api.graphql("""mutation { moveToTrash(recipeId: "$first") { id } }""", token)
        api.graphql("""mutation { moveToTrash(recipeId: "$second") { id } }""", token)

        assertTrue(api.graphql("mutation { emptyTrash }", token).contains("\"emptyTrash\":true"))
        assertFalse(Files.exists(fileOf(a)))
        assertFalse(Files.exists(fileOf(b)))
    }

    @Test
    fun `removing a link photo touches no file`() {
        val token = token(user("life-link"))
        val recipe = createRecipe("Linked", token)
        val bystander = uploads.resolve("photo.jpg")
        Files.writeString(bystander, "x")
        val link = "https://example.com/photo.jpg"
        addPhoto(recipe, link, token)
        assertFalse(api.failed(removePhoto(recipe, link, token)))
        assertTrue(Files.exists(bystander))
    }

    @Test
    fun `the nightly clean-up removes day-old files no recipe uses, and nothing else`() {
        val token = token(user("life-orphans"))
        val recipe = createRecipe("Kept", token)
        val inUse = upload(token)
        addPhoto(recipe, inUse, token)
        val abandoned = upload(token)
        val fresh = upload(token)
        val outside = uploads.parent.resolve("outside-uploads.jpg")
        Files.writeString(outside, "x")
        val sub = Files.createDirectories(uploads.resolve("sub"))
        Files.writeString(sub.resolve("nested.jpg"), "x")

        val dayAgo = FileTime.from(Instant.now().minus(Duration.ofHours(25)))
        for (path in listOf(fileOf(inUse), fileOf(abandoned), outside, sub.resolve("nested.jpg"))) {
            Files.setLastModifiedTime(path, dayAgo)
        }

        val removed = photoFiles.removeAbandonedUploads()
        assertTrue(removed >= 1)
        assertFalse(Files.exists(fileOf(abandoned)), "an abandoned upload survived")
        assertFalse(photoUploadRepository.existsById(abandoned.removePrefix("/uploads/")))
        assertTrue(Files.exists(fileOf(inUse)), "a photo in use was removed")
        assertTrue(Files.exists(fileOf(fresh)), "an upload from the last day was removed")
        assertTrue(Files.exists(outside), "something outside the uploads folder was removed")
        assertTrue(Files.exists(sub.resolve("nested.jpg")), "a file in a subfolder was removed")
        Files.delete(outside)
    }

    @Test
    fun `a trashed recipe keeps its photo through the clean-up`() {
        val token = token(user("life-trash-kept"))
        val recipe = createRecipe("Waiting", token)
        val url = upload(token)
        addPhoto(recipe, url, token)
        api.graphql("""mutation { moveToTrash(recipeId: "$recipe") { id } }""", token)
        Files.setLastModifiedTime(fileOf(url), FileTime.from(Instant.now().minus(Duration.ofDays(3))))
        photoFiles.removeAbandonedUploads()
        assertTrue(Files.exists(fileOf(url)))
    }

    @Test
    fun `deleting an account deletes its uploaded photos and forgets its uploads`() {
        val owner = user("life-account")
        val token = token(owner)
        val recipe = createRecipe("Mine", token)
        val url = upload(token)
        addPhoto(recipe, url, token)

        accountDeletionService.deleteAccount(userRepository.findById(owner.id!!).orElseThrow())
        assertFalse(Files.exists(fileOf(url)))
        assertEquals(0, photoUploadRepository.findAll().count { it.uploaderId == owner.id })
    }
}

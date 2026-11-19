package recipe

import data.ApiException
import data.PhotoUploader
import data.UploadReply
import data.apiCode
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaSaveTest {
    /** Records every call, in order, in [calls]; answers from [refuse] (url -> error) or with success. */
    private class FakeApi(
        val calls: MutableList<String> = mutableListOf(),
        val refuse: Map<String, Throwable> = emptyMap(),
        val createResult: Result<String> = Result.success("r1"),
    ) : NewRecipeApi {
        private fun answer(call: String, url: String): Result<Boolean> {
            calls += call
            return refuse[url]?.let { Result.failure(it) } ?: Result.success(true)
        }
        override suspend fun addPhoto(recipeId: String, url: String) = answer("addPhoto $recipeId $url", url)
        override suspend fun removePhoto(recipeId: String, url: String) = answer("removePhoto $recipeId $url", url)
        override suspend fun addVideo(recipeId: String, url: String) = answer("addVideo $recipeId $url", url)
        override suspend fun removeVideo(recipeId: String, url: String) = answer("removeVideo $recipeId $url", url)
        override suspend fun createRecipe(form: NewRecipeForm): Result<String> {
            calls += "create ${form.name}"
            return createResult
        }
        override suspend fun addIngredient(recipeId: String, ingredient: PendingIngredient): Result<Boolean> {
            calls += "ingredient $recipeId ${ingredient.ingredientName}"
            return Result.success(true)
        }
    }

    private fun <T> run(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return outcome!!.getOrThrow()
    }

    /** An uploader whose "server" records the upload in the same call list and answers with [url]. */
    private fun uploader(calls: MutableList<String>, url: String = "/uploads/abc.jpg") = PhotoUploader(
        endpoint = "http://server/api/photos",
        currentToken = { "token" },
        onRejected = {},
        send = { _, _, _ -> calls += "upload"; UploadReply(200, """{"url":"$url"}""") },
    )

    // --- editing ---------------------------------------------------------------------------

    @Test
    fun replacingThePhotoRemovesTheOldOneBeforeAddingTheNewOne() {
        val api = FakeApi()
        val result = run {
            saveMedia(api, "r1", SavedMedia(photos = setOf("/uploads/old.jpg")), listOf("https://example.com/new.jpg"), emptyList())
        }
        assertEquals(listOf("removePhoto r1 /uploads/old.jpg", "addPhoto r1 https://example.com/new.jpg"), api.calls)
        assertEquals(setOf("https://example.com/new.jpg"), result.saved.photos)
        assertEquals(null, result.failure)
    }

    @Test
    fun nothingChangedSendsNothing() {
        val api = FakeApi()
        run { saveMedia(api, "r1", SavedMedia(setOf("/uploads/a.jpg"), setOf("https://v.example/1")), listOf("/uploads/a.jpg"), listOf("https://v.example/1")) }
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun aLegacyRecipeTrimmedToOnePhotoOnlyRemovesTheExtras() {
        val api = FakeApi()
        val saved = SavedMedia(photos = setOf("https://a.example/1.jpg", "https://a.example/2.jpg"))
        val slot = PhotoSlot(listOf("https://a.example/1.jpg", "https://a.example/2.jpg")).without("https://a.example/2.jpg")
        val result = run { saveMedia(api, "r1", saved, slot.photos, emptyList()) }
        assertEquals(listOf("removePhoto r1 https://a.example/2.jpg"), api.calls)
        assertEquals(setOf("https://a.example/1.jpg"), result.saved.photos)
    }

    @Test
    fun aRefusedLinkIsReportedAndDroppedWhileTheRestGoesThrough() {
        val api = FakeApi(refuse = mapOf("https://bad_link.example/x.jpg" to ApiException("INVALID_URL", "bad")))
        val result = run {
            saveMedia(api, "r1", SavedMedia(), listOf("https://bad_link.example/x.jpg"), listOf("https://v.example/1"))
        }
        assertEquals("INVALID_URL", result.failure?.apiCode)
        assertEquals(setOf("https://bad_link.example/x.jpg"), result.refusedPhotos)
        assertEquals(setOf("https://v.example/1"), result.saved.videos)
    }

    // --- creating ---------------------------------------------------------------------------

    @Test
    fun anUploadedPhotoIsAttachedRightAfterTheRecipeIsCreated() {
        val api = FakeApi()
        val slot = run {
            val picked = byteArrayOf(9)
            PhotoSlot().uploadStarted(picked).uploadFinished(uploader(api.calls).upload(picked))
        }
        val form = NewRecipeForm(
            name = "Pie", description = null,
            ingredients = listOf(PendingIngredient("i1", "Flour", 1.0, null, null)),
            photos = slot.photos,
            videos = listOf("https://v.example/1"),
        )
        val result = run { saveNewRecipe(api, form, NewRecipeProgress()) }
        assertIs<NewRecipeSaveResult.Saved>(result)
        assertEquals(
            listOf("upload", "create Pie", "ingredient r1 Flour", "addPhoto r1 /uploads/abc.jpg", "addVideo r1 https://v.example/1"),
            api.calls,
        )
    }

    @Test
    fun aLinkedPhotoIsAttachedRightAfterTheRecipeIsCreated() {
        val api = FakeApi()
        val slot = PhotoSlot().withLink("https://example.com/pie.jpg")!!
        val result = run { saveNewRecipe(api, NewRecipeForm("Pie", null, photos = slot.photos), NewRecipeProgress()) }
        assertIs<NewRecipeSaveResult.Saved>(result)
        assertEquals(listOf("create Pie", "addPhoto r1 https://example.com/pie.jpg"), api.calls)
    }

    @Test
    fun ifAttachingThePhotoFailsTheRecipeStillExistsAndSaveCarriesOn() {
        val refusal = ApiException("PHOTO_NOT_YOURS", "not yours")
        val failing = FakeApi(refuse = mapOf("/uploads/abc.jpg" to refusal))
        val form = NewRecipeForm("Pie", null, photos = listOf("/uploads/abc.jpg"))
        val first = run { saveNewRecipe(failing, form, NewRecipeProgress()) }
        assertIs<NewRecipeSaveResult.PartlySaved>(first)
        assertEquals("PHOTO_NOT_YOURS", first.error.apiCode)
        assertEquals("r1", first.progress.recipeId)
        assertEquals(emptySet(), first.progress.media.photos)

        // Save again: the recipe is not created twice, only the photo is tried again.
        val api = FakeApi()
        val second = run { saveNewRecipe(api, form, first.progress) }
        assertIs<NewRecipeSaveResult.Saved>(second)
        assertEquals(listOf("addPhoto r1 /uploads/abc.jpg"), api.calls)
    }

    @Test
    fun aFailedUploadLeavesNoPhotoToAttach() {
        val api = FakeApi()
        val failingUploader = PhotoUploader("http://server/api/photos", { "t" }, {}, send = { _, _, _ ->
            UploadReply(413, """{"code":"TOO_LARGE"}""")
        })
        val slot = run { PhotoSlot().uploadStarted(byteArrayOf(1)).uploadFinished(failingUploader.upload(byteArrayOf(1))) }
        assertEquals("TOO_LARGE", slot.error?.apiCode)
        run { saveNewRecipe(api, NewRecipeForm("Pie", null, photos = slot.photos), NewRecipeProgress()) }
        assertEquals(listOf("create Pie"), api.calls)
    }

    @Test
    fun aRefusedCreateMakesNothingElse() {
        val api = FakeApi(createResult = Result.failure(ApiException("TERMS_REQUIRED", "rules")))
        val result = run { saveNewRecipe(api, NewRecipeForm("Pie", null, photos = listOf("/uploads/abc.jpg")), NewRecipeProgress()) }
        assertIs<NewRecipeSaveResult.NotCreated>(result)
        assertEquals(listOf("create Pie"), api.calls)
    }
}

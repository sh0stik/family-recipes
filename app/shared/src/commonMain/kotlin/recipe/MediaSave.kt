package recipe

import data.ApiException
import data.Links
import data.apiCode
import model.Visibility

/**
 * The recipe's photo in the create and edit forms. One photo, uploaded or linked, as the server
 * allows. [photos] is what the recipe will have once saved: normally none or one; a recipe from
 * before the one-photo rule may arrive with more, shown as [extras] so they can be removed.
 */
data class PhotoSlot(
    val photos: List<String> = emptyList(),
    /** The prepared JPEG while it uploads, shown as the thumbnail under a spinner. */
    val uploading: ByteArray? = null,
    /** Why the last upload or pick failed, shown under the slot until the next try. */
    val error: Throwable? = null,
) {
    val photo: String? get() = photos.firstOrNull()
    val extras: List<String> get() = photos.drop(1)
    val isUploading: Boolean get() = uploading != null

    /** Uses [raw] as the photo, replacing whatever was there; null when it is not a web link. */
    fun withLink(raw: String): PhotoSlot? {
        val link = raw.trim()
        if (!Links.isWebLink(link)) return null
        return copy(photos = listOf(link), error = null)
    }

    fun uploadStarted(preview: ByteArray): PhotoSlot = copy(uploading = preview, error = null)

    /**
     * The upload's answer: its URL replaces the photo (and any extras), as a link would. A failed
     * upload leaves the photo that was there.
     */
    fun uploadFinished(result: Result<String>): PhotoSlot = result.fold(
        onSuccess = { copy(photos = listOf(it), uploading = null, error = null) },
        onFailure = { copy(uploading = null, error = it) },
    )

    /** The picker could not read the picture: the server's word for it, so the message matches. */
    fun unreadable(): PhotoSlot = copy(uploading = null, error = ApiException("NOT_AN_IMAGE", "The picked file could not be read"))

    fun without(url: String): PhotoSlot = copy(photos = photos - url)

    // ByteArray has no structural equality; the slot is compared by what it shows.
    override fun equals(other: Any?): Boolean =
        other is PhotoSlot && photos == other.photos && (uploading === other.uploading) && error == other.error

    override fun hashCode(): Int = photos.hashCode()
}

/** The calls that put photos and video links on a recipe. [recipeMediaApi] wires them to the repository. */
interface RecipeMediaApi {
    suspend fun addPhoto(recipeId: String, url: String): Result<Boolean>
    suspend fun removePhoto(recipeId: String, url: String): Result<Boolean>
    suspend fun addVideo(recipeId: String, url: String): Result<Boolean>
    suspend fun removeVideo(recipeId: String, url: String): Result<Boolean>
}

fun recipeMediaApi(repository: data.RecipeRepository): RecipeMediaApi = object : RecipeMediaApi {
    override suspend fun addPhoto(recipeId: String, url: String) = repository.addPhotoToRecipe(recipeId, url)
    override suspend fun removePhoto(recipeId: String, url: String) = repository.removePhotoFromRecipe(recipeId, url)
    override suspend fun addVideo(recipeId: String, url: String) = repository.addVideoLinkToRecipe(recipeId, url)
    override suspend fun removeVideo(recipeId: String, url: String) = repository.removeVideoLinkFromRecipe(recipeId, url)
}

/** What the server has of a recipe's photos and video links, as far as the form knows. */
data class SavedMedia(val photos: Set<String> = emptySet(), val videos: Set<String> = emptySet())

data class MediaSaveResult(
    val saved: SavedMedia,
    /** Links the server refused as links (INVALID_URL): sending them again would fail again. */
    val refusedPhotos: Set<String> = emptySet(),
    val refusedVideos: Set<String> = emptySet(),
    /** The first refusal, or null when everything went through. */
    val failure: Throwable? = null,
)

/**
 * Brings the recipe's photos and video links on the server in line with the form. Removals go
 * first, so replacing the one photo never trips the server's one-photo limit (PHOTO_LIMIT). Every
 * call is tried even when an earlier one failed; what went through is returned in [MediaSaveResult.saved],
 * so a second Save does not repeat it.
 */
suspend fun saveMedia(
    api: RecipeMediaApi,
    recipeId: String,
    saved: SavedMedia,
    photos: List<String>,
    videos: List<String>,
): MediaSaveResult {
    var failure: Throwable? = null
    fun failed(e: Throwable) { if (failure == null) failure = e }

    var savedPhotos = saved.photos
    val refusedPhotos = mutableSetOf<String>()
    for (url in savedPhotos - photos.toSet()) {
        api.removePhoto(recipeId, url).onSuccess { savedPhotos = savedPhotos - url }.onFailure(::failed)
    }
    for (url in photos.distinct().filter { it !in savedPhotos }) {
        api.addPhoto(recipeId, url)
            .onSuccess { savedPhotos = savedPhotos + url }
            .onFailure { e ->
                failed(e)
                if (e.apiCode == "INVALID_URL") refusedPhotos += url
            }
    }

    var savedVideos = saved.videos
    val refusedVideos = mutableSetOf<String>()
    for (url in savedVideos - videos.toSet()) {
        api.removeVideo(recipeId, url).onSuccess { savedVideos = savedVideos - url }.onFailure(::failed)
    }
    for (url in videos.distinct().filter { it !in savedVideos }) {
        api.addVideo(recipeId, url)
            .onSuccess { savedVideos = savedVideos + url }
            .onFailure { e ->
                failed(e)
                if (e.apiCode == "INVALID_URL") refusedVideos += url
            }
    }
    return MediaSaveResult(SavedMedia(savedPhotos, savedVideos), refusedPhotos, refusedVideos, failure)
}

/** The create form, as it is when Save is pressed. */
data class NewRecipeForm(
    val name: String,
    val description: String?,
    val familyCollection: Boolean = false,
    val visibility: Visibility = Visibility.ONLY_ME,
    val ingredients: List<PendingIngredient> = emptyList(),
    val photos: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
)

/** How far an earlier Save got, so pressing Save again carries on instead of starting over. */
data class NewRecipeProgress(
    val recipeId: String? = null,
    val addedIngredientIds: Set<String> = emptySet(),
    val media: SavedMedia = SavedMedia(),
)

/** The calls creating a recipe makes. [newRecipeApi] wires them to the repository. */
interface NewRecipeApi : RecipeMediaApi {
    /** The new recipe's id. */
    suspend fun createRecipe(form: NewRecipeForm): Result<String>
    suspend fun addIngredient(recipeId: String, ingredient: PendingIngredient): Result<Boolean>
}

fun newRecipeApi(repository: data.RecipeRepository): NewRecipeApi = object : NewRecipeApi, RecipeMediaApi by recipeMediaApi(repository) {
    override suspend fun createRecipe(form: NewRecipeForm) = repository.createRecipe(
        form.name, form.description,
        familyCollection = form.familyCollection,
        visibility = form.visibility,
    ).map { it.id }

    override suspend fun addIngredient(recipeId: String, ingredient: PendingIngredient) = repository.addIngredientToRecipe(
        recipeId = recipeId,
        ingredientId = ingredient.ingredientId,
        quantity = ingredient.quantity,
        unitId = ingredient.unitId,
    )
}

sealed interface NewRecipeSaveResult {
    data class Saved(val progress: NewRecipeProgress) : NewRecipeSaveResult

    /** createRecipe itself was refused: nothing exists yet. TERMS_REQUIRED among others. */
    data class NotCreated(val error: Throwable) : NewRecipeSaveResult

    /**
     * The recipe exists, but something after it was refused (an ingredient, the photo, a video
     * link). The form stays open with [progress]; an uploaded photo that never got attached is
     * removed by the server's nightly clean-up.
     */
    data class PartlySaved(
        val progress: NewRecipeProgress,
        val error: Throwable,
        val refusedPhotos: Set<String>,
        val refusedVideos: Set<String>,
    ) : NewRecipeSaveResult
}

/**
 * Creates the recipe, then adds its ingredients, its photo and its video links, in that order.
 * The visibility goes with the create call, so a refusal there (content rules, a blocked word)
 * leaves nothing half made. The photo was uploaded while the form was open; it is attached here,
 * right after the recipe exists, exactly like a link.
 */
suspend fun saveNewRecipe(api: NewRecipeApi, form: NewRecipeForm, progress: NewRecipeProgress): NewRecipeSaveResult {
    val id = progress.recipeId ?: api.createRecipe(form).getOrElse { return NewRecipeSaveResult.NotCreated(it) }

    var failure: Throwable? = null
    var added = progress.addedIngredientIds
    for (ingredient in form.ingredients.filter { it.ingredientId !in added }) {
        api.addIngredient(id, ingredient)
            .onSuccess { added = added + ingredient.ingredientId }
            .onFailure { if (failure == null) failure = it }
    }
    val media = saveMedia(api, id, progress.media, form.photos, form.videos)
    val now = NewRecipeProgress(id, added, media.saved)
    val problem = failure ?: media.failure
    return if (problem == null) NewRecipeSaveResult.Saved(now)
    else NewRecipeSaveResult.PartlySaved(now, problem, media.refusedPhotos, media.refusedVideos)
}

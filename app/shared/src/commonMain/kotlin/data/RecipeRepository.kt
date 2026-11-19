package data

import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.cache.normalized.FetchPolicy
import com.apollographql.apollo3.cache.normalized.fetchPolicy
import data.fragment.RecipeCard
import data.fragment.RecipeListCard
import data.type.ReportReason
import model.*

class RecipeRepository(private val networkModule: NetworkModule) {

    suspend fun getRecipes(): Result<List<Recipe>> {
        return try {
            val response = networkModule.apolloClient.query(GetRecipesQuery())
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch recipes"))
            } else {
                val recipes = response.data?.recipes?.mapNotNull { it?.recipeListCard?.toRecipe() } ?: emptyList()
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyRecipes(): Result<List<Recipe>> {
        return try {
            val response = networkModule.apolloClient.query(MyRecipesQuery())
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch your recipes"))
            } else {
                val recipes = response.data?.myRecipes?.mapNotNull { it?.recipeListCard?.toRecipe() } ?: emptyList()
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSharedWithMeRecipes(): Result<List<Recipe>> {
        return try {
            val response = networkModule.apolloClient.query(SharedWithMeRecipesQuery())
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch shared recipes"))
            } else {
                val recipes = response.data?.sharedWithMeRecipes?.mapNotNull { it?.recipeListCard?.toRecipe() } ?: emptyList()
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPublicRecipes(): Result<List<Recipe>> {
        return try {
            val response = networkModule.apolloClient.query(GetPublicRecipesQuery())
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch public recipes"))
            } else {
                val recipes = response.data?.publicRecipes?.mapNotNull { it?.recipeListCard?.toRecipe() } ?: emptyList()
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipe(id: String): Result<Recipe?> {
        return try {
            val response = networkModule.apolloClient.query(GetRecipeQuery(id))
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch recipe"))
            } else {
                Result.success(response.data?.recipe?.toRecipe())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchRecipes(keyword: String? = null, categoryId: String? = null, ingredientName: String? = null): Result<List<Recipe>> {
        return try {
            val response = networkModule.apolloClient.query(
                SearchRecipesQuery(
                    keyword = Optional.presentIfNotNull(keyword),
                    categoryId = Optional.presentIfNotNull(categoryId),
                    ingredientName = Optional.presentIfNotNull(ingredientName),
                )
            ).fetchPolicy(FetchPolicy.NetworkFirst).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Search failed"))
            } else {
                val recipes = response.data?.searchRecipes?.mapNotNull { it?.recipeListCard?.toRecipe() } ?: emptyList()
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRecipe(
        name: String,
        description: String?,
        familyCollection: Boolean = false,
        visibility: Visibility = Visibility.ONLY_ME,
    ): Result<Recipe> {
        return try {
            val response = networkModule.apolloClient.mutation(
                CreateRecipeMutation(
                    name,
                    Optional.presentIfNotNull(description),
                    // Sent only when set, so a non-admin never even asks.
                    if (familyCollection) Optional.present(true) else Optional.absent(),
                    Optional.present(visibility.toApi()),
                )
            ).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to create recipe"))
            } else {
                val r = response.data?.createRecipe ?: throw Exception("No data")
                Result.success(Recipe(
                    id = r.id,
                    title = r.name,
                    description = r.description ?: "",
                    visibility = r.visibility.toModel(),
                    createdAt = r.createdAt,
                    canEdit = true,
                    canManage = true,
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * [visibility] is for whoever manages the recipe; an editor leaves it null, since the server
     * answers OWNER_ONLY to an editor who tries to change it.
     */
    suspend fun updateRecipe(recipeId: String, name: String? = null, description: String? = null, visibility: Visibility? = null): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                UpdateRecipeMutation(
                    recipeId = recipeId,
                    name = Optional.presentIfNotNull(name),
                    description = Optional.presentIfNotNull(description),
                    visibility = Optional.presentIfNotNull(visibility?.toApi()),
                )
            ).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addIngredientToRecipe(recipeId: String, ingredientId: String, quantity: Double, unitId: String? = null): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                AddIngredientToRecipeMutation(
                    recipeId = recipeId,
                    ingredientId = ingredientId,
                    quantity = quantity,
                    unitId = Optional.presentIfNotNull(unitId),
                )
            ).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeIngredientFromRecipe(recipeId: String, ingredientId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                RemoveIngredientFromRecipeMutation(recipeId, ingredientId)
            ).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCategoryToRecipe(recipeId: String, categoryId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                AddCategoryToRecipeMutation(recipeId, categoryId)
            ).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeCategoryFromRecipe(recipeId: String, categoryId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                RemoveCategoryFromRecipeMutation(recipeId, categoryId)
            ).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveToTrash(recipeId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(MoveToTrashMutation(recipeId)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromTrash(recipeId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(RestoreFromTrashMutation(recipeId)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanently(recipeId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(DeletePermanentlyMutation(recipeId)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun emptyTrash(): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(EmptyTrashMutation()).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrashedRecipes(): Result<List<Recipe>> {
        return try {
            val response = networkModule.apolloClient.query(GetTrashedRecipesQuery()).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch trashed recipes"))
            } else {
                val recipes = response.data?.trashedRecipes?.mapNotNull { it?.let { r ->
                    Recipe(
                        id = r.id,
                        title = r.name,
                        description = r.description ?: "",
                        isTrashed = true,
                        createdAt = r.createdAt,
                        ownerName = r.user.username,
                    )
                }} ?: emptyList()
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyRecipe(recipeId: String): Result<String> {
        return try {
            val response = networkModule.apolloClient.mutation(CopyRecipeMutation(recipeId)).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Copy failed"))
            } else {
                Result.success(response.data!!.copyRecipe.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Uploads a prepared JPEG (data/PhotoUpload.kt); the URL it returns goes on a recipe with [addPhotoToRecipe]. */
    suspend fun uploadPhoto(jpeg: ByteArray): Result<String> = networkModule.photoUploader.upload(jpeg)

    suspend fun addPhotoToRecipe(recipeId: String, url: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(AddPhotoToRecipeMutation(recipeId, url)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removePhotoFromRecipe(recipeId: String, url: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(RemovePhotoFromRecipeMutation(recipeId, url)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addVideoLinkToRecipe(recipeId: String, url: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(AddVideoLinkToRecipeMutation(recipeId, url)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeVideoLinkFromRecipe(recipeId: String, url: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(RemoveVideoLinkFromRecipeMutation(recipeId, url)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUsers(): Result<List<model.User>> {
        return try {
            // NetworkFirst so a relative who registered after launch shows up in the share dialog.
            val response = networkModule.apolloClient.query(GetUsersQuery())
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed to fetch users"))
            } else {
                val users = response.data?.users?.mapNotNull { it?.let { u ->
                    model.User(id = u.id, username = u.username)
                }} ?: emptyList()
                Result.success(users)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareRecipe(recipeId: String, userId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(ShareRecipeMutation(recipeId, userId)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unshareRecipe(recipeId: String, userId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(UnshareRecipeMutation(recipeId, userId)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addNote(recipeId: String, note: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(AddNoteMutation(recipeId, note)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(DeleteNoteMutation(noteId)).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptTerms(): Result<Boolean> = try {
        networkModule.apolloClient.mutation(AcceptTermsMutation()).execute()
            .toResult("Request failed") { true }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reportRecipe(recipeId: String, reason: ReportReason, comment: String?): Result<Boolean> = try {
        networkModule.apolloClient.mutation(
            ReportRecipeMutation(recipeId, reason, Optional.presentIfNotNull(comment?.trim()?.ifBlank { null }))
        ).execute().toResult("Request failed") { true }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reportUser(userId: String, reason: ReportReason, comment: String?): Result<Boolean> = try {
        networkModule.apolloClient.mutation(
            ReportUserMutation(userId, reason, Optional.presentIfNotNull(comment?.trim()?.ifBlank { null }))
        ).execute().toResult("Request failed") { true }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun blockUser(userId: String): Result<Boolean> = try {
        networkModule.apolloClient.mutation(BlockUserMutation(userId)).execute()
            .toResult("Request failed") { true }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun unblockUser(userId: String): Result<Boolean> = try {
        networkModule.apolloClient.mutation(UnblockUserMutation(userId)).execute()
            .toResult("Request failed") { true }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getBlockedUsers(): Result<List<model.User>> = try {
        // Network only: this list is edited from this very screen, and a cached copy would show
        // someone who was just unblocked.
        networkModule.apolloClient.query(GetBlockedUsersQuery())
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
            .toResult("Failed to fetch blocked users") { d ->
                d.blockedUsers.map { model.User(id = it.id, username = it.username) }
            }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Admins only: takes any recipe out of view (it becomes Only me) and tells its owner why. */
    suspend fun adminUnpublishRecipe(recipeId: String, reason: String): Result<Boolean> = try {
        networkModule.apolloClient.mutation(AdminUnpublishRecipeMutation(recipeId, reason.trim())).execute()
            .toResult("Request failed") { true }
    } catch (e: Exception) {
        Result.failure(e)
    }

    data class UserProfile(
        val id: String,
        val username: String,
        val email: String,
        val preferredLocale: String,
        val isAdmin: Boolean = false,
    )

    suspend fun getMe(): Result<UserProfile> {
        return try {
            // NetworkFirst, not the client-wide CacheFirst: this is re-read straight after the
            // profile is edited, and the normalized cache is not keyed by id, so the mutation's
            // returned User does not update the cached `me`. CacheFirst therefore returned the old
            // preferredLocale and the language only changed on next launch. Falls back to cache
            // when offline.
            val response = networkModule.apolloClient.query(GetMeQuery())
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed"))
            } else {
                val me = response.data!!.me
                Result.success(UserProfile(
                    id = me.id,
                    username = me.username,
                    email = me.email ?: "",
                    preferredLocale = me.preferredLocale,
                    isAdmin = me.isAdmin,
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUsername(newUsername: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                UpdateUsernameMutation(newUsername)
            ).execute()
            response.toResult("Request failed") { true }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createIngredient(
        name: String,
        caloriesPer100g: Double = 0.0,
        proteinPer100g: Double = 0.0,
        fatPer100g: Double = 0.0,
        carbsPer100g: Double = 0.0,
    ): Result<Ingredient> {
        return try {
            val response = networkModule.apolloClient.mutation(
                CreateIngredientMutation(name, caloriesPer100g, proteinPer100g, fatPer100g, carbsPer100g)
            ).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed"))
            } else {
                val i = response.data!!.createIngredient
                Result.success(Ingredient(
                    id = i.id,
                    name = i.name,
                    caloriesPer100g = i.caloriesPer100g,
                    proteinPer100g = i.proteinPer100g,
                    fatPer100g = i.fatPer100g,
                    carbsPer100g = i.carbsPer100g,
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLocale(locale: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                UpdatePreferredLocaleMutation(locale)
            ).execute()
            // Was `response.toResult("Request failed") { true }`, which reported success even when the
            // server rejected the mutation — the profile screen then showed a tick for a save that
            // never happened.
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed"))
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllCategories(): Result<List<model.Category>> {
        return try {
            val response = networkModule.apolloClient.query(GetAllCategoriesQuery()).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed"))
            } else {
                val categories = response.data?.allCategories?.mapNotNull { it?.let { c ->
                    model.Category(
                        id = c.id,
                        name = c.name,
                        parentCategory = c.parentCategory?.let { p ->
                            model.Category(id = p.id, name = p.name)
                        }
                    )
                }} ?: emptyList()
                Result.success(categories)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllIngredients(): Result<List<Ingredient>> {
        return try {
            val response = networkModule.apolloClient.query(GetAllIngredientsQuery()).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed"))
            } else {
                val ingredients = response.data?.allIngredients?.mapNotNull { it?.let { i ->
                    Ingredient(
                        id = i.id,
                        name = i.name,
                        caloriesPer100g = i.caloriesPer100g,
                        proteinPer100g = i.proteinPer100g,
                        fatPer100g = i.fatPer100g,
                        carbsPer100g = i.carbsPer100g,
                    )
                }} ?: emptyList()
                Result.success(ingredients)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUnits(): Result<List<MeasurementUnit>> {
        return try {
            val response = networkModule.apolloClient.query(GetAllUnitsQuery()).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Failed"))
            } else {
                val units = response.data?.allUnits?.mapNotNull { it?.let { u ->
                    MeasurementUnit(
                        id = u.id,
                        name = u.name,
                        abbreviation = u.abbreviation,
                        type = u.type.name,
                    )
                }} ?: emptyList()
                Result.success(units)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Mapper for the thin list fragment. Ingredients, nutrition and video links are absent by design —
// the list does not render them, and fetching them for every recipe was the bulk of the load time.
// They are populated when a recipe is opened and GetRecipe returns the full RecipeCard.
private fun RecipeListCard.toRecipe(): Recipe {
    return Recipe(
        id = this.id,
        title = this.name,
        description = this.description ?: "",
        visibility = this.visibility.toModel(),
        isTrashed = this.isTrashed ?: false,
        canEdit = this.canEdit,
        canManage = this.canManage,
        removedByAdmin = this.removedByAdmin,
        removalReason = this.removalReason,
        ownerId = this.user.id,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        ownerName = this.user.username,
        ownerLocale = this.user.preferredLocale,
        categories = this.categories?.mapNotNull { it?.let { c ->
            model.Category(id = c.id, name = c.name)
        }} ?: emptyList(),
        photoUrls = this.photoUrls?.filterNotNull() ?: emptyList(),
    )
}

// Single mapper for the shared RecipeCard fragment — used by recipe detail, which does display
// ingredients, nutrition and links.
private fun RecipeCard.toRecipe(): Recipe {
    return Recipe(
        id = this.id,
        title = this.name,
        description = this.description ?: "",
        visibility = this.visibility.toModel(),
        isTrashed = this.isTrashed ?: false,
        canEdit = this.canEdit,
        canManage = this.canManage,
        removedByAdmin = this.removedByAdmin,
        removalReason = this.removalReason,
        ownerId = this.user.id,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        ownerName = this.user.username,
        ownerLocale = this.user.preferredLocale,
        ingredients = this.ingredients?.mapNotNull { it?.let { ri ->
            RecipeIngredient(
                id = ri.id,
                quantity = ri.quantity,
                unitName = ri.unit?.name,
                unitAbbreviation = ri.unit?.abbreviation,
                ingredientId = ri.ingredient.id,
                ingredientName = ri.ingredient.name,
            )
        }} ?: emptyList(),
        categories = this.categories?.mapNotNull { it?.let { c ->
            model.Category(id = c.id, name = c.name)
        }} ?: emptyList(),
        photoUrls = this.photoUrls?.filterNotNull() ?: emptyList(),
        videoLinks = this.videoLinks?.filterNotNull() ?: emptyList(),
        nutrition = this.nutrition?.let {
            NutritionInfo(
                caloriesPer100g = it.caloriesPer100g,
                proteinPer100g = it.proteinPer100g,
                fatPer100g = it.fatPer100g,
                carbsPer100g = it.carbsPer100g,
                totalWeight = it.totalWeight,
            )
        },
    )
}

// Detail mapper: the shared card fields plus detail-only notes/sharedWith.
private fun GetRecipeQuery.Recipe.toRecipe(): Recipe {
    return this.recipeCard.toRecipe().copy(
        notes = this.notes?.mapNotNull { it?.let { n ->
            RecipeNote(
                id = n.id,
                note = n.note,
                createdAt = n.createdAt,
                userName = n.user.username,
            )
        }} ?: emptyList(),
        sharedWith = this.sharedWith?.mapNotNull { it?.let { u ->
            User(id = u.id, username = u.username)
        }} ?: emptyList(),
    )
}

private fun Visibility.toApi(): data.type.Visibility = when (this) {
    Visibility.ONLY_ME -> data.type.Visibility.ONLY_ME
    Visibility.FAMILY -> data.type.Visibility.FAMILY
    Visibility.PUBLIC -> data.type.Visibility.PUBLIC
}

// An unknown level (a server newer than this app) is treated as the narrowest one.
private fun data.type.Visibility.toModel(): Visibility = when (this) {
    data.type.Visibility.FAMILY -> Visibility.FAMILY
    data.type.Visibility.PUBLIC -> Visibility.PUBLIC
    else -> Visibility.ONLY_ME
}

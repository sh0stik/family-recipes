package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.moderation.ContentReportRepository
import com.recipebook.recipe_backend.moderation.UserBlockRepository
import com.recipebook.recipe_backend.recipe.Recipe
import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.user.User
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The facts [AdminService] needs that are not on the recipe or the user in front of it.
 *
 * An interface so AdminService stays a plain class that tests build with two strings: its default,
 * [Direct], answers from the objects themselves. The running application uses [StoredAccessLookup],
 * which asks the database once per request instead of once per recipe.
 */
interface AccessLookup {
    /** Whether [user] is one of the people [recipe]'s owner lets edit it. */
    fun isEditor(recipe: Recipe, user: User): Boolean

    /** The recipes [user] reported. They are left out of that person's own lists and search. */
    fun reportedRecipeIds(user: User): Set<UUID>

    /** The people [user] blocked. */
    fun blockedBy(user: User): Set<UUID>

    /** The people who blocked [user]. */
    fun blockersOf(user: User): Set<UUID>

    object Direct : AccessLookup {
        override fun isEditor(recipe: Recipe, user: User): Boolean = recipe.sharedWith.any { it.id == user.id }
        override fun reportedRecipeIds(user: User): Set<UUID> = emptySet()
        override fun blockedBy(user: User): Set<UUID> = emptySet()
        override fun blockersOf(user: User): Set<UUID> = emptySet()
    }
}

@Component
class StoredAccessLookup(
    private val recipeRepository: RecipeRepository,
    private val reportRepository: ContentReportRepository,
    private val blockRepository: UserBlockRepository,
    private val requestCache: RequestCache,
) : AccessLookup {

    // `recipe.sharedWith` is lazy: reading it for every recipe in a list is one query per recipe.
    override fun isEditor(recipe: Recipe, user: User): Boolean {
        val userId = user.id ?: return false
        val recipeId = recipe.id ?: return AccessLookup.Direct.isEditor(recipe, user)
        return recipeId in editedRecipeIds(userId)
    }

    override fun reportedRecipeIds(user: User): Set<UUID> {
        val userId = user.id ?: return emptySet()
        return requestCache.get("reportedRecipes:$userId") { reportRepository.findReportedRecipeIds(userId).toSet() }
    }

    override fun blockedBy(user: User): Set<UUID> {
        val userId = user.id ?: return emptySet()
        return requestCache.get("blockedBy:$userId") { blockRepository.findBlockedIds(userId).toSet() }
    }

    override fun blockersOf(user: User): Set<UUID> {
        val userId = user.id ?: return emptySet()
        return requestCache.get("blockersOf:$userId") { blockRepository.findBlockerIds(userId).toSet() }
    }

    private fun editedRecipeIds(userId: UUID): Set<UUID> =
        requestCache.get("editedRecipes:$userId") { recipeRepository.findEditedRecipeIds(userId).toSet() }
}

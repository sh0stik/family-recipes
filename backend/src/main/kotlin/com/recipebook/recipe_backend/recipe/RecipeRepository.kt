package com.recipebook.recipe_backend.recipe

import com.recipebook.recipe_backend.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RecipeRepository : JpaRepository<Recipe, UUID>{
    /**
     * Recipes whose name contains [keyword] or that use an ingredient whose name contains
     * [ingredientName], ignoring case; neither given means no text filter. Each text branch counts
     * only when its own argument is given: unguarded, an absent one became '%%' and matched every
     * recipe.
     */
    @Query("""
        SELECT DISTINCT r FROM Recipe r
        JOIN r.user u
        LEFT JOIN r.categories c
        LEFT JOIN r.sharedWith sw
        LEFT JOIN r.ingredients ri
        LEFT JOIN ri.ingredient i
        WHERE (
            (CAST(:keyword AS string) IS NULL AND CAST(:ingredientName AS string) IS NULL)
            OR (CAST(:keyword AS string) IS NOT NULL
                AND LOWER(r.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
            OR (CAST(:ingredientName AS string) IS NOT NULL
                AND LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:ingredientName AS string), '%')))
        )
        AND (:categoryId IS NULL OR c.id = :categoryId)
        AND r.isTrashed = false
        AND (
            r.visibility = com.recipebook.recipe_backend.recipe.Visibility.PUBLIC
            OR (:signedIn = true AND r.visibility = com.recipebook.recipe_backend.recipe.Visibility.FAMILY)
            OR r.user = :user
            OR sw = :user
        )
    """)
    fun searchRecipes(
        @Param("keyword") keyword: String?,
        @Param("categoryId") categoryId: UUID?,
        @Param("ingredientName") ingredientName: String?,
        @Param("user") user: User?,
        @Param("signedIn") signedIn: Boolean,
    ): List<Recipe>

    /** Everything a signed-in [user] may list: public and family recipes, their own, and ones they edit. */
    @Query("""
        SELECT DISTINCT r FROM Recipe r
        LEFT JOIN r.sharedWith sw
        WHERE r.isTrashed = false
        AND (r.visibility <> com.recipebook.recipe_backend.recipe.Visibility.ONLY_ME OR r.user = :user OR sw = :user)
    """)
    fun findVisibleRecipes(@Param("user") user: User): List<Recipe>

    @Query("""
        SELECT r FROM Recipe r
        WHERE r.isTrashed = true
        AND r.user = :user
    """)
    fun findTrashedRecipes(@Param("user") user: User): List<Recipe>

    @Query("""
        SELECT r FROM Recipe r
        WHERE r.isTrashed = false
        AND r.visibility = com.recipebook.recipe_backend.recipe.Visibility.PUBLIC
    """)
    fun findPublicRecipes(): List<Recipe>

    @Query("""
        SELECT r FROM Recipe r
        WHERE r.isTrashed = false
        AND r.user = :user
    """)
    fun findMyRecipes(@Param("user") user: User): List<Recipe>

    /**
     * The "Shared with me" list: other people's Family recipes, plus recipes [user] was made an
     * editor of whatever their visibility. Never their own.
     */
    @Query("""
        SELECT DISTINCT r FROM Recipe r
        LEFT JOIN r.sharedWith sw
        WHERE r.isTrashed = false
        AND r.user <> :user
        AND (r.visibility = com.recipebook.recipe_backend.recipe.Visibility.FAMILY OR sw = :user)
    """)
    fun findSharedWith(@Param("user") user: User): List<Recipe>

    /** Every recipe [user] owns, trashed or not — what account deletion removes. */
    fun findAllByUser(user: User): List<Recipe>

    /** The recipes [userId] is an editor of (a row in `recipe_shared_users`). */
    @Query(value = "SELECT recipe_id FROM recipe_shared_users WHERE user_id = :userId", nativeQuery = true)
    fun findEditedRecipeIds(@Param("userId") userId: UUID): List<UUID>

    /** Takes [editorId] off the editor list of every recipe [ownerId] owns. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "DELETE FROM recipe_shared_users WHERE user_id = :editorId AND recipe_id IN (SELECT id FROM recipes WHERE user_id = :ownerId)",
        nativeQuery = true
    )
    fun removeEditorFromRecipesOf(@Param("editorId") editorId: UUID, @Param("ownerId") ownerId: UUID): Int

    /** Removes [userId] from the editor list of every recipe. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM recipe_shared_users WHERE user_id = :userId", nativeQuery = true)
    fun removeFromAllShares(@Param("userId") userId: UUID): Int

    /** How many recipes still show [url] — a copied recipe shares its original's photo URLs. */
    @Query("SELECT COUNT(r) FROM Recipe r JOIN r.photoUrls p WHERE p = :url")
    fun countByPhotoUrl(@Param("url") url: String): Long

    /** The recipes showing photo [url], trashed ones included. */
    @Query("SELECT DISTINCT r FROM Recipe r JOIN r.photoUrls p WHERE p = :url")
    fun findAllByPhotoUrl(@Param("url") url: String): List<Recipe>

    /**
     * Every photo URL on any recipe, trashed ones included, that could point into the uploads
     * folder (photo/PhotoFiles decides which actually do).
     */
    @Query(value = "SELECT DISTINCT url FROM recipe_photos WHERE url LIKE '%/uploads/%'", nativeQuery = true)
    fun findUploadPhotoUrls(): List<String>
}

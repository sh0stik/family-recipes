package com.recipebook.recipe_backend.recipe

/**
 * Who may read a recipe (security/AdminService.canRead has the full rule).
 *
 * Replaces the old public/private flag. `recipes.is_public` is still written, always equal to
 * "visibility is PUBLIC", so the previous backend image keeps working if a deploy is rolled back.
 */
enum class Visibility {
    /** The owner, the people the owner lets edit it, and admins for family-collection recipes. */
    ONLY_ME,

    /** Any signed-in user. */
    FAMILY,

    /** Anyone, signed in or not, in the apps and on the website. */
    PUBLIC;

    /** Whether people other than the owner and editors can read it. */
    val isShared: Boolean get() = this != ONLY_ME

    /** Whether [other] opens the recipe to more people than this level does. */
    fun isNarrowerThan(other: Visibility): Boolean = ordinal < other.ordinal
}

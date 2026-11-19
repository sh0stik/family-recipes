package recipeslist

import model.Recipe
import model.Visibility

/** The three sections of the main list, in the order they are shown. */
data class RecipeSections(
    val mine: List<Recipe> = emptyList(),
    val shared: List<Recipe> = emptyList(),
    val public: List<Recipe> = emptyList(),
) {
    val isEmpty: Boolean get() = mine.isEmpty() && shared.isEmpty() && public.isEmpty()

    /** The recipes of one group, for the chip that is selected. */
    fun of(group: RecipeGroup): List<Recipe> = when (group) {
        RecipeGroup.MINE -> mine
        RecipeGroup.SHARED -> shared
        RecipeGroup.PUBLIC -> public
    }
}

/** The groups the main list can show, one at a time, as chips in this order. */
enum class RecipeGroup {
    MINE, SHARED, PUBLIC;

    companion object {
        const val SETTING_KEY = "list_group"

        /** Mine unless a saved name says otherwise (unknown or missing falls back to Mine). */
        fun fromSaved(name: String?): RecipeGroup = entries.firstOrNull { it.name == name } ?: MINE

        /** Signed out there is only Public, whatever was chosen while signed in. */
        fun effective(selected: RecipeGroup, isLoggedIn: Boolean): RecipeGroup =
            if (isLoggedIn) selected else PUBLIC
    }
}

/**
 * Puts the three server lists (myRecipes, sharedWithMeRecipes, publicRecipes) into sections.
 * Mine and Public are exactly what the server returned, so your own Public recipes show in both.
 * Shared drops anything already in Mine. Each list shows a recipe once (de-duplicated by id).
 */
fun sectionsOf(mine: List<Recipe>, shared: List<Recipe>, public: List<Recipe>): RecipeSections {
    val m = mine.distinctBy { it.id }
    val mineIds = m.mapTo(HashSet()) { it.id }
    val s = shared.filter { it.id !in mineIds }.distinctBy { it.id }
    val p = public.distinctBy { it.id }
    return RecipeSections(m, s, p)
}

/**
 * Sorts search results (one list covering everything the user may see) into the same sections.
 * Mine: you own it. Shared: someone else's Family recipe or one you can edit. Public: its
 * visibility is Public, yours included, so a recipe can be in two sections. Each section shows a
 * recipe once. Signed out there is no "me", and everything is Public.
 */
fun classifySearch(results: List<Recipe>, currentUsername: String): RecipeSections {
    val unique = results.distinctBy { it.id }
    if (currentUsername.isBlank()) return RecipeSections(public = unique)
    val mine = unique.filter { it.ownerName == currentUsername }
    val shared = unique.filter { it.ownerName != currentUsername && (it.canEdit || it.visibility == Visibility.FAMILY) }
    val public = unique.filter { it.visibility == Visibility.PUBLIC }
    return RecipeSections(mine, shared, public)
}

package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.recipe.Recipe
import com.recipebook.recipe_backend.recipe.Visibility
import com.recipebook.recipe_backend.user.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Who is an admin, and what that allows. The single place for both, so no controller grows its
 * own idea of "owner or admin".
 *
 * Admins are listed by username in RECIPE_ADMINS (comma separated) rather than stored in the
 * database: nothing in the app can grant it, and changing it is an edit to the server's `.env`
 * plus a restart. A listed username that does not exist yet is reserved — sign-up and renaming
 * refuse it — otherwise whoever registered that name first would become an admin. So create the
 * account first, then add it to the list.
 *
 * The family account owns the shared family collection. Admins manage its recipes from their own
 * accounts, so nobody needs to log in as it day to day.
 */
@Service
class AdminService(
    @Value("\${recipe.admins:family}") admins: String,
    @Value("\${recipe.family-account:family}") val familyAccount: String,
    // Optional so tests can build this class from two strings; Spring injects StoredAccessLookup.
    private val lookup: AccessLookup = AccessLookup.Direct,
) {
    private val adminUsernames: Set<String> =
        admins.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun isAdmin(user: User): Boolean = user.username in adminUsernames

    /** Whether [username] is reserved for an admin. Ignores case: "Family" must not pass for "family". */
    fun isAdminUsername(username: String): Boolean = adminUsernames.any { it.equals(username, ignoreCase = true) }

    fun isFamilyRecipe(recipe: Recipe): Boolean = recipe.user.username == familyAccount

    /**
     * May [user] decide what happens to [recipe] — trash, restore or delete it, change who can
     * see it, change who can edit it: its owner, or an admin when the recipe is in the family
     * collection.
     */
    fun canManage(recipe: Recipe, user: User?): Boolean {
        if (user == null) return false
        return recipe.user.id == user.id || (isAdmin(user) && isFamilyRecipe(recipe))
    }

    /**
     * May [user] change what [recipe] says — name, description, ingredients, categories, photos,
     * videos, notes: whoever can manage it, plus the editors its owner picked. Editors lose that
     * while the recipe is in the trash, which belongs to its managers alone.
     */
    fun canEdit(recipe: Recipe, user: User?): Boolean {
        if (canManage(recipe, user)) return true
        if (user == null || recipe.isTrashed) return false
        return lookup.isEditor(recipe, user)
    }

    /**
     * May [user] see [recipe].
     *
     * Trash is private to whoever manages the recipe: a public recipe moved to trash used to stay
     * readable by anyone holding its link, notes included. Otherwise a PUBLIC recipe is open to
     * everyone, guests too; a FAMILY recipe to any signed-in user; and an ONLY_ME recipe to its
     * owner — plus, at every level, the people the owner lets edit it and admins for
     * family-collection recipes.
     *
     * Blocking cuts into that from both sides. Someone who blocked the recipe's owner does not
     * get the owner's recipes at all, public ones included: that is what they asked for. Someone
     * the owner blocked loses the owner's FAMILY recipes but keeps the PUBLIC ones — anyone can
     * open those without signing in, so hiding them would only pretend.
     *
     * And nothing owned by a suspended account is readable, except by admins.
     */
    fun canRead(recipe: Recipe, user: User?): Boolean {
        // Everything a suspended account owns is out of view, whatever its level; admins still
        // see it so they can judge it.
        if (recipe.user.suspended) return user != null && isAdmin(user)
        if (recipe.isTrashed) return canManage(recipe, user)
        if (user != null) {
            if (canManage(recipe, user)) return true
            if (recipe.user.id in lookup.blockedBy(user)) return false
        }
        if (recipe.visibility == Visibility.PUBLIC) return true
        if (user == null) return false
        if (lookup.isEditor(recipe, user)) return true
        return recipe.visibility == Visibility.FAMILY && recipe.user.id !in lookup.blockersOf(user)
    }

    /** Whether [user] blocked the person [otherId] — whose notes, for one, they then do not get. */
    fun hasBlocked(user: User?, otherId: java.util.UUID?): Boolean =
        user != null && otherId != null && otherId in lookup.blockedBy(user)

    /**
     * [recipes] as [user] should find them in a list or a search: what [canRead] allows, minus
     * what they reported. A reported recipe stays readable by its link but stops turning up.
     */
    fun forLists(recipes: List<Recipe>, user: User?): List<Recipe> {
        if (user == null) return recipes.filterNot { it.user.suspended }
        val reported = lookup.reportedRecipeIds(user)
        return recipes.filter { it.id !in reported && canRead(it, user) }
    }
}

package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.recipe.Visibility
import com.recipebook.recipe_backend.security.InvalidInputException
import com.recipebook.recipe_backend.user.User
import org.springframework.stereotype.Component

/**
 * What must hold before a recipe is shown to people other than its owner and editors — the
 * conditions both app stores set for user-generated content.
 *
 * Asked whenever a recipe is created or saved. Recipes that are already FAMILY or PUBLIC are not
 * re-judged on their own: only a change brings a check.
 */
@Component
class PublishingRules(
    private val blockedWords: BlockedWords = BlockedWords(),
) {
    /**
     * @param actor who is saving
     * @param before the recipe's level until now (ONLY_ME for a new recipe)
     * @param after the level it will have
     * @param textChanged whether this save changes the name or the description
     * @param removedByAdmin whether an admin has locked the recipe against being shared
     */
    fun check(
        actor: User,
        before: Visibility,
        after: Visibility,
        name: String,
        description: String?,
        textChanged: Boolean,
        removedByAdmin: Boolean = false,
    ) {
        val opensUp = before.isNarrowerThan(after)

        // An admin took this recipe out of view. It stays with its owner, who can keep editing it,
        // but only an admin can let it be shared again (adminAllowPublish).
        if (opensUp && removedByAdmin) {
            throw InvalidInputException("REMOVED_BY_ADMIN", "An admin removed this recipe from view; it cannot be shared again until they allow it")
        }

        // The content rules are accepted once per account, before the first recipe is shown to
        // anyone. Only opening a recipe up asks for it: apps released before the rules resend the
        // flag on every save, and refusing those would stop people editing recipes they made
        // public long ago.
        if (opensUp && actor.termsAcceptedAt == null) {
            throw InvalidInputException("TERMS_REQUIRED", "Accept the content rules before sharing a recipe")
        }

        if (after.isShared && (opensUp || textChanged)) {
            blockedWords.firstIn(name, description)?.let { word ->
                throw InvalidInputException(
                    "BLOCKED_WORD",
                    "The word \"$word\" is not allowed in a recipe other people can see",
                    details = mapOf("word" to word),
                )
            }
        }
    }

    /**
     * What must hold before [actor] adds an ingredient. The ingredient list is one list for every
     * user and guest, so a new name is public from the start: the same two rules as a recipe
     * being shared, with the same codes.
     */
    fun checkIngredient(actor: User, name: String) {
        if (actor.termsAcceptedAt == null) {
            throw InvalidInputException("TERMS_REQUIRED", "Accept the content rules before adding an ingredient")
        }
        blockedWords.firstIn(name)?.let { word ->
            throw InvalidInputException(
                "BLOCKED_WORD",
                "The word \"$word\" is not allowed in an ingredient name",
                details = mapOf("word" to word),
            )
        }
    }
}

package com.recipebook.recipe_backend.recipe

import com.recipebook.recipe_backend.ingredient.IngredientRepository
import com.recipebook.recipe_backend.moderation.PublishingRules
import com.recipebook.recipe_backend.moderation.UserBlockRepository
import com.recipebook.recipe_backend.photo.PhotoAttachRule
import com.recipebook.recipe_backend.photo.PhotoFiles
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.InputValidation
import com.recipebook.recipe_backend.security.InvalidInputException
import com.recipebook.recipe_backend.security.RequestCache
import com.recipebook.recipe_backend.trash.RecipeTrash
import com.recipebook.recipe_backend.trash.RecipeTrashRepository
import com.recipebook.recipe_backend.unit.MeasurementUnitRepository
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Controller
class RecipeController(
    val recipeRepository: RecipeRepository,
    val ingredientRepository: IngredientRepository,
    val recipeIngredientRepository: RecipeIngredientRepository,
    val userRepository: UserRepository,
    val recipeTrashRepository: RecipeTrashRepository,
    val measurementUnitRepository: MeasurementUnitRepository,
    val currentUserService: CurrentUserService,
    val adminService: AdminService,
    val requestCache: RequestCache = RequestCache(),
    val publishingRules: PublishingRules = PublishingRules(),
    // Null only in unit tests that are not about blocking.
    val blockRepository: UserBlockRepository? = null,
    // Null only in unit tests that are not about photo files.
    val photoFiles: PhotoFiles? = null,
    val photoAttachRule: PhotoAttachRule? = null,
) {

    /** Refuses unless [user] may change what [recipe] says (AdminService.canEdit). */
    private fun requireEdit(recipe: Recipe, user: User) {
        if (!adminService.canEdit(recipe, user)) throw Exception("Unauthorized: You cannot edit this recipe")
    }

    /**
     * Refuses unless [user] manages [recipe] (AdminService.canManage). An editor gets a code the
     * apps can explain — "only the owner can do this" — instead of a bare failure.
     */
    private fun requireManage(recipe: Recipe, user: User) {
        if (adminService.canManage(recipe, user)) return
        if (adminService.canEdit(recipe, user)) {
            throw InvalidInputException("OWNER_ONLY", "Only the recipe's owner can do this")
        }
        throw Exception("Unauthorized: You do not own this recipe")
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun recipes(): List<Recipe> {
        val currentUser = currentUserService.getCurrentUser()
        val visible = adminService.forLists(recipeRepository.findVisibleRecipes(currentUser), currentUser)
        // Admins also see the family collection's private recipes, which they manage.
        val family = familyAccountFor(currentUser) ?: return visible
        return (visible + recipeRepository.findMyRecipes(family)).distinctBy { it.id }
    }

    /** The family account when [user] is an admin (and is not that account), else null. */
    private fun familyAccountFor(user: User): User? {
        if (!adminService.isAdmin(user) || user.username == adminService.familyAccount) return null
        return userRepository.findByUsername(adminService.familyAccount).orElse(null)
    }

    @QueryMapping
    fun publicRecipes(): List<Recipe> {
        // Open to guests. A signed-in caller gets the same list minus what they reported.
        return adminService.forLists(recipeRepository.findPublicRecipes(), currentUserService.getCurrentUserOrNull())
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun myRecipes(): List<Recipe> {
        val currentUser = currentUserService.getCurrentUser()
        return recipeRepository.findMyRecipes(currentUser)
    }

    // The main list's "Shared with me" section: other people's Family recipes and the recipes the
    // caller was made an editor of.
    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun sharedWithMeRecipes(): List<Recipe> {
        val currentUser = currentUserService.getCurrentUser()
        return adminService.forLists(recipeRepository.findSharedWith(currentUser), currentUser)
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun trashedRecipes(): List<Recipe> {
        val currentUser = currentUserService.getCurrentUser()
        val own = recipeRepository.findTrashedRecipes(currentUser)
        val family = familyAccountFor(currentUser) ?: return own
        return own + recipeRepository.findTrashedRecipes(family)
    }

    // No @PreAuthorize: guests may fetch a single PUBLIC recipe by id, mirroring publicRecipes()
    // (which already returns all public recipes, ingredients included, with no auth at all).
    // Without this, the client's detail screen calls recipe(id:), gets Unauthorized for a recipe
    // it already showed in the public list, and renders "No ingredients added yet". Everything
    // else follows AdminService.canRead.
    @QueryMapping
    fun recipe(@Argument id: UUID): Recipe? {
        val recipe = recipeRepository.findById(id).orElse(null) ?: return null
        val currentUser = currentUserService.getCurrentUserOrNull()

        if (adminService.canRead(recipe, currentUser)) {
            return recipe
        }
        throw Exception("Unauthorized: You do not have access to this recipe")
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun createRecipe(
        @Argument name: String,
        @Argument description: String?,
        @Argument familyCollection: Boolean?,
        @Argument visibility: Visibility? = null,
    ): Recipe {
        InputValidation.text(name, InputValidation.MAX_RECIPE_NAME, "Recipe name", required = true)
        description?.let { InputValidation.text(it, InputValidation.MAX_RECIPE_DESCRIPTION, "Description") }
        val currentUser = currentUserService.getCurrentUser()
        // The token holder owns the recipe, unless an admin files it in the family collection.
        val owner = if (familyCollection == true) {
            if (!adminService.isAdmin(currentUser)) throw Exception("Unauthorized: only admins can add to the family collection")
            userRepository.findByUsername(adminService.familyAccount)
                .orElseThrow { Exception("Family account not found") }
        } else {
            currentUser
        }
        val level = visibility ?: Visibility.ONLY_ME
        publishingRules.check(currentUser, Visibility.ONLY_ME, level, name, description, textChanged = true)
        val newRecipe = Recipe(name = name, description = description, user = owner)
        newRecipe.applyVisibility(level)
        return recipeRepository.save(newRecipe)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun updateRecipe(
        @Argument recipeId: UUID,
        @Argument name: String?,
        @Argument description: String?,
        @Argument isPublic: Boolean?,
        @Argument visibility: Visibility? = null,
    ): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        name?.let { InputValidation.text(it, InputValidation.MAX_RECIPE_NAME, "Recipe name", required = true) }
        description?.let { InputValidation.text(it, InputValidation.MAX_RECIPE_DESCRIPTION, "Description") }

        // Who can see the recipe is its manager's decision, not an editor's. Only an actual change
        // is checked: an old app sends the flag it was shown on every save, and that must not stop
        // an editor saving the text.
        val newVisibility = requestedVisibility(recipe.visibility, visibility, isPublic)
            ?.takeIf { it != recipe.visibility }
        if (newVisibility != null) requireManage(recipe, currentUser)

        val textChanged = (name != null && name != recipe.name) || (description != null && description != recipe.description)
        publishingRules.check(
            actor = currentUser,
            before = recipe.visibility,
            after = newVisibility ?: recipe.visibility,
            name = name ?: recipe.name,
            description = description ?: recipe.description,
            textChanged = textChanged,
            removedByAdmin = recipe.publishLocked,
        )

        // Update fields if new values are provided
        name?.let { recipe.name = it }
        description?.let { recipe.description = it }
        newVisibility?.let { recipe.applyVisibility(it) }

        return recipeRepository.save(recipe)
    }

    /**
     * The level an update asks for, or null to leave it alone. `visibility` is what current apps
     * send. `isPublic` is what apps released before the three levels send, on every save: true
     * means PUBLIC; false means "not public", which must not hide a recipe that a newer app set to
     * FAMILY — it only takes a PUBLIC recipe back to ONLY_ME. If both arrive, `visibility` wins.
     */
    private fun requestedVisibility(current: Visibility, visibility: Visibility?, isPublic: Boolean?): Visibility? = when {
        visibility != null -> visibility
        isPublic == true -> Visibility.PUBLIC
        isPublic == false && current == Visibility.PUBLIC -> Visibility.ONLY_ME
        else -> null
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun removeIngredientFromRecipe(
        @Argument recipeId: UUID,
        @Argument ingredientId: UUID
    ): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        // Filter out the specific ingredient link
        recipe.ingredients.removeIf { it.ingredient.id == ingredientId }
        return recipeRepository.save(recipe)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun addIngredientToRecipe(
        @Argument recipeId: UUID,
        @Argument ingredientId: UUID,
        @Argument quantity: Float,
        @Argument unitId: UUID?
    ): Recipe {
        val recipe = recipeRepository.findById(recipeId)
            .orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        val ingredient = ingredientRepository.findById(ingredientId)
            .orElseThrow { Exception("Ingredient not found") }

        val unit = unitId?.let {
            measurementUnitRepository.findById(it).orElseThrow { Exception("Unit not found") }
        }

        val link = RecipeIngredient(
            quantity = quantity,
            unit = unit,
            ingredient = ingredient,
            recipe = recipe
        )

        recipe.ingredients.add(link)
        return recipeRepository.save(recipe)
    }

    // No @PreAuthorize: guests may search/filter public recipes (e.g. the categories screen),
    // mirroring publicRecipes(). When unauthenticated, currentUser is null and the query falls
    // back to public-only results.
    @QueryMapping
    fun searchRecipes(
        @Argument keyword: String?,
        @Argument categoryId: UUID?,
        @Argument ingredientName: String?
    ): List<Recipe> {
        val currentUser = currentUserService.getCurrentUserOrNull()
        val found = recipeRepository.searchRecipes(keyword, categoryId, ingredientName, currentUser, signedIn = currentUser != null)
        return adminService.forLists(found, currentUser)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun copyRecipe(@Argument recipeId: UUID): Recipe {
        val original = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        // Must be able to read it (AdminService.canRead). The copy always starts out as Only me.
        if (!adminService.canRead(original, currentUser)) {
            throw Exception("Unauthorized: You do not have access to this recipe")
        }

        val copy = Recipe(
            name = "${original.name} (Copy)",
            description = original.description,
            user = currentUser,
            copiedFromId = original.id
        )

        val savedCopy = recipeRepository.save(copy)

        // Copy ingredients
        for (ri in original.ingredients) {
            savedCopy.ingredients.add(
                RecipeIngredient(quantity = ri.quantity, unit = ri.unit, ingredient = ri.ingredient, recipe = savedCopy)
            )
        }

        // Copy categories
        savedCopy.categories.addAll(original.categories)

        // Copy photos and video links
        savedCopy.photoUrls.addAll(original.photoUrls)
        savedCopy.videoLinks.addAll(original.videoLinks)

        return recipeRepository.save(savedCopy)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun shareRecipe(@Argument recipeId: UUID, @Argument userId: UUID): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireManage(recipe, currentUser)

        val targetUser = userRepository.findById(userId).orElseThrow { Exception("User not found") }

        if (targetUser.id == currentUser.id) throw Exception("Cannot share a recipe with yourself")
        if (targetUser.id == recipe.user.id) throw Exception("Cannot share a recipe with its owner")
        // Neither side of a block edits the other's recipes; blocking removed any that did.
        if (blockRepository?.existsBetween(recipe.user.id!!, targetUser.id!!) == true) {
            throw InvalidInputException("BLOCKED", "This person cannot be made an editor while one of you blocks the other")
        }

        if (recipe.sharedWith.any { it.id == targetUser.id }) {
            return recipe
        }

        // A suspended account cannot be newly made an editor. One that already is stays on the list
        // (unsuspending restores its access untouched), which is why this comes after the check above.
        if (targetUser.suspended) {
            throw InvalidInputException("USER_SUSPENDED", "This account has been suspended and cannot be made an editor")
        }

        recipe.sharedWith.add(targetUser)
        requestCache.clear()
        return recipeRepository.save(recipe)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun unshareRecipe(@Argument recipeId: UUID, @Argument userId: UUID): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        // The editor list is its manager's to change — except that an editor may always take
        // themselves off it.
        if (userId != currentUser.id) requireManage(recipe, currentUser)
        else if (!adminService.canEdit(recipe, currentUser)) throw Exception("Unauthorized: You do not own this recipe")

        recipe.sharedWith.removeIf { it.id == userId }
        requestCache.clear()
        return recipeRepository.save(recipe)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun addPhotoToRecipe(@Argument recipeId: UUID, @Argument url: String): Recipe {
        val link = InputValidation.photoLink(url)
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        if (!recipe.photoUrls.contains(link)) {
            // One photo per recipe: replacing it is remove, then add. Refused rather than replaced
            // silently, so an older app that still offers several photos shows an error instead of
            // losing the one already there. Recipes from before the rule may have more; they keep
            // them until they are removed.
            if (recipe.photoUrls.isNotEmpty()) {
                throw InvalidInputException("PHOTO_LIMIT", "A recipe has one photo; remove it before adding another")
            }
            // An uploaded file only by its uploader, or by someone who can see a recipe showing it.
            photoAttachRule?.check(link, currentUser)
            recipe.photoUrls.add(link)
        }
        return recipeRepository.save(recipe)
    }

    // Transactional so an uploaded file that just lost its last recipe is deleted after commit.
    @Transactional
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun removePhotoFromRecipe(@Argument recipeId: UUID, @Argument url: String): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        recipe.photoUrls.remove(url)
        val saved = recipeRepository.save(recipe)
        photoFiles?.deleteUnreferencedAfterCommit(listOf(url))
        return saved
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun addVideoLinkToRecipe(@Argument recipeId: UUID, @Argument url: String): Recipe {
        val link = InputValidation.webLink(url, "Video link")
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        if (!recipe.videoLinks.contains(link)) {
            recipe.videoLinks.add(link)
        }
        return recipeRepository.save(recipe)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun removeVideoLinkFromRecipe(@Argument recipeId: UUID, @Argument url: String): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireEdit(recipe, currentUser)

        recipe.videoLinks.remove(url)
        return recipeRepository.save(recipe)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun moveToTrash(@Argument recipeId: UUID): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireManage(recipe, currentUser)

        recipe.isTrashed = true
        recipeRepository.save(recipe)

        recipeTrashRepository.save(RecipeTrash(recipeId = recipe.id!!))

        return recipe
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun restoreFromTrash(@Argument recipeId: UUID): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireManage(recipe, currentUser)

        recipe.isTrashed = false
        recipeRepository.save(recipe)

        if (recipeTrashRepository.existsById(recipeId)) {
            recipeTrashRepository.deleteById(recipeId)
        }

        return recipe
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun deletePermanently(@Argument recipeId: UUID): Boolean {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        val currentUser = currentUserService.getCurrentUser()

        requireManage(recipe, currentUser)
        if (!recipe.isTrashed) throw Exception("Recipe must be in trash before permanent deletion")

        if (recipeTrashRepository.existsById(recipeId)) {
            recipeTrashRepository.deleteById(recipeId)
        }
        val photos = recipe.photoUrls.toList()
        recipeRepository.delete(recipe)
        photoFiles?.deleteUnreferencedAfterCommit(photos)
        return true
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun emptyTrash(): Boolean {
        // Own trash only, even for admins: a bulk "empty trash" must not also wipe the family
        // collection's trash. Admins delete trashed family recipes one at a time.
        val currentUser = currentUserService.getCurrentUser()
        val trashedRecipes = recipeRepository.findTrashedRecipes(currentUser)

        val photos = trashedRecipes.flatMap { it.photoUrls }.toSet()
        for (recipe in trashedRecipes) {
            if (recipeTrashRepository.existsById(recipe.id!!)) {
                recipeTrashRepository.deleteById(recipe.id!!)
            }
            recipeRepository.delete(recipe)
        }
        photoFiles?.deleteUnreferencedAfterCommit(photos)
        return true
    }

    // Notes and the editor list belong to the people who work on the recipe: whoever manages it and
    // its editors. Readers of a public or family recipe — guests included — used to receive both:
    // the owner's kitchen notes and the usernames it was shared with.
    @SchemaMapping(typeName = "Recipe", field = "notes")
    fun notes(recipe: Recipe): List<com.recipebook.recipe_backend.note.RecipeNote> =
        currentUserService.getAuthenticatedUserOrNull().let { me ->
            if (!adminService.canEdit(recipe, me)) emptyList()
            // Notes written by someone the caller blocked are left out for the caller, and so are
            // those a suspended account left on other people's recipes, for everyone. (On its own
            // recipe, which only admins can open, they stay so the admin can judge the account.)
            else recipe.notes.filterNot {
                adminService.hasBlocked(me, it.user.id) || (it.user.suspended && it.user.id != recipe.user.id)
            }
        }

    // `sharedWith` is the editor list. The name is from when it only granted reading.
    @SchemaMapping(typeName = "Recipe", field = "sharedWith")
    fun sharedWith(recipe: Recipe): List<User> =
        if (adminService.canEdit(recipe, currentUserService.getAuthenticatedUserOrNull())) recipe.sharedWith.toList() else emptyList()

    // Whether an admin took the recipe out of view, and why. Told only to whoever manages it:
    // to everyone else the recipe is simply not there any more.
    @SchemaMapping(typeName = "Recipe", field = "removedByAdmin")
    fun removedByAdmin(recipe: Recipe): Boolean =
        recipe.publishLocked && adminService.canManage(recipe, currentUserService.getAuthenticatedUserOrNull())

    @SchemaMapping(typeName = "Recipe", field = "removalReason")
    fun removalReason(recipe: Recipe): String? =
        recipe.removalReason?.takeIf { adminService.canManage(recipe, currentUserService.getAuthenticatedUserOrNull()) }

    // Derived, for apps released before the three levels: true only for PUBLIC.
    @SchemaMapping(typeName = "Recipe", field = "isPublic")
    fun isPublic(recipe: Recipe): Boolean = recipe.visibility == Visibility.PUBLIC

    // Whether the caller may change what this recipe says: its managers and its editors. The apps
    // gate their edit and notes controls on this instead of comparing usernames, so the rules live
    // only on the server.
    @SchemaMapping(typeName = "Recipe", field = "canEdit")
    fun canEdit(recipe: Recipe): Boolean =
        adminService.canEdit(recipe, currentUserService.getAuthenticatedUserOrNull())

    // Whether the caller may delete it, change who can see it and choose its editors: the owner,
    // or an admin for a family-collection recipe. Gates the delete, visibility and "who can edit"
    // controls.
    @SchemaMapping(typeName = "Recipe", field = "canManage")
    fun canManage(recipe: Recipe): Boolean =
        adminService.canManage(recipe, currentUserService.getAuthenticatedUserOrNull())
}

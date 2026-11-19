package com.recipebook.recipe_backend.recipe

import com.recipebook.recipe_backend.category.Category
import com.recipebook.recipe_backend.ingredient.Ingredient
import com.recipebook.recipe_backend.ingredient.IngredientRepository
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.trash.RecipeTrashRepository
import com.recipebook.recipe_backend.unit.MeasurementUnitRepository
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class RecipeControllerTest {

    @Mock lateinit var recipeRepository: RecipeRepository
    @Mock lateinit var ingredientRepository: IngredientRepository
    @Mock lateinit var recipeIngredientRepository: RecipeIngredientRepository
    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var recipeTrashRepository: RecipeTrashRepository
    @Mock lateinit var measurementUnitRepository: MeasurementUnitRepository
    @Mock lateinit var currentUserService: CurrentUserService

    private lateinit var controller: RecipeController

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    // The owner has accepted the content rules, so they may share recipes; `otherUser` has not.
    private val owner = User(id = userId, username = "owner", email = "owner@test.com", password = "pass", termsAcceptedAt = java.time.Instant.now())
    private val otherUser = User(id = otherUserId, username = "other", email = "other@test.com", password = "pass")
    private val recipeId = UUID.randomUUID()
    // "family" owns the family collection and is an admin; "admin" is a personal admin account.
    private val family = User(id = UUID.randomUUID(), username = "family", email = "f@test.com", password = "pass")
    private val admin = User(id = UUID.randomUUID(), username = "admin", email = "a@test.com", password = "pass")

    @BeforeEach
    fun setUp() {
        controller = RecipeController(
            recipeRepository, ingredientRepository, recipeIngredientRepository,
            userRepository, recipeTrashRepository, measurementUnitRepository, currentUserService,
            AdminService(admins = "family,admin", familyAccount = "family")
        )
    }

    private fun makeRecipe(user: User = owner, public: Boolean = false): Recipe {
        return Recipe(id = recipeId, name = "Test Recipe", user = user, isPublic = public)
    }

    @Test
    fun `createRecipe assigns recipe to current user`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.createRecipe("My Recipe", "Description", null)

        assertEquals("My Recipe", result.name)
        assertEquals(owner, result.user)
        assertFalse(result.isPublic)
    }

    @Test
    fun `recipes returns visible recipes for current user`() {
        val recipes = listOf(makeRecipe())
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.findVisibleRecipes(owner)).thenReturn(recipes)

        val result = controller.recipes()

        assertEquals(1, result.size)
        assertEquals("Test Recipe", result[0].name)
    }

    @Test
    fun `sharedWithMeRecipes returns recipes shared with the current user`() {
        val shared = listOf(makeRecipe(user = otherUser).also { it.sharedWith.add(owner) })
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.findSharedWith(owner)).thenReturn(shared)

        val result = controller.sharedWithMeRecipes()

        assertEquals(1, result.size)
        assertEquals(otherUser, result[0].user)
    }

    @Test
    fun `recipe returns public recipe`() {
        val recipe = makeRecipe(public = true)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(otherUser)

        val result = controller.recipe(recipeId)

        assertNotNull(result)
        assertEquals("Test Recipe", result!!.name)
    }

    @Test
    fun `recipe returns public recipe to a guest with no current user`() {
        val recipe = makeRecipe(public = true)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(null)

        val result = controller.recipe(recipeId)

        assertNotNull(result)
        assertEquals("Test Recipe", result!!.name)
    }

    @Test
    fun `recipe returns own private recipe`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(owner)

        val result = controller.recipe(recipeId)

        assertNotNull(result)
    }

    @Test
    fun `recipe throws for unauthorized access to private recipe`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.recipe(recipeId)
        }
    }

    @Test
    fun `recipe throws for guest fetching a private recipe`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(null)

        assertThrows(Exception::class.java) {
            controller.recipe(recipeId)
        }
    }

    @Test
    fun `recipe returns null for nonexistent id`() {
        whenever(recipeRepository.findById(any<UUID>())).thenReturn(Optional.empty())

        assertNull(controller.recipe(UUID.randomUUID()))
    }

    @Test
    fun `updateRecipe updates fields for owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.updateRecipe(recipeId, "Updated", "New desc", true)

        assertEquals("Updated", result.name)
        assertEquals("New desc", result.description)
        assertTrue(result.isPublic)
    }

    @Test
    fun `updateRecipe throws for non-owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.updateRecipe(recipeId, "Hack", null, null)
        }
    }

    @Test
    fun `addIngredientToRecipe adds ingredient for owner`() {
        val recipe = makeRecipe()
        val ingredientId = UUID.randomUUID()
        val ingredient = Ingredient(id = ingredientId, name = "Salt", caloriesPer100g = 0f)

        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient))
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.addIngredientToRecipe(recipeId, ingredientId, 50f, null)

        assertEquals(1, result.ingredients.size)
        assertEquals(50f, result.ingredients[0].quantity)
    }

    @Test
    fun `addIngredientToRecipe throws for non-owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.addIngredientToRecipe(recipeId, UUID.randomUUID(), 100f, null)
        }
    }

    @Test
    fun `removeIngredientFromRecipe removes correct ingredient`() {
        val ingredientId = UUID.randomUUID()
        val ingredient = Ingredient(id = ingredientId, name = "Salt")
        val recipe = makeRecipe()
        recipe.ingredients.add(RecipeIngredient(id = UUID.randomUUID(), quantity = 10f, ingredient = ingredient, recipe = recipe))

        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.removeIngredientFromRecipe(recipeId, ingredientId)

        assertTrue(result.ingredients.isEmpty())
    }

    @Test
    fun `moveToTrash sets isTrashed and saves trash record`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }
        whenever(recipeTrashRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = controller.moveToTrash(recipeId)

        assertTrue(result.isTrashed)
        verify(recipeTrashRepository).save(any())
    }

    @Test
    fun `moveToTrash throws for non-owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.moveToTrash(recipeId)
        }
    }

    @Test
    fun `restoreFromTrash untrashes recipe`() {
        val recipe = makeRecipe()
        recipe.isTrashed = true
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }
        whenever(recipeTrashRepository.existsById(recipeId)).thenReturn(true)

        val result = controller.restoreFromTrash(recipeId)

        assertFalse(result.isTrashed)
        verify(recipeTrashRepository).deleteById(recipeId)
    }

    // --- Copy tests ---

    @Test
    fun `copyRecipe creates copy for current user with copiedFromId`() {
        val ingredient = Ingredient(id = UUID.randomUUID(), name = "Flour", caloriesPer100g = 364f)
        val category = Category(id = UUID.randomUUID(), name = "Baking")
        val original = makeRecipe(public = true)
        original.ingredients.add(RecipeIngredient(id = UUID.randomUUID(), quantity = 200f, ingredient = ingredient, recipe = original))
        original.categories.add(category)

        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(original))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.copyRecipe(recipeId)

        assertEquals("Test Recipe (Copy)", result.name)
        assertEquals(otherUser, result.user)
        assertEquals(recipeId, result.copiedFromId)
        assertFalse(result.isPublic)
        assertEquals(1, result.ingredients.size)
        assertEquals(200f, result.ingredients[0].quantity)
        assertEquals(1, result.categories.size)
        assertEquals("Baking", result.categories.first().name)
    }

    @Test
    fun `copyRecipe allows owner to copy own recipe`() {
        val original = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(original))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.copyRecipe(recipeId)

        assertEquals("Test Recipe (Copy)", result.name)
        assertEquals(owner, result.user)
    }

    @Test
    fun `copyRecipe allows shared user to copy`() {
        val original = makeRecipe()
        original.sharedWith.add(otherUser)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(original))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.copyRecipe(recipeId)

        assertEquals(otherUser, result.user)
        assertEquals(recipeId, result.copiedFromId)
    }

    @Test
    fun `copyRecipe throws for unauthorized private recipe`() {
        val original = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(original))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.copyRecipe(recipeId)
        }
    }

    // --- Sharing tests ---

    @Test
    fun `shareRecipe adds user to sharedWith`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser))
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.shareRecipe(recipeId, otherUserId)

        assertEquals(1, result.sharedWith.size)
        assertEquals(otherUserId, result.sharedWith.first().id)
    }

    @Test
    fun `shareRecipe throws for non-owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.shareRecipe(recipeId, userId)
        }
    }

    @Test
    fun `shareRecipe throws when sharing with yourself`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(owner))

        assertThrows(Exception::class.java) {
            controller.shareRecipe(recipeId, userId)
        }
    }

    @Test
    fun `shareRecipe is idempotent for already shared user`() {
        val recipe = makeRecipe()
        recipe.sharedWith.add(otherUser)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser))

        val result = controller.shareRecipe(recipeId, otherUserId)

        assertEquals(1, result.sharedWith.size)
        verify(recipeRepository, never()).save(any())
    }

    @Test
    fun `unshareRecipe removes user from sharedWith`() {
        val recipe = makeRecipe()
        recipe.sharedWith.add(otherUser)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.unshareRecipe(recipeId, otherUserId)

        assertTrue(result.sharedWith.isEmpty())
    }

    @Test
    fun `unshareRecipe throws for non-owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.unshareRecipe(recipeId, userId)
        }
    }

    @Test
    fun `recipe returns shared recipe for shared user`() {
        val recipe = makeRecipe()
        recipe.sharedWith.add(otherUser)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(otherUser)

        val result = controller.recipe(recipeId)

        assertNotNull(result)
        assertEquals("Test Recipe", result!!.name)
    }

    // --- Content rules and blocked words ---

    private fun updateBy(user: User, recipe: Recipe, name: String? = null, description: String? = null, isPublic: Boolean? = null, visibility: Visibility? = null): Recipe {
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        // Lenient: the refused cases never reach save.
        org.mockito.Mockito.lenient().doAnswer { it.arguments[0] }.whenever(recipeRepository).save(any<Recipe>())
        return controller.updateRecipe(recipeId, name, description, isPublic, visibility)
    }

    @Test
    fun `sharing a recipe is refused until the content rules are accepted`() {
        val recipe = makeRecipe(user = otherUser)
        refusedWith("TERMS_REQUIRED") { updateBy(otherUser, recipe, visibility = Visibility.FAMILY) }
        refusedWith("TERMS_REQUIRED") { updateBy(otherUser, recipe, visibility = Visibility.PUBLIC) }
        refusedWith("TERMS_REQUIRED") { updateBy(otherUser, recipe, isPublic = true) }
        assertEquals(Visibility.ONLY_ME, recipe.visibility)

        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)
        refusedWith("TERMS_REQUIRED") { controller.createRecipe("New", null, null, Visibility.PUBLIC) }
        verify(recipeRepository, never()).save(any<Recipe>())
    }

    @Test
    fun `an account that has not accepted can still edit and narrow what it already shares`() {
        val public = makeRecipe(user = otherUser, public = true)
        // An old app resends isPublic: true with every save of a recipe that is already public.
        assertEquals("Renamed", updateBy(otherUser, public, name = "Renamed", isPublic = true).name)
        assertEquals(Visibility.FAMILY, updateBy(otherUser, public, visibility = Visibility.FAMILY).visibility)
        // Back up to PUBLIC opens it to more people again, which does need the rules.
        refusedWith("TERMS_REQUIRED") { updateBy(otherUser, public, visibility = Visibility.PUBLIC) }
        assertEquals(Visibility.ONLY_ME, updateBy(otherUser, public, visibility = Visibility.ONLY_ME).visibility)
    }

    @Test
    fun `a blocked word stops a recipe being shared, and the refusal names the word`() {
        val recipe = makeRecipe().also { it.description = "Полная Хуйня, а не суп" }
        val error = assertThrows(com.recipebook.recipe_backend.security.InvalidInputException::class.java) {
            updateBy(owner, recipe, visibility = Visibility.FAMILY)
        }
        assertEquals("BLOCKED_WORD", error.code)
        assertEquals("Хуйня", error.details["word"])
        assertEquals(Visibility.ONLY_ME, recipe.visibility)

        // Kept to yourself, a recipe may say anything.
        assertEquals("Shit soup", updateBy(owner, makeRecipe(), name = "Shit soup").name)
    }

    @Test
    fun `a blocked word cannot be written into a recipe that is already shared`() {
        val recipe = makeRecipe(public = true)
        refusedWith("BLOCKED_WORD") { updateBy(owner, recipe, name = "Fucking good soup") }
        refusedWith("BLOCKED_WORD") { updateBy(owner, recipe, description = "what a bitch to make") }
        assertEquals("Test Recipe", recipe.name)

        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        refusedWith("BLOCKED_WORD") { controller.createRecipe("Shit pie", null, null, Visibility.PUBLIC) }
    }

    @Test
    fun `a shared recipe that already contains a blocked word can still be saved unchanged or hidden`() {
        // Written before the filter existed. An old app resends the same text on every save.
        val recipe = makeRecipe(public = true).also { it.description = "old shit" }
        assertEquals(Visibility.PUBLIC, updateBy(owner, recipe, name = "Test Recipe", description = "old shit", isPublic = true).visibility)
        assertEquals(Visibility.ONLY_ME, updateBy(owner, recipe, visibility = Visibility.ONLY_ME).visibility)
    }

    @Test
    fun `a recipe an admin removed cannot be shared again, but stays editable`() {
        val recipe = makeRecipe().also { it.publishLocked = true; it.removalReason = "advertising" }

        refusedWith("REMOVED_BY_ADMIN") { updateBy(owner, recipe, visibility = Visibility.FAMILY) }
        refusedWith("REMOVED_BY_ADMIN") { updateBy(owner, recipe, isPublic = true) }
        assertEquals(Visibility.ONLY_ME, recipe.visibility)
        assertEquals("Reworded", updateBy(owner, recipe, name = "Reworded").name)

        // Only its managers are told.
        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(owner)
        assertTrue(controller.removedByAdmin(recipe))
        assertEquals("advertising", controller.removalReason(recipe))
        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(otherUser)
        assertFalse(controller.removedByAdmin(recipe))
        assertNull(controller.removalReason(recipe))
    }

    // --- Editors: people the owner lets edit the recipe ---

    private fun editedRecipe(): Recipe {
        val recipe = makeRecipe().also { it.sharedWith.add(otherUser) }
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)
        return recipe
    }

    private fun refusedWith(code: String, action: () -> Unit) {
        val error = assertThrows(com.recipebook.recipe_backend.security.InvalidInputException::class.java) { action() }
        assertEquals(code, error.code)
    }

    @Test
    fun `an editor can change the text, ingredients, photos and videos`() {
        val recipe = editedRecipe()
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }
        val ingredient = Ingredient(id = UUID.randomUUID(), name = "Flour", caloriesPer100g = 1f, proteinPer100g = 1f, fatPer100g = 1f, carbsPer100g = 1f)
        whenever(ingredientRepository.findById(ingredient.id!!)).thenReturn(Optional.of(ingredient))

        assertEquals("Edited", controller.updateRecipe(recipeId, "Edited", "By the editor", null).name)
        controller.addIngredientToRecipe(recipeId, ingredient.id!!, 2f, null)
        controller.addPhotoToRecipe(recipeId, "https://example.com/a.jpg")
        controller.addVideoLinkToRecipe(recipeId, "https://example.com/v")
        assertEquals(1, recipe.ingredients.size)
        assertEquals(listOf("https://example.com/a.jpg"), recipe.photoUrls)
        assertEquals(listOf("https://example.com/v"), recipe.videoLinks)

        controller.removeIngredientFromRecipe(recipeId, ingredient.id!!)
        controller.removePhotoFromRecipe(recipeId, "https://example.com/a.jpg")
        controller.removeVideoLinkFromRecipe(recipeId, "https://example.com/v")
        assertTrue(recipe.ingredients.isEmpty() && recipe.photoUrls.isEmpty() && recipe.videoLinks.isEmpty())
    }

    @Test
    fun `an editor cannot trash, restore or delete the recipe`() {
        val recipe = editedRecipe()
        refusedWith("OWNER_ONLY") { controller.moveToTrash(recipeId) }
        assertFalse(recipe.isTrashed)
        refusedWith("OWNER_ONLY") { controller.restoreFromTrash(recipeId) }
        refusedWith("OWNER_ONLY") { controller.deletePermanently(recipeId) }
        verify(recipeRepository, never()).delete(any<Recipe>())
    }

    @Test
    fun `an editor cannot change who sees the recipe`() {
        val recipe = editedRecipe()
        refusedWith("OWNER_ONLY") { controller.updateRecipe(recipeId, null, null, null, Visibility.PUBLIC) }
        refusedWith("OWNER_ONLY") { controller.updateRecipe(recipeId, null, null, true) }
        refusedWith("OWNER_ONLY") { controller.updateRecipe(recipeId, "New name", null, null, Visibility.FAMILY) }
        assertEquals(Visibility.ONLY_ME, recipe.visibility)
        assertEquals("Test Recipe", recipe.name, "a refused update must change nothing")
    }

    @Test
    fun `an editor on an old app can save the text although it resends the flag it was shown`() {
        val recipe = editedRecipe().also { it.applyVisibility(Visibility.FAMILY) }
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        controller.updateRecipe(recipeId, "Edited", null, false)

        assertEquals("Edited", recipe.name)
        assertEquals(Visibility.FAMILY, recipe.visibility)
    }

    @Test
    fun `an editor cannot change the editor list, except to leave it`() {
        val recipe = editedRecipe()
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }
        refusedWith("OWNER_ONLY") { controller.shareRecipe(recipeId, admin.id!!) }
        refusedWith("OWNER_ONLY") { controller.unshareRecipe(recipeId, admin.id!!) }
        assertEquals(1, recipe.sharedWith.size)

        controller.unshareRecipe(recipeId, otherUserId)
        assertTrue(recipe.sharedWith.isEmpty())
    }

    @Test
    fun `someone who is not an editor cannot take anyone off the list`() {
        val recipe = makeRecipe().also { it.sharedWith.add(otherUser) }
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)

        assertThrows(Exception::class.java) { controller.unshareRecipe(recipeId, otherUserId) }
        assertThrows(Exception::class.java) { controller.unshareRecipe(recipeId, admin.id!!) }
        assertEquals(1, recipe.sharedWith.size)
    }

    @Test
    fun `canEdit is true for editors and canManage is not`() {
        val recipe = makeRecipe().also { it.sharedWith.add(otherUser) }

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(otherUser)
        assertTrue(controller.canEdit(recipe))
        assertFalse(controller.canManage(recipe))
        assertEquals(1, controller.sharedWith(recipe).size)

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(owner)
        assertTrue(controller.canEdit(recipe))
        assertTrue(controller.canManage(recipe))

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(admin)
        assertFalse(controller.canEdit(recipe))
        assertFalse(controller.canManage(recipe))
        assertTrue(controller.sharedWith(recipe).isEmpty())
        assertTrue(controller.notes(recipe).isEmpty())
    }

    @Test
    fun `an editor loses access while the recipe is in the trash`() {
        val recipe = makeRecipe().also { it.sharedWith.add(otherUser); it.isTrashed = true }
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(otherUser)

        assertThrows(Exception::class.java) { controller.recipe(recipeId) }
        assertThrows(Exception::class.java) { controller.updateRecipe(recipeId, "x", null, null) }
    }

    // --- Photos & Videos tests ---

    @Test
    fun `addPhotoToRecipe adds url for owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.addPhotoToRecipe(recipeId, "https://example.com/photo.jpg")

        assertEquals(1, result.photoUrls.size)
        assertEquals("https://example.com/photo.jpg", result.photoUrls[0])
    }

    @Test
    fun `addPhotoToRecipe is idempotent for duplicate url`() {
        val recipe = makeRecipe()
        recipe.photoUrls.add("https://example.com/photo.jpg")
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.addPhotoToRecipe(recipeId, "https://example.com/photo.jpg")

        assertEquals(1, result.photoUrls.size)
    }

    @Test
    fun `addPhotoToRecipe throws for non-owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) {
            controller.addPhotoToRecipe(recipeId, "https://example.com/photo.jpg")
        }
    }

    @Test
    fun `addPhotoToRecipe refuses a second photo with PHOTO_LIMIT`() {
        val recipe = makeRecipe()
        recipe.photoUrls.add("https://example.com/first.jpg")
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)

        val e = assertThrows(com.recipebook.recipe_backend.security.InvalidInputException::class.java) {
            controller.addPhotoToRecipe(recipeId, "https://example.com/second.jpg")
        }
        assertEquals("PHOTO_LIMIT", e.code)
        assertEquals(listOf("https://example.com/first.jpg"), recipe.photoUrls)
    }

    @Test
    fun `removePhotoFromRecipe removes url`() {
        val recipe = makeRecipe()
        recipe.photoUrls.add("https://example.com/photo.jpg")
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.removePhotoFromRecipe(recipeId, "https://example.com/photo.jpg")

        assertTrue(result.photoUrls.isEmpty())
    }

    @Test
    fun `addVideoLinkToRecipe adds url for owner`() {
        val recipe = makeRecipe()
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.addVideoLinkToRecipe(recipeId, "https://youtube.com/watch?v=abc")

        assertEquals(1, result.videoLinks.size)
        assertEquals("https://youtube.com/watch?v=abc", result.videoLinks[0])
    }

    @Test
    fun `removeVideoLinkFromRecipe removes url`() {
        val recipe = makeRecipe()
        recipe.videoLinks.add("https://youtube.com/watch?v=abc")
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        val result = controller.removeVideoLinkFromRecipe(recipeId, "https://youtube.com/watch?v=abc")

        assertTrue(result.videoLinks.isEmpty())
    }

    // --- Search tests ---

    @Test
    fun `searchRecipes delegates to repository with current user`() {
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(owner)
        whenever(recipeRepository.searchRecipes("pasta", null, null, owner, true)).thenReturn(listOf(makeRecipe()))

        val result = controller.searchRecipes("pasta", null, null)

        assertEquals(1, result.size)
    }

    @Test
    fun `searchRecipes by ingredient name delegates correctly`() {
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(owner)
        whenever(recipeRepository.searchRecipes(null, null, "flour", owner, true)).thenReturn(listOf(makeRecipe()))

        val result = controller.searchRecipes(null, null, "flour")

        assertEquals(1, result.size)
    }

    @Test
    fun `searchRecipes tells the repository when the caller is a guest`() {
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(null)
        whenever(recipeRepository.searchRecipes("pasta", null, null, null, false)).thenReturn(listOf(makeRecipe(public = true)))

        assertEquals(1, controller.searchRecipes("pasta", null, null).size)
    }

    private fun updateAs(recipe: Recipe, isPublic: Boolean? = null, visibility: Visibility? = null): Recipe {
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }
        return controller.updateRecipe(recipeId, null, null, isPublic, visibility)
    }

    @Test
    fun `updateRecipe sets the visibility level and keeps the legacy flag in step`() {
        val family = updateAs(makeRecipe(), visibility = Visibility.FAMILY)
        assertEquals(Visibility.FAMILY, family.visibility)
        assertFalse(family.isPublic)

        val public = updateAs(makeRecipe(), visibility = Visibility.PUBLIC)
        assertEquals(Visibility.PUBLIC, public.visibility)
        assertTrue(public.isPublic)

        val private = updateAs(makeRecipe(public = true), visibility = Visibility.ONLY_ME)
        assertEquals(Visibility.ONLY_ME, private.visibility)
        assertFalse(private.isPublic)
    }

    @Test
    fun `legacy isPublic true makes a recipe public`() {
        assertEquals(Visibility.PUBLIC, updateAs(makeRecipe(), isPublic = true).visibility)
        assertEquals(Visibility.PUBLIC, updateAs(makeRecipe().also { it.applyVisibility(Visibility.FAMILY) }, isPublic = true).visibility)
    }

    @Test
    fun `legacy isPublic false takes a public recipe to only me but leaves a family recipe alone`() {
        assertEquals(Visibility.ONLY_ME, updateAs(makeRecipe(public = true), isPublic = false).visibility)
        assertEquals(Visibility.FAMILY, updateAs(makeRecipe().also { it.applyVisibility(Visibility.FAMILY) }, isPublic = false).visibility)
        assertEquals(Visibility.ONLY_ME, updateAs(makeRecipe(), isPublic = false).visibility)
    }

    @Test
    fun `visibility wins when an update carries both arguments`() {
        assertEquals(Visibility.FAMILY, updateAs(makeRecipe(), isPublic = true, visibility = Visibility.FAMILY).visibility)
        assertEquals(Visibility.PUBLIC, updateAs(makeRecipe(), isPublic = false, visibility = Visibility.PUBLIC).visibility)
    }

    @Test
    fun `updateRecipe without either argument leaves the visibility alone`() {
        assertEquals(Visibility.PUBLIC, updateAs(makeRecipe(public = true)).visibility)
    }

    @Test
    fun `createRecipe defaults to only me and takes a visibility`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        assertEquals(Visibility.ONLY_ME, controller.createRecipe("A", null, null).visibility)
        val family = controller.createRecipe("B", null, null, Visibility.FAMILY)
        assertEquals(Visibility.FAMILY, family.visibility)
        assertFalse(family.isPublic)
        assertTrue(controller.createRecipe("C", null, null, Visibility.PUBLIC).isPublic)
    }

    @Test
    fun `a family recipe is readable by any signed-in user but not by a guest`() {
        val recipe = makeRecipe().also { it.applyVisibility(Visibility.FAMILY) }
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))

        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(otherUser)
        assertNotNull(controller.recipe(recipeId))

        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(null)
        assertThrows(Exception::class.java) { controller.recipe(recipeId) }
    }

    @Test
    fun `admin can edit a family recipe`() {
        val recipe = makeRecipe(user = family)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        assertEquals("Renamed", controller.updateRecipe(recipeId, "Renamed", null, null).name)
    }

    @Test
    fun `admin can trash a family recipe`() {
        val recipe = makeRecipe(user = family)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        assertTrue(controller.moveToTrash(recipeId).isTrashed)
    }

    @Test
    fun `non-admin cannot edit a family recipe`() {
        val recipe = makeRecipe(user = family, public = true)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(otherUser)

        assertThrows(Exception::class.java) { controller.updateRecipe(recipeId, "Hacked", null, null) }
        verify(recipeRepository, never()).save(any<Recipe>())
    }

    @Test
    fun `admin cannot edit another user's personal recipe`() {
        val recipe = makeRecipe(user = owner, public = true)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)

        assertThrows(Exception::class.java) { controller.updateRecipe(recipeId, "Hacked", null, null) }
    }

    @Test
    fun `canEdit reflects owner, admin and guest`() {
        val familyRecipe = makeRecipe(user = family, public = true)
        val ownRecipe = makeRecipe(user = owner)

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(owner)
        assertTrue(controller.canEdit(ownRecipe))
        assertFalse(controller.canEdit(familyRecipe))

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(admin)
        assertTrue(controller.canEdit(familyRecipe))
        assertFalse(controller.canEdit(ownRecipe))

        whenever(currentUserService.getAuthenticatedUserOrNull()).thenReturn(null)
        assertFalse(controller.canEdit(familyRecipe))
        assertFalse(controller.canEdit(ownRecipe))
    }

    @Test
    fun `createRecipe in the family collection is owned by the family account`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        whenever(userRepository.findByUsername("family")).thenReturn(Optional.of(family))
        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }

        assertEquals("family", controller.createRecipe("Borscht", null, true).user.username)
    }

    @Test
    fun `createRecipe in the family collection is refused for non-admins`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(owner)

        assertThrows(Exception::class.java) { controller.createRecipe("Borscht", null, true) }
        verify(recipeRepository, never()).save(any<Recipe>())
    }

    @Test
    fun `admin reads a private family recipe`() {
        val recipe = makeRecipe(user = family, public = false)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUserOrNull()).thenReturn(admin)

        assertNotNull(controller.recipe(recipeId))
    }

    @Test
    fun `recipes includes private family recipes for admins`() {
        val privateFamily = makeRecipe(user = family, public = false)
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        whenever(recipeRepository.findVisibleRecipes(admin)).thenReturn(emptyList())
        whenever(userRepository.findByUsername("family")).thenReturn(Optional.of(family))
        whenever(recipeRepository.findMyRecipes(family)).thenReturn(listOf(privateFamily))

        assertEquals(listOf(privateFamily), controller.recipes())
    }

    @Test
    fun `shareRecipe refuses sharing with the recipe's owner`() {
        val recipe = makeRecipe(user = family)
        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        whenever(userRepository.findById(family.id!!)).thenReturn(Optional.of(family))

        assertThrows(Exception::class.java) { controller.shareRecipe(recipeId, family.id!!) }
    }
}

package com.recipebook.recipe_backend.security

import com.recipebook.recipe_backend.recipe.Recipe
import com.recipebook.recipe_backend.recipe.Visibility
import com.recipebook.recipe_backend.user.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class AdminServiceTest {

    private val service = AdminService(admins = " family , daria ,,", familyAccount = "family")
    private val family = User(id = UUID.randomUUID(), username = "family", email = "f@t.com", password = "p")
    private val daria = User(id = UUID.randomUUID(), username = "daria", email = "d@t.com", password = "p")
    private val mom = User(id = UUID.randomUUID(), username = "mom", email = "m@t.com", password = "p")

    @Test
    fun `admin list is trimmed and ignores empty entries`() {
        assertTrue(service.isAdmin(family))
        assertTrue(service.isAdmin(daria))
        assertFalse(service.isAdmin(mom))
        assertFalse(service.isAdminUsername(""))
    }

    @Test
    fun `canManage covers owners and admins on family recipes only`() {
        val familyRecipe = Recipe(name = "F", user = family)
        val momsRecipe = Recipe(name = "M", user = mom)

        assertTrue(service.canManage(familyRecipe, daria))
        assertTrue(service.canManage(momsRecipe, mom))
        assertFalse(service.canManage(familyRecipe, mom))
        assertFalse(service.canManage(momsRecipe, daria))
        assertFalse(service.canManage(familyRecipe, null))
    }

    @Test
    fun `canRead adds public and shared recipes`() {
        val privateRecipe = Recipe(name = "P", user = mom)
        val publicRecipe = Recipe(name = "Pub", user = mom, isPublic = true)
        val sharedRecipe = Recipe(name = "S", user = mom).also { it.sharedWith.add(daria) }

        assertTrue(service.canRead(publicRecipe, null))
        assertTrue(service.canRead(sharedRecipe, daria))
        assertFalse(service.canRead(privateRecipe, daria))
        assertFalse(service.canRead(privateRecipe, null))
    }

    @Test
    fun `canEdit adds the recipe's editors to its managers`() {
        val recipe = Recipe(name = "E", user = mom).also { it.sharedWith.add(daria) }
        val familyRecipe = Recipe(name = "F", user = family).also { it.sharedWith.add(mom) }
        val stranger = User(id = UUID.randomUUID(), username = "stranger", email = "s@t.com", password = "p")

        assertTrue(service.canEdit(recipe, mom))
        assertTrue(service.canEdit(recipe, daria))
        assertFalse(service.canManage(recipe, daria), "an editor does not manage the recipe")
        assertFalse(service.canEdit(recipe, stranger))
        assertFalse(service.canEdit(recipe, null))
        assertTrue(service.canEdit(familyRecipe, mom))
        assertTrue(service.canEdit(familyRecipe, daria), "admins manage family recipes")

        recipe.isTrashed = true
        assertFalse(service.canEdit(recipe, daria), "the trash belongs to the recipe's managers")
        assertTrue(service.canEdit(recipe, mom))
    }

    @Test
    fun `an editor reads the recipe at every visibility`() {
        val recipe = Recipe(name = "E", user = mom).also { it.sharedWith.add(daria) }
        for (level in Visibility.entries) {
            recipe.applyVisibility(level)
            assertTrue(service.canRead(recipe, daria), level.name)
        }
    }

    // --- Blocking ---

    private class Blocks(private val pairs: Set<Pair<UUID, UUID>>) : AccessLookup {
        override fun isEditor(recipe: Recipe, user: User) = AccessLookup.Direct.isEditor(recipe, user)
        override fun reportedRecipeIds(user: User): Set<UUID> = emptySet()
        override fun blockedBy(user: User) = pairs.filter { it.first == user.id }.map { it.second }.toSet()
        override fun blockersOf(user: User) = pairs.filter { it.second == user.id }.map { it.first }.toSet()
    }

    private fun levels(owner: User) = Visibility.entries.map { level -> Recipe(id = UUID.randomUUID(), name = level.name, user = owner).also { it.applyVisibility(level) } }

    @Test
    fun `someone who blocked the owner gets none of the owner's recipes, public included`() {
        val blocking = AdminService("family,daria", "family", Blocks(setOf(daria.id!! to mom.id!!)))
        assertEquals(listOf(false, false, false), levels(mom).map { blocking.canRead(it, daria) })
        assertTrue(blocking.forLists(levels(mom), daria).isEmpty())
        // Guests and everyone else are unaffected.
        assertEquals(listOf(false, false, true), levels(mom).map { blocking.canRead(it, null) })
        assertTrue(blocking.hasBlocked(daria, mom.id))
        assertFalse(blocking.hasBlocked(mom, daria.id))
    }

    @Test
    fun `someone the owner blocked loses the family recipes but keeps the public ones`() {
        val blocking = AdminService("family,daria", "family", Blocks(setOf(mom.id!! to daria.id!!)))
        assertEquals(listOf(false, false, true), levels(mom).map { blocking.canRead(it, daria) })
        assertEquals(listOf("PUBLIC"), blocking.forLists(levels(mom), daria).map { it.name })
    }

    @Test
    fun `a block never takes away recipes you manage`() {
        val blocking = AdminService("family,daria", "family", Blocks(setOf(mom.id!! to daria.id!!, daria.id!! to mom.id!!)))
        assertTrue(levels(mom).all { blocking.canRead(it, mom) })
        assertTrue(levels(family).all { blocking.canRead(it, daria) }, "admins keep the family collection")
    }

    @Test
    fun `nothing a suspended account owns is readable, except by admins`() {
        val suspended = User(id = UUID.randomUUID(), username = "spammer", email = "s@t.com", password = "p", suspended = true)
        val recipes = levels(suspended).onEach { it.sharedWith.add(mom) }

        assertTrue(recipes.none { service.canRead(it, null) })
        assertTrue(recipes.none { service.canRead(it, mom) }, "not even as an editor")
        assertTrue(recipes.none { service.canRead(it, suspended) })
        assertTrue(recipes.all { service.canRead(it, daria) }, "admins still see it")
        assertTrue(service.forLists(recipes, null).isEmpty())
        assertTrue(service.forLists(recipes, mom).isEmpty())
    }

    @Test
    fun `canRead follows the three visibility levels`() {
        val onlyMe = Recipe(name = "O", user = mom)
        val familyLevel = Recipe(name = "F", user = mom).also { it.applyVisibility(Visibility.FAMILY) }
        val public = Recipe(name = "P", user = mom).also { it.applyVisibility(Visibility.PUBLIC) }

        // Guest, another signed-in user, the owner.
        assertEquals(listOf(false, false, true), listOf<User?>(null, daria, mom).map { service.canRead(onlyMe, it) })
        assertEquals(listOf(false, true, true), listOf<User?>(null, daria, mom).map { service.canRead(familyLevel, it) })
        assertEquals(listOf(true, true, true), listOf<User?>(null, daria, mom).map { service.canRead(public, it) })
    }

    @Test
    fun `the legacy flag and the level agree however a recipe is built`() {
        assertEquals(Visibility.PUBLIC, Recipe(name = "P", user = mom, isPublic = true).visibility)
        assertEquals(Visibility.ONLY_ME, Recipe(name = "O", user = mom).visibility)
        val recipe = Recipe(name = "R", user = mom, isPublic = true)
        recipe.applyVisibility(Visibility.FAMILY)
        assertFalse(recipe.isPublic)
        recipe.visibility = Visibility.PUBLIC
        recipe.syncLegacyPublicFlag()
        assertTrue(recipe.isPublic)
    }

    @Test
    fun `trashed recipes are readable only by whoever manages them`() {
        val trashedPublic = Recipe(name = "T", user = mom, isPublic = true, isTrashed = true).also { it.sharedWith.add(daria) }
        val trashedFamily = Recipe(name = "TF", user = family, isPublic = true, isTrashed = true)

        assertTrue(service.canRead(trashedPublic, mom))
        assertFalse(service.canRead(trashedPublic, daria))
        assertFalse(service.canRead(trashedPublic, null))
        assertTrue(service.canRead(trashedFamily, daria))
        assertFalse(service.canRead(trashedFamily, mom))
    }
}

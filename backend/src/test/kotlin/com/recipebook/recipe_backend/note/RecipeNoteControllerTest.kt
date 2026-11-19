package com.recipebook.recipe_backend.note

import com.recipebook.recipe_backend.recipe.Recipe
import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class RecipeNoteControllerTest {

    @Mock lateinit var recipeNoteRepository: RecipeNoteRepository
    @Mock lateinit var recipeRepository: RecipeRepository
    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var currentUserService: CurrentUserService

    @Test
    fun `addNote creates note linked to recipe and current user`() {
        val user = User(id = UUID.randomUUID(), username = "chef", email = "c@t.com", password = "p")
        val recipeId = UUID.randomUUID()
        val recipe = Recipe(id = recipeId, name = "Pasta", user = user)

        whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe))
        whenever(currentUserService.getCurrentUser()).thenReturn(user)
        whenever(recipeNoteRepository.save(any<RecipeNote>())).thenAnswer { it.arguments[0] }

        val controller = RecipeNoteController(recipeNoteRepository, recipeRepository, userRepository, currentUserService, AdminService(admins = "family", familyAccount = "family"))
        val result = controller.addNote(recipeId, "Great recipe!")

        assertEquals("Great recipe!", result.note)
        assertEquals(user, result.user)
        assertEquals(recipe, result.recipe)
    }

    @Test
    fun `addNote throws when recipe not found`() {
        whenever(recipeRepository.findById(any<UUID>())).thenReturn(Optional.empty())

        val controller = RecipeNoteController(recipeNoteRepository, recipeRepository, userRepository, currentUserService, AdminService(admins = "family", familyAccount = "family"))

        assertThrows(Exception::class.java) {
            controller.addNote(UUID.randomUUID(), "Note")
        }
    }

    private val owner = User(id = UUID.randomUUID(), username = "owner", email = "o@t.com", password = "p")
    private val editor = User(id = UUID.randomUUID(), username = "editor", email = "e@t.com", password = "p")
    private val stranger = User(id = UUID.randomUUID(), username = "stranger", email = "s@t.com", password = "p")

    private fun controller() =
        RecipeNoteController(recipeNoteRepository, recipeRepository, userRepository, currentUserService, AdminService(admins = "family", familyAccount = "family"))

    private fun editedRecipe(): Recipe {
        val recipe = Recipe(id = UUID.randomUUID(), name = "Pasta", user = owner).also { it.sharedWith.add(editor) }
        whenever(recipeRepository.findById(recipe.id!!)).thenReturn(Optional.of(recipe))
        return recipe
    }

    @Test
    fun `an editor can add a note, a stranger cannot`() {
        val recipe = editedRecipe()
        whenever(recipeNoteRepository.save(any<RecipeNote>())).thenAnswer { it.arguments[0] }

        whenever(currentUserService.getCurrentUser()).thenReturn(editor)
        assertEquals(editor, controller().addNote(recipe.id!!, "More salt").user)

        whenever(currentUserService.getCurrentUser()).thenReturn(stranger)
        assertThrows(Exception::class.java) { controller().addNote(recipe.id!!, "Hello") }
    }

    @Test
    fun `an editor deletes their own notes but not the owner's, and the owner deletes any`() {
        val recipe = Recipe(id = UUID.randomUUID(), name = "Pasta", user = owner).also { it.sharedWith.add(editor) }
        val ownersNote = RecipeNote(id = UUID.randomUUID(), note = "Mine", recipe = recipe, user = owner)
        val editorsNote = RecipeNote(id = UUID.randomUUID(), note = "Editor's", recipe = recipe, user = editor)
        whenever(recipeNoteRepository.findById(ownersNote.id!!)).thenReturn(Optional.of(ownersNote))
        whenever(recipeNoteRepository.findById(editorsNote.id!!)).thenReturn(Optional.of(editorsNote))

        whenever(currentUserService.getCurrentUser()).thenReturn(editor)
        assertThrows(Exception::class.java) { controller().deleteNote(ownersNote.id!!) }
        assertTrue(controller().deleteNote(editorsNote.id!!))

        whenever(currentUserService.getCurrentUser()).thenReturn(owner)
        assertTrue(controller().deleteNote(editorsNote.id!!))
        verify(recipeNoteRepository, never()).delete(ownersNote)
    }
}

package com.recipebook.recipe_backend.note

import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.InputValidation
import com.recipebook.recipe_backend.user.UserRepository
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class RecipeNoteController (
    val recipeNoteRepository: RecipeNoteRepository,
    val recipeRepository: RecipeRepository,
    val userRepository: UserRepository,
    val currentUserService: CurrentUserService,
    val adminService: AdminService,
) {

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun addNote(
        @Argument recipeId: UUID,
        @Argument note: String
    ): RecipeNote {
        InputValidation.text(note, InputValidation.MAX_NOTE, "Note", required = true)
        val recipe = recipeRepository.findById(recipeId)
            .orElseThrow { Exception("Recipe not found") }

        // We get the user from the token, NOT from the GraphQL arguments
        val user = currentUserService.getCurrentUser()

        // Notes belong to the people who work on the recipe: its owner, its editors, and admins
        // on a family recipe. To annotate anyone else's recipe, copy it first.
        if (!adminService.canEdit(recipe, user)) {
            throw Exception("Unauthorized: You can only add notes to recipes you can edit")
        }

        val recipeNote = RecipeNote(note = note, recipe = recipe, user = user)
        return recipeNoteRepository.save(recipeNote)
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun deleteNote(@Argument noteId: UUID): Boolean {
        val note = recipeNoteRepository.findById(noteId)
            .orElseThrow { Exception("Note not found") }

        val user = currentUserService.getCurrentUser()
        // The author — while they can still read the recipe — or whoever manages it (so an owner
        // can remove an editor's note, and an admin can tidy family recipes). An editor cannot
        // delete other people's notes.
        val ownNote = note.user.id == user.id && adminService.canRead(note.recipe, user)
        if (!ownNote && !adminService.canManage(note.recipe, user)) {
            throw Exception("You can only delete your own notes")
        }

        recipeNoteRepository.delete(note)
        return true
    }
}
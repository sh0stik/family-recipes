package com.recipebook.recipe_backend.user

import com.recipebook.recipe_backend.moderation.ContentReportRepository
import com.recipebook.recipe_backend.moderation.UserBlockRepository
import com.recipebook.recipe_backend.note.RecipeNoteRepository
import com.recipebook.recipe_backend.photo.PhotoFiles
import com.recipebook.recipe_backend.photo.PhotoUploadRepository
import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.BlacklistedTokenRepository
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.security.PasswordResetTokenRepository
import com.recipebook.recipe_backend.trash.RecipeTrashRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path

/**
 * Permanently deletes an account and everything that belongs to it (App Store Guideline
 * 5.1.1(v)): the user's recipes, trashed ones included, with their ingredients, notes and photos;
 * notes they wrote anywhere; their place in other people's editor lists; their password-reset
 * codes; the reports they made and the reports about them; blocks in both directions; and the user row itself. Copies other people made of their recipes are kept — a copy
 * belongs to whoever made it.
 *
 * One transaction: either all of it goes or none of it does. Existing tokens stop working on
 * their own, because JwtAuthenticationFilter can no longer load the user.
 */
@Service
class AccountDeletionService(
    private val userRepository: UserRepository,
    private val recipeRepository: RecipeRepository,
    private val recipeNoteRepository: RecipeNoteRepository,
    private val recipeTrashRepository: RecipeTrashRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val blacklistedTokenRepository: BlacklistedTokenRepository,
    private val jwtService: JwtService,
    private val contentReportRepository: ContentReportRepository,
    private val userBlockRepository: UserBlockRepository,
    private val photoFiles: PhotoFiles,
    private val photoUploadRepository: PhotoUploadRepository,
) {

    @Transactional
    fun deleteAccount(user: User) {
        val userId = user.id!!
        // Bulk deletes first: they bypass the persistence context, and each clears it, so the
        // recipes loaded below reflect what is actually left in the database.
        recipeRepository.removeFromAllShares(userId)
        recipeNoteRepository.deleteAllByAuthor(user)
        passwordResetTokenRepository.deleteAllByUser(user)
        // Reports they made, and reports about them or their recipes: neither means anything once
        // the account and its recipes are gone, and both name the person.
        contentReportRepository.deleteAllInvolving(userId)
        // Blocks in both directions: theirs, and other people's blocks of them.
        userBlockRepository.deleteAllInvolving(userId)
        // Logged-out tokens are kept until they expire and name the account, so they would
        // otherwise outlive it by up to 30 days. Dropping them is safe: a token from a deleted
        // account can never open a later account with the same name (JwtAuthenticationFilter
        // looks the account up by id, or for older tokens checks the account's creation time).
        blacklistedTokenRepository.deleteAll(
            blacklistedTokenRepository.findAll().filter { issuedTo(it.token, user) }
        )

        val recipes = recipeRepository.findAllByUser(user)
        val photoUrls = recipes.flatMap { it.photoUrls }.toSet()
        val recipeIds = recipes.mapNotNull { it.id }
        recipeTrashRepository.deleteAllById(recipeIds.filter { recipeTrashRepository.existsById(it) })
        recipeRepository.deleteAll(recipes)

        // A copied recipe keeps its original's photo URLs, so a file is only removed when no
        // remaining recipe still shows it — and only after commit (photo/PhotoFiles).
        photoFiles.deleteUnreferencedAfterCommit(photoUrls)
        // Who uploaded what: meaningless once the account is gone. Files they uploaded that live
        // on in someone else's copy stay, and stay attachable by whoever can see that copy.
        photoUploadRepository.deleteAllByUploaderId(userId)

        userRepository.deleteById(userId)
    }

    /**
     * Whether [token] was issued to [user]. By account id when the token has one: its username is
     * the one the account had at login, which a rename since then has changed. Tokens from before
     * the id was added can only be matched by the current name. False for a token that no longer
     * parses (expired); the nightly cleanup removes those anyway.
     */
    private fun issuedTo(token: String, user: User): Boolean = try {
        val claims = jwtService.extractAllVerifiedClaims(token)
        val tokenUserId = jwtService.userIdOf(claims)
        if (tokenUserId != null) tokenUserId == user.id else claims.subject == user.username
    } catch (e: Exception) {
        false
    }

    /** The uploaded file a photo URL stands for (photo/PhotoFiles.uploadedFile). */
    internal fun uploadedFile(url: String): Path? = photoFiles.uploadedFile(url)
}

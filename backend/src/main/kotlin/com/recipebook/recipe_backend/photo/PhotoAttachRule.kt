package com.recipebook.recipe_backend.photo

import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.InvalidInputException
import com.recipebook.recipe_backend.user.User
import org.springframework.stereotype.Service

/**
 * Who may put an uploaded photo (`/uploads/<name>`) on a recipe: the account that uploaded it, or
 * anyone who can already see a recipe showing it. Without this, a file name seen anywhere — or a
 * stranger's upload that was never attached — could be put on any recipe.
 *
 * "Can see", not "can edit": copying a recipe in the apps creates a new recipe and adds the
 * original's photos to it one by one, and the copier usually cannot edit the original. Seeing
 * the photo already gives them the picture; the server-side copyRecipe copies it too.
 *
 * Links to other websites are not affected.
 */
@Service
class PhotoAttachRule(
    private val photoFiles: PhotoFiles,
    private val folder: UploadsFolder,
    private val photoUploadRepository: PhotoUploadRepository,
    private val recipeRepository: RecipeRepository,
    private val adminService: AdminService,
) {
    fun check(url: String, user: User) {
        if (!url.startsWith(UploadsFolder.URL_PREFIX)) return
        val file = photoFiles.uploadedFile(url) ?: throw InvalidInputException("INVALID_URL", "Photo link is not valid")
        val name = folder.root.relativize(file).toString()
        val upload = photoUploadRepository.findById(name).orElse(null)
        if (upload != null && upload.uploaderId == user.id) return
        if (recipeRepository.findAllByPhotoUrl(url).any { adminService.canRead(it, user) }) return
        throw InvalidInputException("PHOTO_NOT_YOURS", "This photo was uploaded by someone else")
    }
}

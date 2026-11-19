package com.recipebook.recipe_backend.photo

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The folder uploaded photos live in, served read-only under /uploads/ (recipe/WebConfig). In
 * Docker it is the `uploads` volume mounted at /app/uploads.
 */
@Component
class UploadsFolder(@Value("\${recipe.uploads-dir:uploads}") uploadsDir: String) {
    val root: Path = Paths.get(uploadsDir).toAbsolutePath().normalize()

    fun urlOf(fileName: String): String = "$URL_PREFIX$fileName"

    companion object {
        const val URL_PREFIX = "/uploads/"
    }
}

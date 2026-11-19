package com.recipebook.recipe_backend.photo

import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.RateLimitExceededException
import com.recipebook.recipe_backend.security.RateLimitService
import com.recipebook.recipe_backend.user.User
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.UUID

/**
 * `POST /api/photos`, multipart, the picture in the field `file`. Signed-in accounts only
 * (security/SecurityConfig). Answers `{"url": "/uploads/<uuid>.jpg"}`, which the app then puts on
 * a recipe with addPhotoToRecipe exactly like a typed link. Uploading alone shows the photo to
 * nobody: it is just a file until a recipe uses it, and recipe visibility decides who sees that.
 *
 * Refusals are `{"code": …}` with a status: NOT_AN_IMAGE / UNSUPPORTED_FORMAT (400), TOO_LARGE
 * (413), RATE_LIMITED (429), UNAUTHENTICATED (401, from SecurityConfig), ACCOUNT_SUSPENDED (403).
 */
@RestController
class PhotoController(
    private val currentUserService: CurrentUserService,
    private val rateLimitService: RateLimitService,
    private val photoStore: PhotoStore,
) {
    @PostMapping("/api/photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file", required = false) file: MultipartFile?): ResponseEntity<Map<String, String>> {
        val user = currentUserService.getCurrentUserOrNull() ?: return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED")
        // JwtAuthenticationFilter already turns a suspended account's token away; this is the
        // second lock on the one endpoint that writes files.
        if (user.suspended) return error(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED")
        // Counted before any work, so refused and broken uploads count too.
        rateLimitService.check("photo-upload:${user.id}", limit = UPLOADS_PER_HOUR, window = Duration.ofHours(1))

        if (file == null || file.isEmpty) return error(HttpStatus.BAD_REQUEST, "NOT_AN_IMAGE")
        // The multipart limits in application.yaml stop bigger requests before they get here;
        // this covers clients (and tests) that bypass them.
        if (file.size > MAX_UPLOAD_BYTES) return error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE")

        val jpeg = PhotoImages.toJpeg(file.bytes)
        return ResponseEntity.ok(mapOf("url" to photoStore.store(jpeg, user)))
    }

    @ExceptionHandler(PhotoRejectedException::class)
    fun rejected(e: PhotoRejectedException) = error(HttpStatus.BAD_REQUEST, e.code)

    @ExceptionHandler(RateLimitExceededException::class)
    fun rateLimited(e: RateLimitExceededException) = error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED")

    companion object {
        const val UPLOADS_PER_HOUR = 30
        /** Matches spring.servlet.multipart.max-file-size. */
        const val MAX_UPLOAD_BYTES = 15L * 1024 * 1024

        fun error(status: HttpStatus, code: String): ResponseEntity<Map<String, String>> =
            ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("code" to code))
    }
}

/**
 * A request over the multipart limit is refused while Spring reads it, before any controller is
 * chosen, so a controller's own @ExceptionHandler never sees it. Only /api/photos takes multipart.
 */
@RestControllerAdvice
class UploadSizeAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(e: MaxUploadSizeExceededException) = PhotoController.error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE")
}

/** Writes a checked photo into the uploads folder under a random name and records its uploader. */
@Service
class PhotoStore(
    private val folder: UploadsFolder,
    private val uploads: PhotoUploadRepository,
) {
    fun store(jpeg: ByteArray, uploader: User): String {
        Files.createDirectories(folder.root)
        val name = "${UUID.randomUUID()}.jpg"
        val target = folder.root.resolve(name)
        // Written under a temporary name and moved into place, so /uploads never serves half a file.
        val temp = Files.createTempFile(folder.root, ".upload-", ".tmp")
        try {
            Files.write(temp, jpeg)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        try {
            uploads.save(PhotoUpload(fileName = name, uploaderId = uploader.id!!))
        } catch (e: Exception) {
            Files.deleteIfExists(target)
            throw e
        }
        return folder.urlOf(name)
    }
}

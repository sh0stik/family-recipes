package com.recipebook.recipe_backend.photo

import com.recipebook.recipe_backend.recipe.RecipeRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * When an uploaded photo file goes away. A file stays as long as any recipe shows it — a copied
 * recipe keeps its original's photo URLs, so one file can belong to several recipes — and is
 * deleted once the last one lets go of it: the photo removed in the editor, the recipe deleted
 * from the trash, the account deleted. A file uploaded but never put on a recipe is removed by
 * the nightly [removeAbandonedUploads].
 *
 * Nothing outside the uploads folder is ever touched: every path goes through [uploadedFile].
 */
@Service
class PhotoFiles(
    private val folder: UploadsFolder,
    private val recipeRepository: RecipeRepository,
    private val photoUploadRepository: PhotoUploadRepository,
) {
    private val log = LoggerFactory.getLogger(PhotoFiles::class.java)

    /**
     * The file behind a photo URL served from /uploads/ (recipe/WebConfig), or null for anything
     * else — photos linked from other websites are not ours to delete. Refuses any name that would
     * resolve outside the uploads folder.
     */
    fun uploadedFile(url: String): Path? {
        val name = url.substringAfter(UploadsFolder.URL_PREFIX, "").substringBefore('?').substringBefore('#')
        if (name.isBlank()) return null
        val root = folder.root
        val file = root.resolve(name).normalize()
        return if (file.startsWith(root) && file != root) file else null
    }

    /** The file's name relative to the uploads folder, as stored in photo_uploads. */
    private fun nameOf(file: Path): String = folder.root.relativize(file).toString()

    /** Every uploaded file some recipe still shows, trashed recipes included. */
    private fun referencedFiles(): Set<Path> =
        recipeRepository.findUploadPhotoUrls().mapNotNull(::uploadedFile).toSet()

    /**
     * Deletes the files behind [urls] that no recipe shows any more. Call it inside the
     * transaction that removed them, after the change: which files are orphaned is decided now
     * (the transaction sees its own changes), but they are deleted only after commit — a rollback
     * must not leave recipes pointing at files that are already gone. Outside a transaction the
     * files are deleted at once.
     */
    fun deleteUnreferencedAfterCommit(urls: Collection<String>) {
        val candidates = urls.mapNotNull(::uploadedFile).toSet()
        if (candidates.isEmpty()) return
        recipeRepository.flush()
        val orphaned = candidates - referencedFiles()
        if (orphaned.isEmpty()) return
        photoUploadRepository.deleteAllById(orphaned.map(::nameOf))
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = orphaned.forEach(::deleteQuietly)
            })
        } else {
            orphaned.forEach(::deleteQuietly)
        }
    }

    /**
     * Removes files in the uploads folder that are older than [ABANDONED_AFTER] and that no recipe
     * shows: uploaded in an editor that was then closed without saving, or whose recipe failed to
     * save. A day is long enough for anyone still editing. Only plain files directly in the
     * folder are considered; nothing is followed into subfolders or out of the folder.
     */
    @Scheduled(cron = "0 30 3 * * *")
    fun removeAbandonedUploads(): Int = removeAbandonedUploads(Instant.now())

    internal fun removeAbandonedUploads(now: Instant): Int {
        val root = folder.root
        if (!Files.isDirectory(root)) return 0
        val cutoff = now.minus(ABANDONED_AFTER)
        val old = Files.list(root).use { files ->
            files.filter { Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
                .filter { Files.getLastModifiedTime(it).toInstant().isBefore(cutoff) }
                .toList()
        }
        if (old.isEmpty()) return 0
        val referenced = referencedFiles()
        val abandoned = old.map { it.toAbsolutePath().normalize() }.filter { it !in referenced && it.startsWith(root) }
        photoUploadRepository.deleteAllById(abandoned.map(::nameOf))
        abandoned.forEach(::deleteQuietly)
        log.info("Removed {} uploaded photo files no recipe uses.", abandoned.size)
        return abandoned.size
    }

    // Logged, never thrown: whatever removed the photo has already succeeded, and a leftover file
    // must not turn that into an error for the user.
    private fun deleteQuietly(file: Path) {
        try {
            Files.deleteIfExists(file)
        } catch (e: Exception) {
            log.warn("Could not delete uploaded photo {}: {}", file, e.message)
        }
    }

    companion object {
        val ABANDONED_AFTER: Duration = Duration.ofHours(24)
    }
}

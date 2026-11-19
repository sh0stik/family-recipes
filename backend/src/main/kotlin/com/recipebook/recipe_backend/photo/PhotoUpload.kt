package com.recipebook.recipe_backend.photo

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Who uploaded a file in the uploads folder. Only the uploader may put a freshly uploaded photo on
 * a recipe (photo/PhotoAttachRule), so a file name seen somewhere cannot be attached by anyone else.
 *
 * [uploaderId] is a plain column, not a foreign key: account deletion removes these rows itself,
 * and a file a deleted account uploaded can live on in someone else's copy of a recipe.
 * Created by `ddl-auto: update` on the first start, like every other table.
 */
@Entity
@Table(name = "photo_uploads", indexes = [Index(name = "idx_photo_uploads_uploader", columnList = "uploader_id")])
class PhotoUpload(
    /** The name inside the uploads folder, e.g. `0f8c…e1.jpg`; the photo's URL is `/uploads/<name>`. */
    @Id
    @Column(name = "file_name", length = 100)
    val fileName: String,

    @Column(name = "uploader_id", nullable = false)
    val uploaderId: UUID,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: Instant? = null,
)

interface PhotoUploadRepository : JpaRepository<PhotoUpload, String> {
    /** Forgets who uploaded what for a deleted account. The files follow photo/PhotoFiles' rules. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PhotoUpload p WHERE p.uploaderId = :uploaderId")
    fun deleteAllByUploaderId(@Param("uploaderId") uploaderId: UUID): Int
}

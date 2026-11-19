package com.recipebook.recipe_backend.user

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.recipebook.recipe_backend.photo.PhotoFiles
import com.recipebook.recipe_backend.photo.UploadsFolder
import org.mockito.kotlin.mock
import java.nio.file.Paths

class AccountDeletionServiceTest {

    private val service = AccountDeletionService(
        mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(),
        photoFiles = PhotoFiles(UploadsFolder("/data/uploads"), mock(), mock()),
        photoUploadRepository = mock(),
    )

    @Test
    fun `uploaded photo URLs map to files inside the uploads folder`() {
        assertEquals(Paths.get("/data/uploads/abc.jpg"), service.uploadedFile("/uploads/abc.jpg"))
        assertEquals(Paths.get("/data/uploads/abc.jpg"), service.uploadedFile("https://api.example.com/uploads/abc.jpg?v=2"))
    }

    @Test
    fun `photos hosted elsewhere are not ours to delete`() {
        assertNull(service.uploadedFile("https://example.com/photo.jpg"))
        assertNull(service.uploadedFile("/uploads/"))
    }

    @Test
    fun `names escaping the uploads folder are refused`() {
        assertNull(service.uploadedFile("/uploads/../../etc/passwd"))
        assertNull(service.uploadedFile("/uploads/.."))
    }
}

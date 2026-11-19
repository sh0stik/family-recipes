package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.photo.PhotoFiles
import com.recipebook.recipe_backend.photo.PhotoUploadRepository
import com.recipebook.recipe_backend.photo.TestImages
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.AccountDeletionService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shared set-up for the tests of what happens to uploaded photos once they are on recipes: one
 * Spring context with its own uploads folder (emptied before and after), accounts made through
 * [user] deleted again afterwards with everything they own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["recipe.uploads-dir=build/test-uploads/photo-lifecycle"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PhotoTestBase {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var photoUploadRepository: PhotoUploadRepository
    @Autowired lateinit var photoFiles: PhotoFiles
    @Autowired lateinit var accountDeletionService: AccountDeletionService

    protected val api by lazy { Api(mockMvc) }
    protected val uploads: Path = Paths.get("build/test-uploads/photo-lifecycle")
    private val accounts = mutableListOf<String>()

    @BeforeAll
    fun emptyUploads() {
        uploads.toFile().deleteRecursively()
        Files.createDirectories(uploads)
    }

    @AfterAll
    fun cleanUp() {
        for (name in accounts.distinct()) {
            userRepository.findByUsername(name).ifPresent { accountDeletionService.deleteAccount(it) }
        }
        uploads.toFile().deleteRecursively()
    }

    protected fun user(name: String): User {
        accounts += name
        // Has accepted the content rules, so its recipes may be shared.
        return userRepository.save(User(username = name, email = "$name@test.com", password = "x", termsAcceptedAt = java.time.Instant.now()))
    }

    protected fun token(user: User): String = jwtService.generateToken(CustomUserDetails(user))

    /** Uploads a small picture as [token]'s account and returns its `/uploads/…` URL. */
    protected fun upload(token: String): String {
        val response = mockMvc.perform(
            multipart("/api/photos")
                .file(MockMultipartFile("file", "p.png", "image/png", TestImages.png(32, 24)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        ).andReturn().response
        check(response.status == 200) { "upload failed: ${response.status} ${response.contentAsString}" }
        return response.contentAsString.substringAfter("\"url\":\"").substringBefore("\"")
    }

    protected fun fileOf(url: String): Path = uploads.resolve(url.removePrefix("/uploads/"))

    protected fun createRecipe(name: String, token: String): String {
        val response = api.graphql("""mutation { createRecipe(name: "$name") { id } }""", token)
        check(!api.failed(response)) { "createRecipe failed: $response" }
        return api.id(response)
    }

    protected fun addPhoto(recipeId: String, url: String, token: String): String =
        api.graphql("""mutation { addPhotoToRecipe(recipeId: "$recipeId", url: "$url") { photoUrls } }""", token)

    protected fun removePhoto(recipeId: String, url: String, token: String): String =
        api.graphql("""mutation { removePhotoFromRecipe(recipeId: "$recipeId", url: "$url") { photoUrls } }""", token)

    protected fun photos(recipeId: String, token: String): String =
        api.graphql("""{ recipe(id: "$recipeId") { photoUrls } }""", token)
}

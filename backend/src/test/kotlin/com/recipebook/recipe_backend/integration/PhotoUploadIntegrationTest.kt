package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.photo.PhotoUploadRepository
import com.recipebook.recipe_backend.photo.TestImages
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * POST /api/photos end to end: who may upload, what is refused and with which code, and what is
 * stored — a re-encoded JPEG with no metadata, at most 2048 px, under a random name, with its
 * uploader recorded. Own uploads folder, removed afterwards with the accounts it made.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "recipe.uploads-dir=build/test-uploads/photo-upload",
        "recipe.cors.allowed-origins=http://localhost:8085",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhotoUploadIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var photoUploadRepository: PhotoUploadRepository

    private val uploads = Paths.get("build/test-uploads/photo-upload")
    private lateinit var anna: User
    private lateinit var annaToken: String

    @BeforeAll
    fun setUp() {
        uploads.toFile().deleteRecursively()
        anna = userRepository.save(User(username = "upload-anna", email = "upload-anna@test.com", password = "x"))
        annaToken = jwtService.generateToken(CustomUserDetails(anna))
    }

    @AfterAll
    fun tearDown() {
        photoUploadRepository.deleteAll(photoUploadRepository.findAll().filter { it.uploaderId == anna.id || it.uploaderId == UUID(0, 0) })
        for (name in listOf("upload-anna", "upload-limited", "upload-suspended")) {
            userRepository.findByUsername(name).ifPresent {
                photoUploadRepository.deleteAll(photoUploadRepository.findAll().filter { u -> u.uploaderId == it.id })
                userRepository.delete(it)
            }
        }
        uploads.toFile().deleteRecursively()
    }

    private fun upload(bytes: ByteArray, token: String?, name: String = "photo.jpg"): MockHttpServletResponse {
        val request = multipart("/api/photos").file(MockMultipartFile("file", name, "image/jpeg", bytes))
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        return mockMvc.perform(request).andReturn().response
    }

    private fun code(response: MockHttpServletResponse): String? =
        response.contentAsString.takeIf { it.contains("\"code\":\"") }?.substringAfter("\"code\":\"")?.substringBefore("\"")

    private fun url(response: MockHttpServletResponse): String =
        response.contentAsString.substringAfter("\"url\":\"").substringBefore("\"")

    @Test
    fun `a signed-in upload is stored as a JPEG under a random name and served from uploads`() {
        val response = upload(TestImages.png(640, 480), annaToken, name = "../../evil.png")
        assertEquals(200, response.status, response.contentAsString)
        val url = url(response)
        assertTrue(Regex("^/uploads/[0-9a-f-]{36}\\.jpg$").matches(url), url)

        val name = url.removePrefix("/uploads/")
        val stored = uploads.resolve(name)
        assertTrue(Files.exists(stored))
        val image = TestImages.decode(Files.readAllBytes(stored))
        assertEquals(640 to 480, image.width to image.height)
        assertEquals(anna.id, photoUploadRepository.findById(name).orElseThrow().uploaderId)
        // The original's name went nowhere.
        assertFalse(Files.exists(uploads.resolve("evil.png")) || Files.exists(Paths.get("build/evil.png")))

        val served = mockMvc.perform(get(url)).andReturn().response
        assertEquals(200, served.status)
        assertTrue(served.contentAsByteArray.contentEquals(Files.readAllBytes(stored)))
    }

    @Test
    fun `EXIF is stripped, the picture is turned upright and capped at 2048 px`() {
        val phone = TestImages.jpegWithExif(3000, 2000, orientation = 6, secret = "GPS 55.7558N 37.6173E")
        val response = upload(phone, annaToken)
        assertEquals(200, response.status, response.contentAsString)
        val bytes = Files.readAllBytes(uploads.resolve(url(response).removePrefix("/uploads/")))
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("Exif"), "EXIF survived")
        assertFalse(text.contains("55.7558"), "the location survived")
        val image = TestImages.decode(bytes)
        // Stored landscape with "turn clockwise": comes out portrait, long side 2048.
        assertEquals(1365 to 2048, image.width to image.height)
        assertTrue(TestImages.isRed(image.getRGB(image.width - 20, 20)), "the red corner should now be top-right")
    }

    @Test
    fun `something that is not a picture is refused with NOT_AN_IMAGE and nothing is stored`() {
        val before = fileCount()
        val text = upload("just some text".toByteArray(), annaToken, name = "photo.jpg")
        assertEquals(400, text.status)
        assertEquals("NOT_AN_IMAGE", code(text))
        val broken = upload(TestImages.jpeg(200, 200).copyOfRange(0, 30), annaToken)
        assertEquals("NOT_AN_IMAGE", code(broken))
        val gif = upload("GIF89a\u0001\u0000\u0001\u0000".toByteArray(), annaToken, name = "a.gif")
        assertEquals(400, gif.status)
        assertEquals("UNSUPPORTED_FORMAT", code(gif))
        assertEquals(before, fileCount())
    }

    @Test
    fun `a file over 15 MB is refused with TOO_LARGE`() {
        val response = upload(ByteArray(15 * 1024 * 1024 + 1), annaToken)
        assertEquals(413, response.status)
        assertEquals("TOO_LARGE", code(response))
    }

    @Test
    fun `guests and dead tokens get 401 UNAUTHENTICATED`() {
        val guest = upload(TestImages.png(10, 10), token = null)
        assertEquals(401, guest.status)
        assertEquals("UNAUTHENTICATED", code(guest))
        val garbage = upload(TestImages.png(10, 10), token = "not-a-jwt")
        assertEquals(401, garbage.status)
        assertEquals("UNAUTHENTICATED", code(garbage))
    }

    @Test
    fun `a suspended account cannot upload`() {
        val suspended = userRepository.save(User(username = "upload-suspended", email = "upload-suspended@test.com", password = "x", suspended = true))
        val response = upload(TestImages.png(10, 10), jwtService.generateToken(CustomUserDetails(suspended)))
        assertEquals(401, response.status)
        assertTrue(photoUploadRepository.findAll().none { it.uploaderId == suspended.id })
    }

    @Test
    fun `the 31st upload within an hour is refused with RATE_LIMITED`() {
        val limited = userRepository.save(User(username = "upload-limited", email = "upload-limited@test.com", password = "x"))
        val token = jwtService.generateToken(CustomUserDetails(limited))
        val png = TestImages.png(8, 8)
        repeat(30) { assertEquals(200, upload(png, token).status, "upload ${it + 1}") }
        val refused = upload(png, token)
        assertEquals(429, refused.status)
        assertEquals("RATE_LIMITED", code(refused))
        // Per account: someone else can still upload.
        assertEquals(200, upload(png, annaToken).status)
    }

    @Test
    fun `other unknown paths still answer 403 to guests`() {
        assertEquals(403, mockMvc.perform(get("/api/anything")).andReturn().response.status)
        // Only POST is open, and only to accounts.
        val getWithToken = mockMvc.perform(get("/api/photos").header(HttpHeaders.AUTHORIZATION, "Bearer $annaToken")).andReturn().response
        assertEquals(403, getWithToken.status)
    }

    @Test
    fun `the web app's origin may upload from the browser`() {
        val response = mockMvc.perform(
            options("/api/photos")
                .header(HttpHeaders.ORIGIN, "http://localhost:8085")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
        ).andReturn().response
        assertEquals(200, response.status)
        assertEquals("http://localhost:8085", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        val evil = mockMvc.perform(
            options("/api/photos")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        ).andReturn().response
        assertNull(evil.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    private fun fileCount(): Int = if (Files.exists(uploads)) Files.list(uploads).use { it.count().toInt() } else 0
}

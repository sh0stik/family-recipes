package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.photo.PhotoUpload
import com.recipebook.recipe_backend.photo.PhotoUploadRepository
import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.PasswordResetToken
import com.recipebook.recipe_backend.security.PasswordResetTokenRepository
import com.recipebook.recipe_backend.trash.RecipeTrashRepository
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Account deletion (App Store Guideline 5.1.1(v)) and the admin role, end to end against the real
 * schema: what deleteAccount removes, what it must leave alone, the admin lock, and admins
 * managing the family collection.
 *
 * Own `recipe.invite-code` for a separate Spring context (it registers users), its own admin list
 * and a throwaway uploads folder. Everything it creates is removed again in [tearDown]: contexts
 * share one database, and SeedDataIntegrationTest asserts exact seed counts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "recipe.invite-code=account-deletion-invite",
        "recipe.admins=family,deletion-admin,deletion-reserved",
        "recipe.uploads-dir=build/test-uploads/account-deletion",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AccountDeletionIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var recipeRepository: RecipeRepository
    @Autowired lateinit var recipeTrashRepository: RecipeTrashRepository
    @Autowired lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    @Autowired lateinit var blacklistedTokenRepository: com.recipebook.recipe_backend.security.BlacklistedTokenRepository
    @Autowired lateinit var photoUploadRepository: PhotoUploadRepository

    private val uploads = Paths.get("build/test-uploads/account-deletion")

    lateinit var familyToken: String
    lateinit var aliceToken: String
    lateinit var aliceLoggedOut: String
    lateinit var bobToken: String
    lateinit var adminToken: String
    lateinit var carolToken: String

    lateinit var aliceRecipe: String
    lateinit var aliceTrashed: String
    lateinit var bobRecipe: String
    lateinit var bobCopy: String
    lateinit var familyRecipe: String

    @BeforeAll
    fun setUp() {
        Files.createDirectories(uploads)
        Files.writeString(uploads.resolve("alice-only.jpg"), "x")
        Files.writeString(uploads.resolve("copied.jpg"), "x")

        familyToken = login("family", "password")
        aliceToken = signUp("deletion-alice")
        // A second session that Alice logged out of: its token sits in the blacklist.
        aliceLoggedOut = login("deletion-alice", "password123")
        graphql("mutation { logout }", aliceLoggedOut)
        assertTrue(blacklistedTokenRepository.existsByToken(aliceLoggedOut), "logout did not record the token")
        bobToken = signUp("deletion-bob")

        // A reserved admin name cannot be registered, so the personal admin is inserted directly —
        // the same order the docs prescribe: the account exists, then it is listed as an admin.
        userRepository.save(
            // Has accepted the content rules: the admin publishes a family recipe below.
            User(username = "deletion-admin", email = "deletion-admin@test.com", password = passwordEncoder.encode("password123")!!, termsAcceptedAt = Instant.now())
        )
        adminToken = login("deletion-admin", "password123")

        // Alice's two uploads, recorded as the upload endpoint would: only she may attach them.
        for (name in listOf("alice-only.jpg", "copied.jpg")) {
            photoUploadRepository.save(PhotoUpload(fileName = name, uploaderId = user("deletion-alice").id!!))
        }

        aliceRecipe = id(graphql("""mutation { createRecipe(name: "Alice soup") { id } }""", aliceToken))
        graphql("""mutation { addPhotoToRecipe(recipeId: "$aliceRecipe", url: "/uploads/copied.jpg") { id } }""", aliceToken)
        graphql("""mutation { addNote(recipeId: "$aliceRecipe", note: "Alice's note") { id } }""", aliceToken)

        aliceTrashed = id(graphql("""mutation { createRecipe(name: "Alice old cake") { id } }""", aliceToken))
        graphql("""mutation { addPhotoToRecipe(recipeId: "$aliceTrashed", url: "/uploads/alice-only.jpg") { id } }""", aliceToken)
        graphql("""mutation { moveToTrash(recipeId: "$aliceTrashed") { id } }""", aliceToken)

        // Alice shares her soup with Bob, who copies it — the copy, and its photo, must survive.
        graphql("""mutation { shareRecipe(recipeId: "$aliceRecipe", userId: "${userId("deletion-bob")}") { id } }""", aliceToken)
        bobCopy = id(graphql("""mutation { copyRecipe(recipeId: "$aliceRecipe") { id } }""", bobToken))

        // Bob shares one of his with Alice — it must stay, minus Alice.
        bobRecipe = id(graphql("""mutation { createRecipe(name: "Bob stew") { id } }""", bobToken))
        graphql("""mutation { shareRecipe(recipeId: "$bobRecipe", userId: "${userId("deletion-alice")}") { id } }""", bobToken)

        passwordResetTokenRepository.save(
            PasswordResetToken(token = UUID.randomUUID().toString(), expiryDate = Instant.now().plusSeconds(3600), user = user("deletion-alice"))
        )

        // Reports in every direction around Alice, and one that has nothing to do with her.
        val aliceId = userId("deletion-alice")
        val bobId = userId("deletion-bob")
        graphql("""mutation { reportRecipe(recipeId: "$bobRecipe", reason: SPAM) }""", aliceToken)
        graphql("""mutation { reportUser(userId: "$bobId", reason: OTHER, comment: "by alice") }""", aliceToken)
        graphql("""mutation { reportRecipe(recipeId: "$aliceRecipe", reason: INAPPROPRIATE) }""", bobToken)
        graphql("""mutation { reportUser(userId: "$aliceId", reason: OTHER, comment: "about alice") }""", bobToken)
        graphql("""mutation { reportUser(userId: "${userId("deletion-admin")}", reason: OTHER, comment: "unrelated") }""", bobToken)
        assertEquals(5, reportCount(), "the reports were not stored")

        // Blocks in both directions around a third person, Carol, who is then deleted too.
        carolToken = signUp("deletion-carol")
        val carolId = userId("deletion-carol")
        graphql("""mutation { blockUser(userId: "$carolId") }""", bobToken)
        graphql("""mutation { blockUser(userId: "$bobId") }""", carolToken)
        graphql("""mutation { blockUser(userId: "$aliceId") }""", carolToken)
        assertEquals(3, blockCount(), "the blocks were not stored")
    }

    private fun blockCount(): Int = jdbc.queryForObject("SELECT count(*) FROM user_blocks", Int::class.java)!!

    @Test
    @Order(4)
    fun `deleting an account removes its blocks and other people's blocks of it`() {
        // Alice is gone already (order 3), and Carol's block of her with her.
        assertEquals(2, blockCount())
        val response = graphql("""mutation { deleteAccount(password: "password123") }""", carolToken)
        assertTrue(response.contains("\"deleteAccount\":true"), response)
        assertEquals(0, blockCount())
        assertTrue(userRepository.findByUsername("deletion-bob").isPresent)
    }

    private fun reportCount(): Int = jdbc.queryForObject("SELECT count(*) FROM content_reports", Int::class.java)!!

    @AfterAll
    fun tearDown() {
        for (recipe in listOfNotNull(bobRecipe, bobCopy)) {
            graphql("""mutation { moveToTrash(recipeId: "$recipe") { id } }""", bobToken)
            graphql("""mutation { deletePermanently(recipeId: "$recipe") }""", bobToken)
        }
        if (::familyRecipe.isInitialized) {
            graphql("""mutation { moveToTrash(recipeId: "$familyRecipe") { id } }""", adminToken)
            graphql("""mutation { deletePermanently(recipeId: "$familyRecipe") }""", adminToken)
        }
        jdbc.update("DELETE FROM content_reports")
        jdbc.update("DELETE FROM user_blocks")
        userRepository.findByUsername("deletion-carol").ifPresent { userRepository.delete(it) }
        userRepository.findByUsername("deletion-bob").ifPresent { userRepository.delete(it) }
        userRepository.findByUsername("deletion-admin").ifPresent { userRepository.delete(it) }
        uploads.toFile().deleteRecursively()
    }

    // --- deletion ---------------------------------------------------------------------------

    @Test
    @Order(1)
    fun `wrong password deletes nothing`() {
        val response = graphql("""mutation { deleteAccount(password: "not-it") }""", aliceToken)
        assertTrue(response.contains("\"code\":\"WRONG_PASSWORD\""), response)
        assertTrue(userRepository.findByUsername("deletion-alice").isPresent)
        assertTrue(recipeRepository.findById(UUID.fromString(aliceRecipe)).isPresent)
    }

    @Test
    @Order(2)
    fun `admins and the family account cannot delete themselves`() {
        val family = graphql("""mutation { deleteAccount(password: "password") }""", familyToken)
        assertTrue(family.contains("\"code\":\"ADMIN_CANNOT_DELETE\""), family)
        val admin = graphql("""mutation { deleteAccount(password: "password123") }""", adminToken)
        assertTrue(admin.contains("\"code\":\"ADMIN_CANNOT_DELETE\""), admin)
        assertTrue(userRepository.findByUsername("family").isPresent)
    }

    @Test
    @Order(3)
    fun `deleting an account removes everything it owns and nothing else`() {
        val aliceId = userId("deletion-alice")
        val response = graphql("""mutation { deleteAccount(password: "password123") }""", aliceToken)
        assertTrue(response.contains("\"deleteAccount\":true"), response)

        assertFalse(userRepository.findById(UUID.fromString(aliceId)).isPresent)
        assertFalse(recipeRepository.findById(UUID.fromString(aliceRecipe)).isPresent)
        assertFalse(recipeRepository.findById(UUID.fromString(aliceTrashed)).isPresent)
        assertFalse(recipeTrashRepository.existsById(UUID.fromString(aliceTrashed)))
        assertTrue(passwordResetTokenRepository.findAll().none { it.user.id.toString() == aliceId })
        assertFalse(blacklistedTokenRepository.existsByToken(aliceLoggedOut), "logged-out token outlived the account")

        // Bob's recipe stays, without Alice in its share list.
        val stew = graphql("""query { recipe(id: "$bobRecipe") { name sharedWith { username } } }""", bobToken)
        assertTrue(stew.contains("Bob stew") && !stew.contains("deletion-alice"), stew)
        // Bob's copy stays, still pointing at its (now deleted) original.
        val copy = graphql("""query { recipe(id: "$bobCopy") { copiedFromId photoUrls } }""", bobToken)
        assertTrue(copy.contains(aliceRecipe) && copy.contains("/uploads/copied.jpg"), copy)

        // Reports Alice made and reports about her or her recipes are gone; Bob's other one stays.
        assertEquals(listOf("unrelated"), jdbc.queryForList("SELECT comment FROM content_reports", String::class.java))

        // Only the photo nobody else uses is removed from disk.
        assertFalse(Files.exists(uploads.resolve("alice-only.jpg")))
        assertTrue(Files.exists(uploads.resolve("copied.jpg")))
        // Nothing remembers what Alice uploaded.
        assertTrue(photoUploadRepository.findAll().none { it.uploaderId.toString() == aliceId })
        // The family collection is untouched.
        assertTrue(graphql("query { recipes { id } }", familyToken).split("\"id\"").size - 1 >= 4)
    }

    @Test
    @Order(4)
    fun `a deleted account can no longer sign in or use its old token`() {
        val login = graphql("""mutation { login(username: "deletion-alice", password: "password123") }""")
        assertTrue(login.contains("\"code\":\"INVALID_CREDENTIALS\""), login)

        // The old token is treated as a guest: a clear auth error, not a server crash.
        val me = graphql("query { me { username } }", aliceToken)
        assertTrue(me.contains("\"errors\"") && !me.contains("INTERNAL_ERROR"), me)
    }

    // --- admin role ---------------------------------------------------------------------------

    @Test
    @Order(5)
    fun `an admin manages the family collection from their own account`() {
        familyRecipe = id(graphql("""mutation { createRecipe(name: "Family borscht", familyCollection: true) { id user { username } } }""", adminToken))
        val owner = graphql("""query { recipe(id: "$familyRecipe") { user { username } canEdit } }""", adminToken)
        assertTrue(owner.contains("\"username\":\"family\"") && owner.contains("\"canEdit\":true"), owner)

        val renamed = graphql("""mutation { updateRecipe(recipeId: "$familyRecipe", name: "Family borscht v2", isPublic: true) { name } }""", adminToken)
        assertTrue(renamed.contains("Family borscht v2"), renamed)
    }

    @Test
    @Order(6)
    fun `a regular user cannot edit family recipes or add to the collection`() {
        val view = graphql("""query { recipe(id: "$familyRecipe") { canEdit } }""", bobToken)
        assertTrue(view.contains("\"canEdit\":false"), view)

        val edit = graphql("""mutation { updateRecipe(recipeId: "$familyRecipe", name: "Hacked") { name } }""", bobToken)
        assertTrue(edit.contains("\"errors\""), edit)

        val create = graphql("""mutation { createRecipe(name: "Sneaky", familyCollection: true) { id } }""", bobToken)
        assertTrue(create.contains("\"errors\""), create)
    }

    @Test
    @Order(7)
    fun `guests never get canEdit`() {
        val guest = graphql("""query { recipe(id: "$familyRecipe") { canEdit } }""")
        assertTrue(guest.contains("\"canEdit\":false"), guest)
    }

    @Test
    @Order(8)
    fun `isAdmin is only ever true on your own record`() {
        assertTrue(graphql("query { me { isAdmin } }", adminToken).contains("\"isAdmin\":true"))
        assertTrue(graphql("query { me { isAdmin } }", bobToken).contains("\"isAdmin\":false"))
        val others = graphql("""query { recipe(id: "$familyRecipe") { user { username isAdmin } } }""", bobToken)
        assertTrue(others.contains("\"isAdmin\":false"), others)
    }

    @Test
    @Order(9)
    fun `a listed admin name with no account is reserved`() {
        val response = graphql(
            """mutation { createUser(username: "deletion-reserved", email: "reserved@test.com", password: "password123", inviteCode: "account-deletion-invite") }"""
        )
        assertTrue(response.contains("\"code\":\"USERNAME_TAKEN\""), response)
        assertFalse(userRepository.findByUsername("deletion-reserved").isPresent)
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun signUp(username: String): String = graphql(
        """mutation { createUser(username: "$username", email: "$username@test.com", password: "password123", inviteCode: "account-deletion-invite") }"""
    ).substringAfter("\"createUser\":\"").substringBefore("\"")

    private fun login(username: String, password: String): String =
        graphql("""mutation { login(username: "$username", password: "$password") }""")
            .substringAfter("\"login\":\"").substringBefore("\"")

    private fun user(username: String): User = userRepository.findByUsername(username).orElseThrow()

    private fun userId(username: String): String = user(username).id.toString()

    private fun id(response: String): String {
        assertFalse(response.contains("\"errors\""), response)
        return response.substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun buildBody(query: String): String {
        val escaped = query.replace("\"", "\\\"").replace("\n", " ")
        return """{"query":"$escaped"}"""
    }

    private fun graphql(query: String, authToken: String? = null): String {
        val request = post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(buildBody(query))
        if (authToken != null) {
            request.header("Authorization", "Bearer $authToken")
        }
        val result = mockMvc.perform(request).andReturn()
        assertEquals(200, result.response.status, result.response.contentAsString)
        return result.response.contentAsString
    }
}

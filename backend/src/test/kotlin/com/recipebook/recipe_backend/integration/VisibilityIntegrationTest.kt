package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.recipe.VisibilityBackfill
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three visibility levels end to end: who can read a recipe at each level, which list it
 * turns up in, what apps released before the levels see and send (`isPublic`), and the start-up
 * backfill from the old `is_public` column.
 *
 * Shares the default test context, so everything it creates is removed in [tearDown]:
 * SeedDataIntegrationTest asserts exact seed counts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisibilityIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var recipeRepository: RecipeRepository
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var backfill: VisibilityBackfill

    private val api by lazy { Api(mockMvc) }
    lateinit var owner: User
    lateinit var reader: User
    lateinit var ownerToken: String
    lateinit var readerToken: String
    private val created = mutableListOf<String>()

    private fun user(name: String): User =
        userRepository.save(User(username = name, email = "$name-${System.nanoTime()}@test.com", password = "x", termsAcceptedAt = java.time.Instant.now()))

    @BeforeAll
    fun setUp() {
        owner = user("visibility-owner")
        reader = user("visibility-reader")
        ownerToken = jwtService.generateToken(CustomUserDetails(owner))
        readerToken = jwtService.generateToken(CustomUserDetails(reader))
    }

    @AfterAll
    fun tearDown() {
        recipeRepository.deleteAll(recipeRepository.findAllByUser(owner) + recipeRepository.findAllByUser(reader))
        userRepository.deleteAll(listOf(owner, reader))
    }

    private fun recipe(name: String, level: String? = null): String {
        val arg = if (level == null) "" else ", visibility: $level"
        return api.id(api.graphql("""mutation { createRecipe(name: "$name"$arg) { id } }""", ownerToken)).also { created += it }
    }

    private fun level(id: String): String =
        api.graphql("""{ recipe(id: "$id") { visibility } }""", ownerToken).substringAfter("\"visibility\":\"").substringBefore("\"")

    private fun update(id: String, args: String): String =
        api.graphql("""mutation { updateRecipe(recipeId: "$id", $args) { id visibility isPublic } }""", ownerToken)

    private fun readable(id: String, token: String?): Boolean =
        api.graphql("""{ recipe(id: "$id") { id } }""", token).contains("\"id\":\"$id\"")

    private fun listed(query: String, id: String, token: String?): Boolean =
        api.graphql("{ $query { id } }", token).contains(id)

    @Test
    fun `a new recipe is only me and nobody else can read or list it`() {
        val response = api.graphql("""mutation { createRecipe(name: "Vis private") { id visibility isPublic } }""", ownerToken)
        val id = api.id(response).also { created += it }
        assertTrue(response.contains("\"visibility\":\"ONLY_ME\""), response)
        assertTrue(response.contains("\"isPublic\":false"), response)

        assertTrue(readable(id, ownerToken))
        assertFalse(readable(id, readerToken))
        assertFalse(readable(id, null))
        for (list in listOf("recipes", "sharedWithMeRecipes", "publicRecipes", "searchRecipes(keyword: \"Vis private\", ingredientName: \"Vis private\")")) {
            assertFalse(listed(list, id, readerToken), list)
        }
        assertTrue(listed("myRecipes", id, ownerToken))
    }

    @Test
    fun `a family recipe is for signed-in users only`() {
        val id = recipe("Vis family", "FAMILY")
        val detail = api.graphql("""{ recipe(id: "$id") { visibility isPublic } }""", readerToken)
        assertTrue(detail.contains("\"visibility\":\"FAMILY\""), detail)
        assertTrue(detail.contains("\"isPublic\":false"), "old apps must not think it is public: $detail")

        assertFalse(readable(id, null), "a guest must not read a family recipe")
        assertTrue(listed("recipes", id, readerToken))
        assertTrue(listed("sharedWithMeRecipes", id, readerToken))
        assertTrue(listed("searchRecipes(keyword: \"Vis family\", ingredientName: \"Vis family\")", id, readerToken))
        assertFalse(listed("publicRecipes", id, readerToken))
        assertFalse(listed("publicRecipes", id, null))
        assertFalse(listed("searchRecipes(keyword: \"Vis family\", ingredientName: \"Vis family\")", id, null))
        assertFalse(listed("sharedWithMeRecipes", id, ownerToken), "your own recipe is not shared with you")
    }

    @Test
    fun `a public recipe is for everyone and the old column follows`() {
        val id = recipe("Vis public")
        val response = update(id, "visibility: PUBLIC")
        assertTrue(response.contains("\"isPublic\":true"), response)

        assertTrue(readable(id, null))
        assertTrue(readable(id, readerToken))
        assertTrue(listed("publicRecipes", id, null))
        assertTrue(listed("searchRecipes(keyword: \"Vis public\", ingredientName: \"Vis public\")", id, null))
        assertFalse(listed("sharedWithMeRecipes", id, readerToken), "public recipes have their own section")
        assertEquals(true, jdbc.queryForObject("SELECT is_public FROM recipes WHERE id = ?", Boolean::class.java, UUID.fromString(id)))

        update(id, "visibility: FAMILY")
        assertEquals(false, jdbc.queryForObject("SELECT is_public FROM recipes WHERE id = ?", Boolean::class.java, UUID.fromString(id)))
        assertFalse(readable(id, null))
    }

    @Test
    fun `an app from before the levels can still publish and unpublish`() {
        val id = recipe("Vis legacy")
        assertTrue(update(id, "isPublic: true").contains("\"visibility\":\"PUBLIC\""))
        assertTrue(update(id, "isPublic: false").contains("\"visibility\":\"ONLY_ME\""))
    }

    @Test
    fun `an old app saving a family recipe does not hide it`() {
        val id = recipe("Vis legacy family", "FAMILY")
        // Old apps send the flag on every save, with the value they were shown: false.
        val response = update(id, """name: "Vis legacy family, edited", isPublic: false""")
        assertTrue(response.contains("\"visibility\":\"FAMILY\""), response)
        assertTrue(readable(id, readerToken))
    }

    @Test
    fun `visibility wins over the legacy flag`() {
        val id = recipe("Vis both")
        assertTrue(update(id, "isPublic: true, visibility: FAMILY").contains("\"visibility\":\"FAMILY\""))
    }

    @Test
    fun `a copy of someone's recipe starts as only me`() {
        val id = recipe("Vis to copy", "FAMILY")
        val copy = api.graphql("""mutation { copyRecipe(recipeId: "$id") { id visibility } }""", readerToken)
        assertTrue(copy.contains("\"visibility\":\"ONLY_ME\""), copy)
    }

    @Test
    fun `backfill lifts old public rows and repairs what a rollback leaves`() {
        fun insert(isPublic: Boolean, visibility: String?): UUID {
            val id = UUID.randomUUID()
            if (visibility == null) {
                // What the previous image and the seed files write: no visibility at all.
                jdbc.update("INSERT INTO recipes (id, name, is_public, is_trashed, user_id) VALUES (?, 'Vis backfill', ?, false, ?)", id, isPublic, owner.id)
            } else {
                jdbc.update("INSERT INTO recipes (id, name, is_public, is_trashed, user_id, visibility) VALUES (?, 'Vis backfill', ?, false, ?, ?)", id, isPublic, owner.id, visibility)
            }
            return id
        }
        fun stored(id: UUID) = jdbc.queryForObject("SELECT visibility FROM recipes WHERE id = ?", String::class.java, id)

        val oldPublic = insert(true, null)
        val oldPrivate = insert(false, null)
        val family = insert(false, "FAMILY")
        val publishedOnOldImage = insert(true, "FAMILY")
        val unpublishedOnOldImage = insert(false, "PUBLIC")
        assertEquals("ONLY_ME", stored(oldPublic), "the column default must not expose anything")

        repeat(2) { backfill.run(DefaultApplicationArguments()) }

        assertEquals("PUBLIC", stored(oldPublic))
        assertEquals("ONLY_ME", stored(oldPrivate))
        assertEquals("FAMILY", stored(family))
        assertEquals("PUBLIC", stored(publishedOnOldImage))
        assertEquals("ONLY_ME", stored(unpublishedOnOldImage))
    }

    @Test
    fun `seeded recipes come out public`() {
        val seeded = jdbc.queryForObject(
            "SELECT count(*) FROM recipes r JOIN users u ON u.id = r.user_id WHERE u.username = 'family' AND r.is_public = true AND r.visibility <> 'PUBLIC'",
            Int::class.java
        )
        assertEquals(0, seeded)
        assertTrue(api.graphql("{ publicRecipes { id } }").contains("\"id\""))
    }
}

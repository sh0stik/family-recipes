package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.security.CustomUserDetails
import com.recipebook.recipe_backend.security.JwtService
import com.recipebook.recipe_backend.user.AccountDeletionService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.time.Instant

/**
 * Shared set-up for the moderation tests (content rules, blocked words, reports, blocks, admin
 * removal, suspension). One Spring context for all of them: its own invite code because some
 * register users, its own admin, and a mocked mail sender so a report never leaves the machine and
 * the tests can read what would have been sent.
 *
 * Contexts share one database and SeedDataIntegrationTest asserts exact seed counts, so every
 * account made through [user] is deleted again — recipes, reports and blocks with it — in
 * [deleteTestAccounts].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "recipe.invite-code=moderation-invite",
        "recipe.admins=family,moderation-admin",
        "recipe.moderation.report-email=reports@test.local",
        // The mail health check wants the real sender bean, which the mock below replaces.
        "management.health.mail.enabled=false",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ModerationTestBase {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var recipeRepository: RecipeRepository
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var accountDeletionService: AccountDeletionService
    @MockitoBean lateinit var mailSender: JavaMailSender

    protected val api by lazy { Api(mockMvc) }
    private val accounts = mutableListOf<String>()

    /** A stored account. [accepted]: whether it has accepted the content rules. */
    protected fun user(name: String, accepted: Boolean = true): User {
        accounts += name
        return userRepository.save(
            User(username = name, email = "$name@test.com", password = "x", termsAcceptedAt = if (accepted) Instant.now() else null)
        )
    }

    /** Registers [name] for clean-up when the test created it through the API instead. */
    protected fun track(name: String) { accounts += name }

    protected fun token(user: User): String = jwtService.generateToken(CustomUserDetails(user))

    protected fun createRecipe(name: String, token: String, level: String? = null): String {
        val arg = if (level == null) "" else ", visibility: $level"
        val response = api.graphql("""mutation { createRecipe(name: "$name"$arg) { id } }""", token)
        check(!api.failed(response)) { "createRecipe failed: $response" }
        return api.id(response)
    }

    protected fun readable(recipeId: String, token: String?): Boolean =
        api.graphql("""{ recipe(id: "$recipeId") { id } }""", token).contains("\"id\":\"$recipeId\"")

    protected fun listed(query: String, recipeId: String, token: String?): Boolean =
        api.graphql("{ $query { id } }", token).contains(recipeId)

    @AfterAll
    fun deleteTestAccounts() {
        for (name in accounts.distinct()) {
            userRepository.findByUsername(name).ifPresent { accountDeletionService.deleteAccount(it) }
        }
    }
}

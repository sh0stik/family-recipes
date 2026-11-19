package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.recipe.Recipe
import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.recipe.Visibility
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.EmailService
import com.recipebook.recipe_backend.security.InvalidInputException
import com.recipebook.recipe_backend.security.RateLimitExceededException
import com.recipebook.recipe_backend.security.RateLimitService
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class ModerationControllerTest {

    @Mock lateinit var reportRepository: ContentReportRepository
    @Mock lateinit var blockRepository: UserBlockRepository
    @Mock lateinit var recipeRepository: RecipeRepository
    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var currentUserService: CurrentUserService
    @Mock lateinit var emailService: EmailService
    @Mock lateinit var rateLimitService: RateLimitService

    private lateinit var controller: ModerationController

    private val reporter = User(id = UUID.randomUUID(), username = "reporter", email = "r@t.com", password = "p")
    private val author = User(id = UUID.randomUUID(), username = "author", email = "a@t.com", password = "p")
    private val admin = User(id = UUID.randomUUID(), username = "admin", email = "ad@t.com", password = "p")
    private val recipeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        controller = ModerationController(
            reportRepository, blockRepository, recipeRepository, userRepository, currentUserService,
            AdminService(admins = "family,admin", familyAccount = "family"), emailService, rateLimitService,
        )
    }

    private fun recipe(level: Visibility = Visibility.PUBLIC, owner: User = author): Recipe =
        Recipe(id = recipeId, name = "Borscht", user = owner).also {
            it.applyVisibility(level)
            whenever(recipeRepository.findById(recipeId)).thenReturn(Optional.of(it))
        }

    private fun refusedWith(code: String, action: () -> Unit) =
        assertEquals(code, assertThrows(InvalidInputException::class.java) { action() }.code)

    @Test
    fun `reportRecipe stores the report with the names of the day and tells the admins`() {
        recipe()
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        val saved = argumentCaptor<ContentReport>()

        assertTrue(controller.reportRecipe(recipeId, ReportReason.SPAM, "  looks like an advert  "))

        verify(reportRepository).save(saved.capture())
        with(saved.firstValue) {
            assertEquals(reporter, this.reporter)
            assertEquals(this@ModerationControllerTest.recipeId, this.recipeId)
            assertEquals("Borscht", recipeName)
            assertEquals(author.id, reportedUserId)
            assertEquals("author", reportedUsername)
            assertEquals(ReportReason.SPAM, reason)
            assertEquals("looks like an advert", comment)
        }
        val lines = argumentCaptor<List<String>>()
        verify(emailService).sendReportEmail(eq("Recipe reported: Borscht"), lines.capture())
        val body = lines.firstValue.joinToString("\n")
        for (expected in listOf("Borscht", recipeId.toString(), "Owner: author", "Reported by: reporter", "Reason: SPAM", "looks like an advert")) {
            assertTrue(body.contains(expected), "$expected missing from:\n$body")
        }
    }

    @Test
    fun `reporting the same recipe twice stores and sends nothing more`() {
        recipe()
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(reportRepository.existsByReporterAndRecipeId(reporter, recipeId)).thenReturn(true)

        assertTrue(controller.reportRecipe(recipeId, ReportReason.OTHER, null))

        verify(reportRepository, never()).save(any<ContentReport>())
        verifyNoInteractions(emailService)
    }

    @Test
    fun `you cannot report a recipe you manage`() {
        recipe(owner = reporter)
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)

        refusedWith("CANNOT_REPORT") { controller.reportRecipe(recipeId, ReportReason.OTHER, null) }
        verify(reportRepository, never()).save(any<ContentReport>())
    }

    @Test
    fun `a recipe you cannot read looks like one that does not exist`() {
        recipe(level = Visibility.ONLY_ME)
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        val hidden = assertThrows(Exception::class.java) { controller.reportRecipe(recipeId, ReportReason.OTHER, null) }

        val missingId = UUID.randomUUID()
        whenever(recipeRepository.findById(missingId)).thenReturn(Optional.empty())
        val missing = assertThrows(Exception::class.java) { controller.reportRecipe(missingId, ReportReason.OTHER, null) }

        assertEquals(missing.message, hidden.message)
        verify(reportRepository, never()).save(any<ContentReport>())
    }

    @Test
    fun `an over-long comment is refused and a blank one is stored as none`() {
        recipe()
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)

        refusedWith("TOO_LONG") { controller.reportRecipe(recipeId, ReportReason.OTHER, "x".repeat(1001)) }

        val saved = argumentCaptor<ContentReport>()
        controller.reportRecipe(recipeId, ReportReason.OTHER, "   ")
        verify(reportRepository).save(saved.capture())
        assertNull(saved.firstValue.comment)
    }

    @Test
    fun `reports are rate limited per account, and a refused one stores nothing`() {
        recipe()
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(rateLimitService.check(eq("report-user:${reporter.id}"), eq(10), any()))
            .thenThrow(RateLimitExceededException("Too many attempts"))

        assertThrows(RateLimitExceededException::class.java) { controller.reportRecipe(recipeId, ReportReason.SPAM, null) }
        verify(reportRepository, never()).save(any<ContentReport>())
        verifyNoInteractions(emailService)
    }

    @Test
    fun `reportUser stores a report about the person`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(userRepository.findById(author.id!!)).thenReturn(Optional.of(author))
        val saved = argumentCaptor<ContentReport>()

        assertTrue(controller.reportUser(author.id!!, ReportReason.INAPPROPRIATE, "rude notes"))

        verify(reportRepository).save(saved.capture())
        assertNull(saved.firstValue.recipeId)
        assertEquals(author.id, saved.firstValue.reportedUserId)
        assertEquals("author", saved.firstValue.reportedUsername)
        verify(emailService).sendReportEmail(eq("User reported: author"), any())
    }

    @Test
    fun `you cannot report yourself, and a second report of the same person is ignored`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(userRepository.findById(reporter.id!!)).thenReturn(Optional.of(reporter))
        refusedWith("CANNOT_REPORT") { controller.reportUser(reporter.id!!, ReportReason.OTHER, null) }

        whenever(userRepository.findById(author.id!!)).thenReturn(Optional.of(author))
        whenever(reportRepository.existsByReporterAndReportedUserIdAndRecipeIdIsNull(reporter, author.id!!)).thenReturn(true)
        assertTrue(controller.reportUser(author.id!!, ReportReason.OTHER, null))

        verify(reportRepository, never()).save(any<ContentReport>())
    }

    @Test
    fun `blockUser stores the block and takes each off the other's editor lists`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(userRepository.findById(author.id!!)).thenReturn(Optional.of(author))
        val saved = argumentCaptor<UserBlock>()

        assertTrue(controller.blockUser(author.id!!))

        verify(blockRepository).save(saved.capture())
        assertEquals(reporter, saved.firstValue.blocker)
        assertEquals(author, saved.firstValue.blocked)
        verify(recipeRepository).removeEditorFromRecipesOf(editorId = author.id!!, ownerId = reporter.id!!)
        verify(recipeRepository).removeEditorFromRecipesOf(editorId = reporter.id!!, ownerId = author.id!!)
    }

    @Test
    fun `blocking someone twice keeps one block`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(userRepository.findById(author.id!!)).thenReturn(Optional.of(author))
        whenever(blockRepository.exists(reporter.id!!, author.id!!)).thenReturn(true)

        assertTrue(controller.blockUser(author.id!!))
        verify(blockRepository, never()).save(any<UserBlock>())
    }

    @Test
    fun `you cannot block yourself or the family collection`() {
        val family = User(id = UUID.randomUUID(), username = "family", email = "f@t.com", password = "p")
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        whenever(userRepository.findById(reporter.id!!)).thenReturn(Optional.of(reporter))
        whenever(userRepository.findById(family.id!!)).thenReturn(Optional.of(family))

        refusedWith("CANNOT_BLOCK") { controller.blockUser(reporter.id!!) }
        refusedWith("CANNOT_BLOCK") { controller.blockUser(family.id!!) }
        verify(blockRepository, never()).save(any<UserBlock>())
    }

    @Test
    fun `unblockUser removes only the caller's own block`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        assertTrue(controller.unblockUser(author.id!!))
        verify(blockRepository).remove(reporter.id!!, author.id!!)
    }

    @Test
    fun `an admin takes any recipe out of view and locks it, with a reason`() {
        val recipe = recipe(level = Visibility.PUBLIC)
        whenever(currentUserService.getCurrentUser()).thenReturn(admin)

        assertTrue(controller.adminUnpublishRecipe(recipeId, "  advertising  "))

        assertEquals(Visibility.ONLY_ME, recipe.visibility)
        assertFalse(recipe.isPublic)
        assertTrue(recipe.publishLocked)
        assertEquals("advertising", recipe.removalReason)
        verify(recipeRepository).save(recipe)

        assertTrue(controller.adminAllowPublish(recipeId))
        assertFalse(recipe.publishLocked)
        assertNull(recipe.removalReason)
        assertEquals(Visibility.ONLY_ME, recipe.visibility, "lifting the lock does not publish anything")
    }

    @Test
    fun `removal needs an admin and a reason`() {
        // Every refusal comes before the recipe is even looked up.
        val recipe = Recipe(id = recipeId, name = "Borscht", user = author, isPublic = true)
        whenever(currentUserService.getCurrentUser()).thenReturn(author)
        refusedWith("ADMIN_ONLY") { controller.adminUnpublishRecipe(recipeId, "mine") }
        refusedWith("ADMIN_ONLY") { controller.adminAllowPublish(recipeId) }

        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        refusedWith("EMPTY_FIELD") { controller.adminUnpublishRecipe(recipeId, "   ") }
        refusedWith("TOO_LONG") { controller.adminUnpublishRecipe(recipeId, "x".repeat(1001)) }

        assertEquals(Visibility.PUBLIC, recipe.visibility)
        assertFalse(recipe.publishLocked)
        verify(recipeRepository, never()).save(any<Recipe>())
    }

    @Test
    fun `only admins read the reports`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(reporter)
        refusedWith("ADMIN_ONLY") { controller.reports() }

        whenever(currentUserService.getCurrentUser()).thenReturn(admin)
        whenever(reportRepository.findAllNewestFirst()).thenReturn(emptyList())
        assertTrue(controller.reports().isEmpty())
    }
}

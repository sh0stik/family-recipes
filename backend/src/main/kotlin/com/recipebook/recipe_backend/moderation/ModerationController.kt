package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.recipe.RecipeRepository
import com.recipebook.recipe_backend.recipe.Visibility
import com.recipebook.recipe_backend.security.AdminService
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.EmailService
import com.recipebook.recipe_backend.security.InputValidation
import com.recipebook.recipe_backend.security.InvalidInputException
import com.recipebook.recipe_backend.security.RateLimitService
import com.recipebook.recipe_backend.security.RequestCache
import com.recipebook.recipe_backend.user.User
import com.recipebook.recipe_backend.user.UserRepository
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

/**
 * What users and admins can do about content that should not be here. Both app stores require
 * it of any app where one person's content reaches another (Apple Guideline 1.2, Google's UGC
 * policy): a way to report content and people, and someone who acts on the reports.
 */
@Controller
class ModerationController(
    private val reportRepository: ContentReportRepository,
    private val blockRepository: UserBlockRepository,
    private val recipeRepository: RecipeRepository,
    private val userRepository: UserRepository,
    private val currentUserService: CurrentUserService,
    private val adminService: AdminService,
    private val emailService: EmailService,
    private val rateLimitService: RateLimitService,
    private val requestCache: RequestCache = RequestCache(),
) {

    /**
     * Reports a recipe to the admins. It is stored, emailed to the moderation address, and the
     * recipe disappears from the reporter's own lists and search straight away — they should not
     * have to keep looking at it while someone decides. Reporting the same recipe again changes
     * nothing.
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun reportRecipe(@Argument recipeId: UUID, @Argument reason: ReportReason, @Argument comment: String?): Boolean {
        val reporter = currentUserService.getCurrentUser()
        val text = cleanComment(comment)
        // "Not found" for a recipe the caller cannot read, so reporting cannot be used to probe ids.
        val recipe = recipeRepository.findById(recipeId).orElse(null)
            ?.takeIf { adminService.canRead(it, reporter) }
            ?: throw Exception("Recipe not found")
        if (adminService.canManage(recipe, reporter)) {
            throw InvalidInputException("CANNOT_REPORT", "You cannot report a recipe you manage")
        }
        limit(reporter)
        if (reportRepository.existsByReporterAndRecipeId(reporter, recipeId)) return true

        val owner = recipe.user
        reportRepository.save(
            ContentReport(
                reporter = reporter,
                recipeId = recipeId,
                recipeName = recipe.name,
                reportedUserId = owner.id,
                reportedUsername = owner.username,
                reason = reason,
                comment = text,
            )
        )
        requestCache.clear()
        emailService.sendReportEmail(
            subject = "Recipe reported: ${recipe.name.take(80)}",
            lines = listOf(
                "A recipe was reported in Mom's Recipes.",
                "",
                "Recipe: ${recipe.name}",
                "Recipe id: $recipeId",
                "Owner: ${owner.username}",
                "Visibility: ${recipe.visibility}",
                "Reported by: ${reporter.username}",
                "Reason: $reason",
                "Comment: ${text ?: "-"}",
                "",
                "To take it out of view, sign in as an admin, open the recipe and choose \"Remove from public\".",
            ),
        )
        return true
    }

    /** Reports a person rather than one recipe. Stored and emailed like [reportRecipe]. */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun reportUser(@Argument userId: UUID, @Argument reason: ReportReason, @Argument comment: String?): Boolean {
        val reporter = currentUserService.getCurrentUser()
        val text = cleanComment(comment)
        val reported = userRepository.findById(userId).orElseThrow { Exception("User not found") }
        if (reported.id == reporter.id) throw InvalidInputException("CANNOT_REPORT", "You cannot report yourself")
        limit(reporter)
        if (reportRepository.existsByReporterAndReportedUserIdAndRecipeIdIsNull(reporter, userId)) return true

        reportRepository.save(
            ContentReport(
                reporter = reporter,
                reportedUserId = reported.id,
                reportedUsername = reported.username,
                reason = reason,
                comment = text,
            )
        )
        emailService.sendReportEmail(
            subject = "User reported: ${reported.username}",
            lines = listOf(
                "A user was reported in Mom's Recipes.",
                "",
                "User: ${reported.username}",
                "User id: ${reported.id}",
                "Reported by: ${reporter.username}",
                "Reason: $reason",
                "Comment: ${text ?: "-"}",
                "",
                "To stop the account, set users.suspended = true in the database.",
            ),
        )
        return true
    }

    /**
     * Blocks a person: the caller stops getting their recipes and notes anywhere, and they stop
     * getting the caller's Family recipes (AdminService.canRead). Each is taken off the other's
     * editor lists and cannot be put back while the block stands. Blocking twice changes nothing.
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    @Transactional
    fun blockUser(@Argument userId: UUID): Boolean {
        val me = currentUserService.getCurrentUser()
        val other = userRepository.findById(userId).orElseThrow { Exception("User not found") }
        if (other.id == me.id) throw InvalidInputException("CANNOT_BLOCK", "You cannot block yourself")
        // The family account is the shared collection, not a person; hiding it would empty the app.
        if (other.username == adminService.familyAccount) {
            throw InvalidInputException("CANNOT_BLOCK", "The family collection cannot be blocked")
        }
        if (!blockRepository.exists(me.id!!, userId)) {
            blockRepository.save(UserBlock(blocker = me, blocked = other))
            blockRepository.flush()
        }
        recipeRepository.removeEditorFromRecipesOf(editorId = userId, ownerId = me.id!!)
        recipeRepository.removeEditorFromRecipesOf(editorId = me.id!!, ownerId = userId)
        requestCache.clear()
        return true
    }

    /** Lifts a block. Editor rights the block removed do not come back by themselves. */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    @Transactional
    fun unblockUser(@Argument userId: UUID): Boolean {
        val me = currentUserService.getCurrentUser()
        blockRepository.remove(me.id!!, userId)
        requestCache.clear()
        return true
    }

    /** The people the caller blocked, most recent first — for the "Blocked users" screen. */
    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun blockedUsers(): List<User> = blockRepository.findBlockedUsers(currentUserService.getCurrentUser().id!!)

    /**
     * An admin takes a recipe out of view — any recipe, not only the family collection's. It goes
     * back to Only me and is locked: its owner keeps it and can edit it, but cannot share it again
     * (REMOVED_BY_ADMIN) until an admin calls [adminAllowPublish]. The owner is shown [reason].
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun adminUnpublishRecipe(@Argument recipeId: UUID, @Argument reason: String): Boolean {
        requireAdmin(currentUserService.getCurrentUser())
        val why = InputValidation.text(reason.trim(), MAX_COMMENT, "Reason", required = true)
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        recipe.applyVisibility(Visibility.ONLY_ME)
        recipe.publishLocked = true
        recipe.removalReason = why
        recipeRepository.save(recipe)
        return true
    }

    /** Lifts the lock [adminUnpublishRecipe] set. The recipe stays Only me until its owner shares it again. */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun adminAllowPublish(@Argument recipeId: UUID): Boolean {
        requireAdmin(currentUserService.getCurrentUser())
        val recipe = recipeRepository.findById(recipeId).orElseThrow { Exception("Recipe not found") }
        recipe.publishLocked = false
        recipe.removalReason = null
        recipeRepository.save(recipe)
        return true
    }

    /** Every report, newest first. Admins only. */
    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    fun reports(): List<ContentReport> {
        requireAdmin(currentUserService.getCurrentUser())
        return reportRepository.findAllNewestFirst()
    }

    @SchemaMapping(typeName = "Report", field = "reporterUsername")
    fun reporterUsername(report: ContentReport): String = report.reporter.username

    private fun requireAdmin(user: User) {
        if (!adminService.isAdmin(user)) throw InvalidInputException("ADMIN_ONLY", "Only admins can do this")
    }

    private fun cleanComment(comment: String?): String? =
        comment?.trim()?.takeIf { it.isNotEmpty() }?.also { InputValidation.text(it, MAX_COMMENT, "Comment") }

    // Each report sends mail, so one account must not be able to flood the moderation inbox.
    private fun limit(reporter: User) =
        rateLimitService.check("report-user:${reporter.id}", limit = REPORTS_PER_HOUR, window = Duration.ofHours(1))

    companion object {
        const val MAX_COMMENT = 1_000
        const val REPORTS_PER_HOUR = 10
    }
}

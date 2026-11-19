package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ContentReportRepository : JpaRepository<ContentReport, UUID> {
    fun existsByReporterAndRecipeId(reporter: User, recipeId: UUID): Boolean

    /** Whether [reporter] already reported the person [reportedUserId] (as opposed to one of their recipes). */
    fun existsByReporterAndReportedUserIdAndRecipeIdIsNull(reporter: User, reportedUserId: UUID): Boolean

    /** The recipes [userId] reported, which are hidden from their own lists. */
    @Query("SELECT r.recipeId FROM ContentReport r WHERE r.reporter.id = :userId AND r.recipeId IS NOT NULL")
    fun findReportedRecipeIds(@Param("userId") userId: UUID): List<UUID>

    @Query("SELECT r FROM ContentReport r JOIN FETCH r.reporter ORDER BY r.createdAt DESC")
    fun findAllNewestFirst(): List<ContentReport>

    /** Every report [userId] made and every report about them or their recipes — used by account deletion. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ContentReport r WHERE r.reporter.id = :userId OR r.reportedUserId = :userId")
    fun deleteAllInvolving(@Param("userId") userId: UUID): Int
}

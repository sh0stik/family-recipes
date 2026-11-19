package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserBlockRepository : JpaRepository<UserBlock, UUID> {
    @Query("SELECT COUNT(b) > 0 FROM UserBlock b WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId")
    fun exists(@Param("blockerId") blockerId: UUID, @Param("blockedId") blockedId: UUID): Boolean

    /** Whether either of the two has blocked the other. */
    @Query("""
        SELECT COUNT(b) > 0 FROM UserBlock b
        WHERE (b.blocker.id = :a AND b.blocked.id = :b) OR (b.blocker.id = :b AND b.blocked.id = :a)
    """)
    fun existsBetween(@Param("a") a: UUID, @Param("b") b: UUID): Boolean

    /** The people [userId] blocked. */
    @Query("SELECT b.blocked.id FROM UserBlock b WHERE b.blocker.id = :userId")
    fun findBlockedIds(@Param("userId") userId: UUID): List<UUID>

    /** The people who blocked [userId]. */
    @Query("SELECT b.blocker.id FROM UserBlock b WHERE b.blocked.id = :userId")
    fun findBlockerIds(@Param("userId") userId: UUID): List<UUID>

    @Query("SELECT b.blocked FROM UserBlock b WHERE b.blocker.id = :userId ORDER BY b.createdAt DESC")
    fun findBlockedUsers(@Param("userId") userId: UUID): List<User>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserBlock b WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId")
    fun remove(@Param("blockerId") blockerId: UUID, @Param("blockedId") blockedId: UUID): Int

    /** Every block [userId] made and every block of them — used by account deletion. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserBlock b WHERE b.blocker.id = :userId OR b.blocked.id = :userId")
    fun deleteAllInvolving(@Param("userId") userId: UUID): Int
}

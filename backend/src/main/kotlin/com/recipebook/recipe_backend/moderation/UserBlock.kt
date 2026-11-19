package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/**
 * [blocker] does not want to see [blocked]'s content, or be seen by them beyond what is public.
 * What that means for reading recipes is in security/AdminService.canRead.
 */
@Entity
@Table(name = "user_blocks", uniqueConstraints = [UniqueConstraint(columnNames = ["blocker_id", "blocked_id"])])
class UserBlock(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    val blocker: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    val blocked: User,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: Instant? = null,
)

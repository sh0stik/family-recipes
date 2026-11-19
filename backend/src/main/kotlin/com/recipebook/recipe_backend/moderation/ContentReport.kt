package com.recipebook.recipe_backend.moderation

import com.recipebook.recipe_backend.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

enum class ReportReason { INAPPROPRIATE, SPAM, NOT_THEIRS, OTHER }

/**
 * Someone telling the admins that a recipe, or a person, should not be here.
 *
 * What was reported is stored as plain ids with the names as they were at the time, not as
 * references: a report must still make sense to an admin after the recipe has been deleted or
 * renamed, and must never stand in the way of deleting it. [recipeId] is null for a report about
 * a person; [reportedUserId] is that person, or the recipe's owner.
 */
@Entity
@Table(name = "content_reports")
class ContentReport(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User,

    @Column(name = "recipe_id")
    val recipeId: UUID? = null,

    @Column(name = "recipe_name", columnDefinition = "TEXT")
    val recipeName: String? = null,

    @Column(name = "reported_user_id")
    val reportedUserId: UUID? = null,

    @Column(name = "reported_username")
    val reportedUsername: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val reason: ReportReason,

    @Column(columnDefinition = "TEXT")
    val comment: String? = null,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: Instant? = null,
)

package com.recipebook.recipe_backend.translation

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "entity_translations",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["entity_type", "entity_id", "field_name", "locale"])
    ]
)
data class EntityTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "entity_type", nullable = false)
    val entityType: String,

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @Column(name = "field_name", nullable = false)
    val fieldName: String,

    @Column(nullable = false, length = 10)
    val locale: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val value: String
)

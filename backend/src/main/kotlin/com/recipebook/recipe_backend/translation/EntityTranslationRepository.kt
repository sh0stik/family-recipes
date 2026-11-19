package com.recipebook.recipe_backend.translation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EntityTranslationRepository : JpaRepository<EntityTranslation, UUID> {

    fun findByEntityTypeAndEntityIdAndFieldNameAndLocale(
        entityType: String,
        entityId: UUID,
        fieldName: String,
        locale: String
    ): EntityTranslation?

    fun findByEntityTypeAndEntityIdInAndFieldNameAndLocale(
        entityType: String,
        entityIds: List<UUID>,
        fieldName: String,
        locale: String
    ): List<EntityTranslation>
}

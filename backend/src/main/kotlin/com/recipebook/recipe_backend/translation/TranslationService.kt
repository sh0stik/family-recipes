package com.recipebook.recipe_backend.translation

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TranslationService(
    private val entityTranslationRepository: EntityTranslationRepository
) {

    fun translate(entityType: String, entityId: UUID, fieldName: String, locale: String): String? {
        if (locale == "en") return null
        return entityTranslationRepository
            .findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, fieldName, locale)
            ?.value
    }

    fun translateBatch(
        entityType: String,
        entityIds: List<UUID>,
        fieldName: String,
        locale: String
    ): Map<UUID, String> {
        if (locale == "en" || entityIds.isEmpty()) return emptyMap()
        return entityTranslationRepository
            .findByEntityTypeAndEntityIdInAndFieldNameAndLocale(entityType, entityIds, fieldName, locale)
            .associate { it.entityId to it.value }
    }
}

package com.recipebook.recipe_backend.translation

import com.recipebook.recipe_backend.category.Category
import com.recipebook.recipe_backend.ingredient.Ingredient
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.unit.MeasurementUnit
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class TranslationController(
    private val translationService: TranslationService,
    private val currentUserService: CurrentUserService
) {

    @SchemaMapping(typeName = "Ingredient", field = "name")
    fun ingredientName(ingredient: Ingredient): String {
        val locale = getCurrentLocale()
        if (locale == "en") return ingredient.name
        return translationService.translate("ingredient", ingredient.id!!, "name", locale)
            ?: ingredient.name
    }

    @SchemaMapping(typeName = "Category", field = "name")
    fun categoryName(category: Category): String {
        val locale = getCurrentLocale()
        if (locale == "en") return category.name
        return translationService.translate("category", category.id!!, "name", locale)
            ?: category.name
    }

    @SchemaMapping(typeName = "Unit", field = "name")
    fun unitName(unit: MeasurementUnit): String {
        val locale = getCurrentLocale()
        if (locale == "en") return unit.name
        return translationService.translate("unit", unit.id!!, "name", locale)
            ?: unit.name
    }

    @SchemaMapping(typeName = "Unit", field = "abbreviation")
    fun unitAbbreviation(unit: MeasurementUnit): String {
        val locale = getCurrentLocale()
        if (locale == "en") return unit.abbreviation
        return translationService.translate("unit", unit.id!!, "abbreviation", locale)
            ?: unit.abbreviation
    }

    private fun getCurrentLocale(): String {
        return try {
            currentUserService.getCurrentUser().preferredLocale
        } catch (e: Exception) {
            "en"
        }
    }
}

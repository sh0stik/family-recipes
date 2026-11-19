package com.recipebook.recipe_backend.nutrition

import com.recipebook.recipe_backend.recipe.Recipe
import com.recipebook.recipe_backend.recipe.RecipeIngredient
import com.recipebook.recipe_backend.unit.UnitType
import org.springframework.stereotype.Service

@Service
class NutritionService(
    private val ingredientUnitWeightRepository: IngredientUnitWeightRepository,
) {

    /**
     * (ingredientId, unitId) -> grams, loaded once and reused.
     *
     * Nutrition is computed for every recipe, so looking these up per ingredient would add an N+1.
     * The table is small (one row per ingredient/unit pair that anyone has needed) and changes only
     * when someone adds an ingredient, so it is cached for the lifetime of the process. A restart
     * picks up new rows; that is acceptable for data of this kind.
     */
    @Volatile
    private var weightCache: Map<Pair<String, String>, Float>? = null

    private fun weights(): Map<Pair<String, String>, Float> =
        weightCache ?: ingredientUnitWeightRepository.findAllWithRefs()
            .mapNotNull { w ->
                val ingredientId = w.ingredient.id?.toString()
                val unitId = w.unit.id?.toString()
                if (ingredientId == null || unitId == null) null else (ingredientId to unitId) to w.grams
            }
            .toMap()
            .also { weightCache = it }

    /** Called after the weight table changes, so the next calculation reflects it. */
    fun invalidateWeightCache() {
        weightCache = null
    }

    fun calculate(recipe: Recipe): NutritionInfo? {
        val ingredients = recipe.ingredients
        if (ingredients.isEmpty()) return null

        val weightIngredients = ingredients.mapNotNull { ri ->
            toGrams(ri)?.let { grams -> ri to grams }
        }
        if (weightIngredients.isEmpty()) return null

        val totalWeight = weightIngredients.sumOf { it.second.toDouble() }.toFloat()
        if (totalWeight == 0f) return null

        var totalCalories = 0f
        var totalProtein = 0f
        var totalFat = 0f
        var totalCarbs = 0f

        for ((ri, grams) in weightIngredients) {
            val factor = grams / 100f
            totalCalories += ri.ingredient.caloriesPer100g * factor
            totalProtein += ri.ingredient.proteinPer100g * factor
            totalFat += ri.ingredient.fatPer100g * factor
            totalCarbs += ri.ingredient.carbsPer100g * factor
        }

        val normFactor = 100f / totalWeight

        return NutritionInfo(
            caloriesPer100g = totalCalories * normFactor,
            proteinPer100g = totalProtein * normFactor,
            fatPer100g = totalFat * normFactor,
            carbsPer100g = totalCarbs * normFactor,
            totalWeight = totalWeight
        )
    }

    /**
     * Grams contributed by one recipe ingredient, or null when it cannot be established.
     *
     * Returning null excludes the ingredient from both the weight and the macros, so it does not
     * count as zero calories and drag the per-100g figures down. Resolution order: an explicit
     * weight for this ingredient in this unit, then the unit's own weight where that is
     * ingredient-independent, then the plain weight units.
     */
    private fun toGrams(ri: RecipeIngredient): Float? {
        // No unit is how "to taste" is recorded, and those carry quantity 0.
        val unit = ri.unit ?: return ri.quantity

        val ingredientId = ri.ingredient.id?.toString()
        val unitId = unit.id?.toString()
        if (ingredientId != null && unitId != null) {
            weights()[ingredientId to unitId]?.let { return it * ri.quantity }
        }

        unit.gramsPerUnit?.let { return it * ri.quantity }

        return if (unit.type == UnitType.WEIGHT) {
            when (unit.abbreviation) {
                "kg", "кг" -> ri.quantity * 1000f
                "g", "г" -> ri.quantity
                "mg", "мг" -> ri.quantity / 1000f
                else -> null
            }
        } else {
            null
        }
    }
}

package com.recipebook.recipe_backend.ingredient

import com.recipebook.recipe_backend.moderation.PublishingRules
import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.InputValidation
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class IngredientController(
    val ingredientRepository: IngredientRepository,
    val currentUserService: CurrentUserService,
    val publishingRules: PublishingRules = PublishingRules(),
) {

    @QueryMapping
    fun allIngredients(): List<Ingredient> {
        return ingredientRepository.findAll()
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    fun createIngredient(
        @Argument name: String,
        @Argument caloriesPer100g: Float,
        @Argument proteinPer100g: Float,
        @Argument fatPer100g: Float,
        @Argument carbsPer100g: Float
    ): Ingredient {
        // The ingredient list is shared by every user, so what one person adds everyone sees.
        val newIngredient = Ingredient(
            name = InputValidation.text(name.trim(), InputValidation.MAX_INGREDIENT_NAME, "Ingredient name", required = true),
            caloriesPer100g = InputValidation.per100g(caloriesPer100g, "Calories"),
            proteinPer100g = InputValidation.per100g(proteinPer100g, "Protein"),
            fatPer100g = InputValidation.per100g(fatPer100g, "Fat"),
            carbsPer100g = InputValidation.per100g(carbsPer100g, "Carbs")
        )
        // Hence the content rules and the blocked words, as for a recipe that is being shared.
        publishingRules.checkIngredient(currentUserService.getCurrentUser(), newIngredient.name)
        return ingredientRepository.save(newIngredient)
    }
}

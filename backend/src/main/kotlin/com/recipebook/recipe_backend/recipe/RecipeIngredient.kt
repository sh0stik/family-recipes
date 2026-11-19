package com.recipebook.recipe_backend.recipe

import com.recipebook.recipe_backend.ingredient.Ingredient
import com.recipebook.recipe_backend.unit.MeasurementUnit
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "recipe_ingredients")
data class RecipeIngredient(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val quantity: Float,

    @ManyToOne
    @JoinColumn(name = "unit_id")
    val unit: MeasurementUnit? = null,

    @ManyToOne
    @JoinColumn(name = "ingredient_id")
    val ingredient: Ingredient,

    @ManyToOne
    @JoinColumn(name = "recipe_id")
    val recipe: Recipe? = null
)
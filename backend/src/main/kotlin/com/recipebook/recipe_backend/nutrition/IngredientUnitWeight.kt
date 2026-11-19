package com.recipebook.recipe_backend.nutrition

import com.recipebook.recipe_backend.ingredient.Ingredient
import com.recipebook.recipe_backend.unit.MeasurementUnit
import jakarta.persistence.*
import java.util.UUID

/**
 * How much one unit of a specific ingredient weighs, in grams.
 *
 * Needed because a unit's weight depends on what is being measured: a tablespoon of salt is ~25 g
 * and one of flour ~10 g; one egg is 60 g and one cabbage 1500 g. A single number per unit cannot
 * express that, so nutrition was previously computed only from ingredients given in grams — about a
 * quarter of them — and the resulting per-100g figures were wrong for most recipes.
 *
 * `MeasurementUnit.gramsPerUnit` remains the generic fallback for units whose weight really is
 * ingredient-independent (millilitres, litres), so this table only needs rows where it matters.
 */
@Entity
@Table(
    name = "ingredient_unit_weights",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ingredient_id", "unit_id"])]
)
data class IngredientUnitWeight(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    val ingredient: Ingredient,

    @ManyToOne(optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    val unit: MeasurementUnit,

    /** Grams in one of [unit] of [ingredient]. Approximate by nature — a cook's measure, not a scale. */
    @Column(name = "grams", nullable = false)
    val grams: Float,
)

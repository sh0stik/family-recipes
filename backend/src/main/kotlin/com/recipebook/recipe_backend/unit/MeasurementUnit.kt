package com.recipebook.recipe_backend.unit

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "units")
data class MeasurementUnit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val abbreviation: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: UnitType,

    /**
     * Grams in one of this unit, when that does not depend on the ingredient — millilitres, litres.
     * Null for units whose weight varies with what is measured (шт, стакан, ст. л.); those are
     * resolved per ingredient through `ingredient_unit_weights`, and an ingredient with no row
     * there is left out of the nutrition calculation rather than guessed at.
     */
    @Column(name = "grams_per_unit")
    val gramsPerUnit: Float? = null
)

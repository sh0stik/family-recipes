package com.recipebook.recipe_backend.nutrition

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IngredientUnitWeightRepository : JpaRepository<IngredientUnitWeight, UUID> {

    /**
     * Every known weight, fetched in one query and cached per calculation. Loading these lazily per
     * ingredient would add an N+1 to nutrition, which is computed for every recipe in the list.
     */
    @Query("SELECT w FROM IngredientUnitWeight w JOIN FETCH w.ingredient JOIN FETCH w.unit")
    fun findAllWithRefs(): List<IngredientUnitWeight>
}

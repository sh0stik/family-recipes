package com.recipebook.recipe_backend.unit

import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class UnitController(val measurementUnitRepository: MeasurementUnitRepository) {

    @QueryMapping
    fun allUnits(): List<MeasurementUnit> {
        return measurementUnitRepository.findAll()
    }
}

package com.recipebook.recipe_backend.unit

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MeasurementUnitRepository : JpaRepository<MeasurementUnit, UUID>

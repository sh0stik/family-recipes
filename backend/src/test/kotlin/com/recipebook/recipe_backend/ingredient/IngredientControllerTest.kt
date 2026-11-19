package com.recipebook.recipe_backend.ingredient

import com.recipebook.recipe_backend.security.CurrentUserService
import com.recipebook.recipe_backend.security.InvalidInputException
import com.recipebook.recipe_backend.user.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class IngredientControllerTest {

    @Mock lateinit var ingredientRepository: IngredientRepository
    @Mock lateinit var currentUserService: CurrentUserService

    private fun user(accepted: Boolean) = User(
        id = UUID.randomUUID(), username = "cook", email = "cook@t.com", password = "p",
        termsAcceptedAt = if (accepted) Instant.now() else null,
    )

    @Test
    fun `allIngredients returns all`() {
        val ingredients = listOf(
            Ingredient(name = "Salt"),
            Ingredient(name = "Pepper")
        )
        whenever(ingredientRepository.findAll()).thenReturn(ingredients)

        val controller = IngredientController(ingredientRepository, currentUserService)
        val result = controller.allIngredients()

        assertEquals(2, result.size)
    }

    @Test
    fun `createIngredient saves and returns ingredient`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(user(accepted = true))
        whenever(ingredientRepository.save(any<Ingredient>())).thenAnswer { it.arguments[0] }

        val controller = IngredientController(ingredientRepository, currentUserService)
        val result = controller.createIngredient("Flour", 364f, 10f, 1f, 76f)

        assertEquals("Flour", result.name)
        assertEquals(364f, result.caloriesPer100g)
        assertEquals(10f, result.proteinPer100g)
        assertEquals(1f, result.fatPer100g)
        assertEquals(76f, result.carbsPer100g)
    }

    @Test
    fun `createIngredient is refused until the content rules are accepted`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(user(accepted = false))

        val controller = IngredientController(ingredientRepository, currentUserService)
        val refused = assertThrows(InvalidInputException::class.java) { controller.createIngredient("Flour", 364f, 10f, 1f, 76f) }

        assertEquals("TERMS_REQUIRED", refused.code)
        verify(ingredientRepository, never()).save(any())
    }

    @Test
    fun `createIngredient refuses a blocked word and names it`() {
        whenever(currentUserService.getCurrentUser()).thenReturn(user(accepted = true))

        val controller = IngredientController(ingredientRepository, currentUserService)
        val refused = assertThrows(InvalidInputException::class.java) { controller.createIngredient("Shit sauce", 0f, 0f, 0f, 0f) }

        assertEquals("BLOCKED_WORD", refused.code)
        assertEquals("Shit", refused.details["word"])
        assertTrue(refused.message!!.contains("Shit"))
        verify(ingredientRepository, never()).save(any())
    }
}

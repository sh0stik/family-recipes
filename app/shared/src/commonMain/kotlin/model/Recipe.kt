package model

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.ExperimentalResourceApi

data class Recipe @OptIn(ExperimentalResourceApi::class) constructor(
    val id: String,
    val title: String,
    val description: String,
    val visibility: Visibility = Visibility.ONLY_ME,
    val isTrashed: Boolean = false,
    // From the server, never derived on the client. canEdit: the owner, the recipe's editors, and
    // an admin on a family recipe. canManage: only the owner, or an admin on a family recipe.
    val canEdit: Boolean = false,
    val canManage: Boolean = false,
    // Only filled for whoever canManage: an admin took the recipe out of view, and why.
    val removedByAdmin: Boolean = false,
    val removalReason: String? = null,
    val ownerId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val ownerName: String? = null,
    val ownerLocale: String? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val categories: List<Category> = emptyList(),
    val notes: List<RecipeNote> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val videoLinks: List<String> = emptyList(),
    val nutrition: NutritionInfo? = null,
    val sharedWith: List<User> = emptyList(),
) {
    val isPublic: Boolean get() = visibility == Visibility.PUBLIC

    // Legacy fields for local drawable-based display - moved to computed or default properties
    val bgColor: Color get() = Color(0xffFBF5E9)
}

/** Who may read a recipe: only me (and its editors), any signed-in user, or anyone. */
enum class Visibility { ONLY_ME, FAMILY, PUBLIC }

data class RecipeIngredient(
    val id: String,
    val quantity: Double,
    val unitName: String? = null,
    val unitAbbreviation: String? = null,
    val ingredientId: String,
    val ingredientName: String,
)

data class Category(
    val id: String,
    val name: String,
    val parentCategory: Category? = null,
)

data class NutritionInfo(
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,
    val totalWeight: Double,
)

data class RecipeNote(
    val id: String,
    val note: String,
    val createdAt: String? = null,
    val userName: String? = null,
)

data class User(
    val id: String,
    val username: String,
    val preferredLocale: String = "en",
)

data class Ingredient(
    val id: String,
    val name: String,
    val caloriesPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
)

data class MeasurementUnit(
    val id: String,
    val name: String,
    val abbreviation: String,
    val type: String,
)

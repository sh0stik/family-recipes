package details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import model.Recipe
import model.RecipeIngredient
import primary

@Composable
fun IngredientItem(recipe: Recipe, ingredient: RecipeIngredient) {
    val quantity = if (ingredient.quantity == ingredient.quantity.toLong().toDouble()) {
        ingredient.quantity.toLong().toString()
    } else {
        ingredient.quantity.toString()
    }
    val unitText = ingredient.unitAbbreviation ?: ""
    val amountText = "$quantity $unitText".trim()

    Box(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ingredient.ingredientName,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = amountText,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Medium,
                color = primary,
            )
        }
    }
}

package recipe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dismissKeyboardOnTap
import errors.BlockedWordIn
import errors.errorText
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*

/**
 * Collects nutrition for an ingredient the user is creating.
 *
 * Required rather than optional: ingredients used to be created with zeroed macros, which is worse
 * than missing data — the ingredient still contributes its weight to the recipe, so its zero
 * calories drag the per-100g figures down and the result looks plausible while being wrong.
 *
 * Stays open while the ingredient is being created ([busy]) and shows why it was refused ([error]),
 * with the numbers still filled in for another try, as the content rules dialog does.
 */
@Composable
fun NewIngredientDialog(
    ingredientName: String,
    onDismiss: () -> Unit,
    onConfirm: (calories: Double, protein: Double, fat: Double, carbs: Double) -> Unit,
    busy: Boolean = false,
    error: Throwable? = null,
) {
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }

    // Comma is what a Russian keyboard offers for decimals, and toDoubleOrNull rejects it.
    fun parse(value: String): Double? = value.replace(',', '.').trim().toDoubleOrNull()

    val values = listOf(calories, protein, fat, carbs).map(::parse)
    val canSave = values.all { it != null && it >= 0 }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.new_ingredient_title) + ": " + ingredientName) },
        text = {
            // The decimal keypad has no return key on iOS, and a dialog is out of reach of the
            // app-wide tap-to-dismiss — tapping the dialog itself is the only way to lower it.
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.dismissKeyboardOnTap(),
            ) {
                Text(
                    stringResource(Res.string.new_ingredient_hint),
                    style = MaterialTheme.typography.caption,
                )

                @Composable
                fun field(value: String, onChange: (String) -> Unit, label: String) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        label = { Text(label) },
                        singleLine = true,
                        isError = value.isNotBlank() && parse(value) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = primary,
                            focusedLabelColor = primary,
                            cursorColor = primary,
                        ),
                    )
                }

                field(calories, { calories = it }, stringResource(Res.string.calories_per_100g))
                field(protein, { protein = it }, stringResource(Res.string.protein_per_100g))
                field(fat, { fat = it }, stringResource(Res.string.fat_per_100g))
                field(carbs, { carbs = it }, stringResource(Res.string.carbs_per_100g))

                if (!canSave) {
                    Text(
                        stringResource(Res.string.nutrition_required),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error,
                    )
                }

                error?.let {
                    Text(
                        errorText(it, blockedWordIn = BlockedWordIn.INGREDIENT),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave && !busy,
                onClick = {
                    val (c, p, f, cb) = values.map { it ?: 0.0 }
                    onConfirm(c, p, f, cb)
                },
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primary, strokeWidth = 2.dp)
                else Text(stringResource(Res.string.save), color = primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

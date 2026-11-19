package profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import data.AuthRepository
import data.DeleteAccountException
import data.RecipeRepository
import dismissKeyboardOnTap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*

/**
 * Confirms and performs account deletion (App Store Guideline 5.1.1(v)). Says how many recipes go
 * with the account — counted from the existing "my recipes" and trash queries, so no extra server
 * field — and asks for the password again, which the server checks.
 *
 * Only offered to non-admins: the server refuses admins, and ProfileScreen hides the entry.
 */
@Composable
fun DeleteAccountDialog(
    authRepository: AuthRepository,
    recipeRepository: RecipeRepository,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var recipeCount by remember { mutableStateOf<Int?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val active = recipeRepository.getMyRecipes().getOrNull()?.size
        val trashed = recipeRepository.getTrashedRecipes().getOrNull()?.size
        // A failed count must not block deletion; the warning then just omits the number.
        recipeCount = if (active != null && trashed != null) active + trashed else -1
    }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text(stringResource(Res.string.delete_account_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.dismissKeyboardOnTap(),
            ) {
                val count = recipeCount
                Text(
                    when {
                        count == null -> stringResource(Res.string.delete_account_counting)
                        count < 0 -> stringResource(Res.string.delete_account_message_no_count)
                        // "…and 0 of your recipes" read like a glitch; with none, say so plainly.
                        count == 0 -> stringResource(Res.string.delete_account_message_none)
                        else -> pluralStringResource(Res.plurals.delete_account_message, count, count)
                    }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorCode = null; failed = false },
                    label = { Text(stringResource(Res.string.delete_account_password_hint)) },
                    singleLine = true,
                    enabled = !isDeleting,
                    isError = errorCode == "WRONG_PASSWORD",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = primary,
                        focusedLabelColor = primary,
                        cursorColor = primary,
                    ),
                )
                val message = when {
                    errorCode == "WRONG_PASSWORD" -> stringResource(Res.string.delete_account_wrong_password)
                    errorCode == "ADMIN_CANNOT_DELETE" -> stringResource(Res.string.delete_account_admin)
                    errorCode == "RATE_LIMITED" -> stringResource(Res.string.delete_account_rate_limited)
                    failed -> stringResource(Res.string.delete_account_failed)
                    else -> null
                }
                if (message != null) {
                    Text(message, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank() && !isDeleting,
                onClick = {
                    isDeleting = true
                    scope.launch {
                        authRepository.deleteAccount(password).fold(
                            onSuccess = { onDeleted() },
                            onFailure = { e ->
                                errorCode = (e as? DeleteAccountException)?.code
                                failed = errorCode == null
                                isDeleting = false
                            },
                        )
                    }
                },
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Red, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.delete_account_confirm), color = Color.Red)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

package moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import data.type.ReportReason
import errors.errorText
import kotlinx.coroutines.launch
import navigation.LegalLinks
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*

private const val MAX_REASON = 1000

/** An admin's reason for taking a recipe out of public view. The owner sees it. */
@Composable
fun AdminRemoveDialog(
    onRemove: suspend (reason: String) -> Result<*>,
    onRemoved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.admin_remove_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(Res.string.admin_remove_message), style = MaterialTheme.typography.body2)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(MAX_REASON) },
                    label = { Text(stringResource(Res.string.admin_remove_reason_label)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(errorText(it), color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && reason.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        onRemove(reason).fold(
                            onSuccess = { onRemoved() },
                            onFailure = { error = it; busy = false },
                        )
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Red, strokeWidth = 2.dp)
                else Text(stringResource(Res.string.admin_remove_confirm), color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

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

/** The server refuses a comment over this many characters (TOO_LONG). */
private const val MAX_COMMENT = 1000

/** Reason (the API's four) plus an optional comment. [title] says what is being reported. */
@Composable
fun ReportDialog(
    title: String,
    onSend: suspend (reason: ReportReason, comment: String?) -> Result<*>,
    onSent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf(ReportReason.INAPPROPRIATE) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.report_reason_prompt), style = MaterialTheme.typography.subtitle2)
                Column(modifier = Modifier.selectableGroup()) {
                    listOf(
                        ReportReason.INAPPROPRIATE to Res.string.report_reason_inappropriate,
                        ReportReason.SPAM to Res.string.report_reason_spam,
                        ReportReason.NOT_THEIRS to Res.string.report_reason_not_theirs,
                        ReportReason.OTHER to Res.string.report_reason_other,
                    ).forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .selectable(selected = reason == value, role = Role.RadioButton) { reason = value }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                selected = reason == value,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = primary),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.body2)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(MAX_COMMENT) },
                    label = { Text(stringResource(Res.string.report_comment_label)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText(it), color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        onSend(reason, comment.ifBlank { null }).fold(
                            onSuccess = { onSent() },
                            onFailure = { error = it; busy = false },
                        )
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primary, strokeWidth = 2.dp)
                else Text(stringResource(Res.string.report_send), color = primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** Says what blocking does before it happens. */
@Composable
fun BlockUserDialog(
    username: String,
    onBlock: suspend () -> Result<*>,
    onBlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.block_dialog_title, username)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(Res.string.block_dialog_message), style = MaterialTheme.typography.body2)
                error?.let {
                    Text(errorText(it), color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        onBlock().fold(
                            onSuccess = { onBlocked() },
                            onFailure = { error = it; busy = false },
                        )
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Red, strokeWidth = 2.dp)
                else Text(stringResource(Res.string.block_confirm), color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.cancel)) }
        },
    )
}


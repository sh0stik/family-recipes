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

/**
 * The content rules with an Accept button. Shown when the server answers TERMS_REQUIRED. [onAccept]
 * calls the accept mutation; on success the caller's [onAccepted] retries whatever was refused.
 */
@Composable
fun TermsDialog(
    onAccept: suspend () -> Result<*>,
    onAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.terms_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(Res.string.terms_dialog_intro))
                Text("•  " + stringResource(Res.string.terms_rule_1), style = MaterialTheme.typography.body2)
                Text("•  " + stringResource(Res.string.terms_rule_2), style = MaterialTheme.typography.body2)
                Text("•  " + stringResource(Res.string.terms_rule_3), style = MaterialTheme.typography.body2)
                Text(
                    stringResource(Res.string.terms_read_full),
                    color = primary,
                    modifier = Modifier.clickable { uriHandler.openUri(LegalLinks.TERMS) }.padding(vertical = 4.dp),
                )
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
                        onAccept().fold(
                            onSuccess = { onAccepted() },
                            onFailure = { error = it; busy = false },
                        )
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primary, strokeWidth = 2.dp)
                else Text(stringResource(Res.string.terms_accept), color = primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

package details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import data.RecipeRepository
import errors.errorText
import kotlinx.coroutines.launch
import model.Recipe
import model.User
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*

/**
 * "Who can edit", for whoever manages the recipe. Each person is a checkbox; ticking one makes
 * them an editor immediately (the server's shareRecipe), unticking removes them (unshareRecipe).
 * Editors change the recipe's content, never its visibility, its editors or its trash status.
 * Copying the link or the text lives here too, so the Share button has one destination instead of
 * a menu in front of a dialog.
 *
 * [onDismiss] receives whether anything changed, so the caller can re-fetch the recipe.
 */
@Composable
fun ShareRecipeDialog(
    recipe: Recipe,
    currentUsername: String,
    recipeRepository: RecipeRepository,
    onCopyLink: () -> Unit,
    onCopyText: () -> Unit,
    onDismiss: (changed: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<User>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var sharedIds by remember { mutableStateOf(recipe.sharedWith.map { it.id }.toSet()) }
    // Ids with a request in flight: their checkbox is disabled so a double tap cannot send a
    // share and an unshare that race each other.
    var pending by remember { mutableStateOf(emptySet<String>()) }
    var changed by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        recipeRepository.getUsers().fold(
            // Neither you nor the recipe's owner (an admin sharing a family recipe) can be picked.
            onSuccess = { all -> users = all.filter { it.username != currentUsername && it.username != recipe.ownerName }.sortedBy { it.username.lowercase() } },
            onFailure = { loadFailed = true },
        )
    }

    fun toggle(user: User) {
        val share = user.id !in sharedIds
        pending = pending + user.id
        actionError = null
        scope.launch {
            val result = if (share) recipeRepository.shareRecipe(recipe.id, user.id)
                         else recipeRepository.unshareRecipe(recipe.id, user.id)
            result.fold(
                onSuccess = {
                    sharedIds = if (share) sharedIds + user.id else sharedIds - user.id
                    changed = true
                },
                onFailure = { actionError = it },
            )
            pending = pending - user.id
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss(changed) },
        title = { Text(stringResource(Res.string.editors_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.editors_hint),
                    style = MaterialTheme.typography.caption,
                    color = androidx.compose.ui.graphics.Color.Gray,
                )
                val loaded = users
                when {
                    loadFailed -> Text(stringResource(Res.string.editors_load_failed), color = MaterialTheme.colors.error)
                    loaded == null -> CircularProgressIndicator(color = primary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    loaded.isEmpty() -> Text(stringResource(Res.string.editors_none))
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(loaded, key = { it.id }) { user ->
                            val enabled = user.id !in pending
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { toggle(user) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = user.id in sharedIds,
                                    onCheckedChange = { toggle(user) },
                                    enabled = enabled,
                                    colors = CheckboxDefaults.colors(checkedColor = primary),
                                )
                                Text(user.username)
                            }
                        }
                    }
                }
                actionError?.let { e ->
                    Text(
                        errorText(e),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                    )
                }
                // AlertDialog has only two button slots and Done takes one; the copy actions sit
                // under the list instead, where they also read as "or share it another way".
                // Stacked, not side by side: the Russian labels do not fit two to a row on a phone.
                Divider()
                TextButton(onClick = onCopyLink) {
                    Text(stringResource(Res.string.share_copy_link), color = primary)
                }
                TextButton(onClick = onCopyText) {
                    Text(stringResource(Res.string.share_copy_text), color = primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(changed) }) {
                Text(stringResource(Res.string.done), color = primary)
            }
        },
    )
}

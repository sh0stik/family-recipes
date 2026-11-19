package details

import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import recipeappkmp.generated.resources.*

/**
 * What a reader can do about a recipe that is not theirs. A null action is not offered.
 * [onReport] and [onRemoveFromPublic] live in the recipe's overflow menu; [onReportUser] and [onBlockUser] in the menu on
 * the author line.
 */
class ModerationActions(
    val onReport: (() -> Unit)? = null,
    val onRemoveFromPublic: (() -> Unit)? = null,
    val onReportUser: (() -> Unit)? = null,
    val onBlockUser: (() -> Unit)? = null,
) {
    val hasRecipeActions: Boolean get() = onReport != null || onRemoveFromPublic != null
    val hasAuthorActions: Boolean get() = onReportUser != null || onBlockUser != null
}

/** The "more" button of the recipe's top bar: Report, and Remove from public for admins. */
@Composable
fun RecipeMenuButton(
    actions: ModerationActions,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconModifier: Modifier = Modifier,
) {
    if (!actions.hasRecipeActions) return
    OverflowMenu(
        modifier = modifier,
        tint = tint,
        iconModifier = iconModifier,
        items = listOfNotNull(
            actions.onReport?.let { stringResource(Res.string.report_action) to it },
            actions.onRemoveFromPublic?.let { stringResource(Res.string.admin_remove_action) to it },
        ),
    )
}

/** The "more" button on the author line: Report user, Block user. */
@Composable
fun AuthorMenuButton(
    actions: ModerationActions,
    modifier: Modifier = Modifier,
    tint: Color = Color.Gray,
) {
    if (!actions.hasAuthorActions) return
    OverflowMenu(
        modifier = modifier,
        tint = tint,
        items = listOfNotNull(
            actions.onReportUser?.let { stringResource(Res.string.report_user_action) to it },
            actions.onBlockUser?.let { stringResource(Res.string.block_user_action) to it },
        ),
    )
}

@Composable
private fun OverflowMenu(
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier,
    tint: Color,
    iconModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.more_actions),
                tint = tint,
                modifier = iconModifier,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    action()
                }) {
                    Text(label)
                }
            }
        }
    }
}

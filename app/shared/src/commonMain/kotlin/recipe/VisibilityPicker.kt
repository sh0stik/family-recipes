package recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.Visibility
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*

/**
 * Only me / Family / Public, one line of explanation under each. Replaces the old "Public recipe"
 * switch on both the create and the edit screen.
 */
@Composable
fun VisibilityPicker(
    selected: Visibility,
    onSelect: (Visibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        Text(
            stringResource(Res.string.visibility_title),
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
        )
        Visibility.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = primary),
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(option.titleRes()), style = MaterialTheme.typography.body1)
                    Text(
                        stringResource(option.hintRes()),
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}

private fun Visibility.titleRes() = when (this) {
    Visibility.ONLY_ME -> Res.string.visibility_only_me
    Visibility.FAMILY -> Res.string.visibility_family
    Visibility.PUBLIC -> Res.string.visibility_public
}

private fun Visibility.hintRes() = when (this) {
    Visibility.ONLY_ME -> Res.string.visibility_only_me_hint
    Visibility.FAMILY -> Res.string.visibility_family_hint
    Visibility.PUBLIC -> Res.string.visibility_public_hint
}

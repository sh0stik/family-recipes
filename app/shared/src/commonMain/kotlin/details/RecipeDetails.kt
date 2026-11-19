package details

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import model.Recipe
import sensor.SensorManager

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecipeDetails(
    recipe: Recipe,
    goBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onAddNote: ((String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    isAddingNote: Boolean = false,
    onShare: (() -> Unit)? = null,
    currentUsername: String? = null,
    moderation: ModerationActions = ModerationActions(),
    sensorManager: SensorManager?,
    isLarge: Boolean,
    animatedVisibilityScope: AnimatedContentScope,
    sharedTransactionScope: SharedTransitionScope
) {
    if (isLarge) RecipeDetailsLarge(
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransactionScope = sharedTransactionScope,
        recipe = recipe,
        goBack = goBack,
        onEdit = onEdit,
        onDelete = onDelete,
        onCopy = onCopy,
        onAddNote = onAddNote,
        onDeleteNote = onDeleteNote,
        isAddingNote = isAddingNote,
        onShare = onShare,
        currentUsername = currentUsername,
        moderation = moderation,
        sensorManager = sensorManager,
    )
    else RecipeDetailsSmall(
        animatedVisibilityScope = animatedVisibilityScope,
        sharedTransactionScope = sharedTransactionScope,
        recipe = recipe,
        goBack = goBack,
        onEdit = onEdit,
        onDelete = onDelete,
        onCopy = onCopy,
        onAddNote = onAddNote,
        onDeleteNote = onDeleteNote,
        isAddingNote = isAddingNote,
        onShare = onShare,
        currentUsername = currentUsername,
        moderation = moderation,
        sensorManager = sensorManager,
    )
}

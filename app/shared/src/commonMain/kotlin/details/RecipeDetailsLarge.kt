package details

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import icons.ChefHat
import model.Recipe
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sensor.Listener
import sensor.SensorData
import sensor.SensorManager
import sugar


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecipeDetailsLarge(
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
    animatedVisibilityScope: AnimatedContentScope,
    sharedTransactionScope: SharedTransitionScope,
) {
    val sensorDataLive = remember { mutableStateOf(SensorData(0.0f, 0.0f)) }

    sensorManager?.registerListener(object : Listener {
        override fun onUpdate(sensorData: SensorData) {
            sensorDataLive.value = sensorData
        }
    })

    with(sharedTransactionScope) {
        Box(
            modifier = Modifier.fillMaxSize().background(sugar)
        ) {
            Row {
                // Left: image area
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                            .then(
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "item-container-${recipe.id}"),
                                    animatedVisibilityScope,
                                )
                            ),
                        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(primary.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier.aspectRatio(1f).padding(32.dp)
                                    .align(Alignment.Center)
                            ) {
                                val imageUrl = recipe.photoUrls.firstOrNull()
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = data.resolveImageUrl(imageUrl),
                                        contentDescription = null,
                                        modifier = Modifier.aspectRatio(1f).align(Alignment.Center)
                                            .padding(16.dp)
                                            .sharedBounds(
                                                rememberSharedContentState(key = "item-image-${recipe.id}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            ),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.aspectRatio(1f).align(Alignment.Center)
                                            .padding(16.dp)
                                            .background(primary.copy(alpha = 0.12f), CircleShape)
                                            .sharedBounds(
                                                rememberSharedContentState(key = "item-image-${recipe.id}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = ChefHat,
                                            contentDescription = null,
                                            tint = primary,
                                            modifier = Modifier.size(64.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right: details
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f).background(sugar)
                ) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 32.dp),
                        userScrollEnabled = true,
                        verticalArrangement = Arrangement.Absolute.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        StepsAndDetails(
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransactionScope = sharedTransactionScope,
                            recipe = recipe,
                            onAddNote = onAddNote,
                            onDeleteNote = onDeleteNote,
                            isAddingNote = isAddingNote,
                            currentUsername = currentUsername,
                            moderation = moderation,
                        )
                    }
                }
            }

            // Top buttons
            Row(
                modifier = Modifier.padding(start = 32.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackButton(goBack)
                if (onShare != null) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .clickable { onShare() }
                            .background(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.share_action),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onCopy != null) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .clickable { onCopy() }
                            .background(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.copy_action),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onEdit != null) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .clickable { onEdit() }
                            .background(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.edit_action),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .clickable { onDelete() }
                            .background(color = Color.Red.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.delete),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (moderation.hasRecipeActions) {
                    RecipeMenuButton(
                        actions = moderation,
                        modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)),
                        iconModifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BackButton(goBack: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .clickable { goBack() }
            .background(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
            .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.padding(start = 8.dp))
            Text(
                text = stringResource(Res.string.back_to_recipes),
                style = MaterialTheme.typography.subtitle2,
                color = Color.White
            )
        }
    }
}

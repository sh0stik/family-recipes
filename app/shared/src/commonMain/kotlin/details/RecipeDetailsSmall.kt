package details

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
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


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecipeDetailsSmall(
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
    sharedTransactionScope: SharedTransitionScope
) {
    val sensorDataLive = remember { mutableStateOf(SensorData(0.0f, 0.0f)) }

    sensorManager?.registerListener(object : Listener {
        override fun onUpdate(sensorData: SensorData) {
            sensorDataLive.value = sensorData
        }
    })

    val listState = rememberLazyListState()
    val (fraction, setFraction) = remember { mutableStateOf(1f) }

    with(sharedTransactionScope) {

        if (sharedTransactionScope.isTransitionActive.not()) {
            setFraction(0f)
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .background(color = sugar)
        ) {
            // The note input is the last item; imePadding lets the list scroll its Add button
            // up above the keyboard.
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                state = listState
            ) {

                // A normal item, not a stickyHeader: the photo scrolls away with the content. It was
                // sticky with a scroll-driven fade, but the fade was never wired to scroll — the
                // `fraction` below only ever holds 1 during the shared-element transition and 0
                // after — so it stayed pinned and opaque over every recipe.
                item {
                    Box(
                        modifier = Modifier
                            .alpha(if (fraction < 0.2) 1f - fraction else 0f)
                            .fillMaxWidth()
                            .background(
                                primary.copy(alpha = 0.08f),
                                RoundedCornerShape(bottomEnd = 24.dp, bottomStart = 24.dp),
                            )
                            .clip(RoundedCornerShape(bottomEnd = 24.dp, bottomStart = 24.dp))
                            .height(300.dp)
                            .then(
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "item-container-${recipe.id}"),
                                    animatedVisibilityScope,
                                )
                            ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier.aspectRatio(1f).align(Alignment.Center)
                            ) {
                                val imageUrl = recipe.photoUrls.firstOrNull()
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = data.resolveImageUrl(imageUrl),
                                        contentDescription = null,
                                        modifier = Modifier.aspectRatio(1f).align(Alignment.Center)
                                            .windowInsetsPadding(WindowInsets.systemBars)
                                            .padding(16.dp)
                                            .background(Color.Transparent, CircleShape)
                                            .sharedBounds(
                                                rememberSharedContentState(key = "item-image-${recipe.id}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                            ),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.aspectRatio(1f).align(Alignment.Center)
                                            .windowInsetsPadding(WindowInsets.systemBars)
                                            .padding(16.dp)
                                            .background(primary.copy(alpha = 0.12f), CircleShape)
                                            .sharedBounds(
                                                rememberSharedContentState(key = "item-image-${recipe.id}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                enter = fadeIn(),
                                                exit = fadeOut(),
                                                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
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

                StepsAndDetails(
                    sharedTransactionScope = sharedTransactionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    recipe = recipe,
                    onAddNote = onAddNote,
                    onDeleteNote = onDeleteNote,
                    isAddingNote = isAddingNote,
                    currentUsername = currentUsername,
                    moderation = moderation,
                )
            }

            // Top buttons
            Row(
                modifier = Modifier.statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = goBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(Res.string.back),
                        tint = Color.White,
                    )
                }
                Row {
                    if (onShare != null) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(Res.string.share_action),
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (onCopy != null) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Res.string.copy_action),
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.edit_action),
                                tint = Color.White,
                            )
                        }
                    }
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.background(Color.Red.copy(alpha = 0.5f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.delete),
                                tint = Color.White,
                            )
                        }
                    }
                    if (moderation.hasRecipeActions) {
                        Spacer(modifier = Modifier.width(4.dp))
                        RecipeMenuButton(
                            actions = moderation,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

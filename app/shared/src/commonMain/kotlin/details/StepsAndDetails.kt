package details

import AnimateInEffect
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import model.Recipe
import honey
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import white

@OptIn(ExperimentalSharedTransitionApi::class)
internal fun LazyListScope.StepsAndDetails(
    animatedVisibilityScope: AnimatedContentScope,
    sharedTransactionScope: SharedTransitionScope,
    recipe: Recipe,
    onAddNote: ((String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    isAddingNote: Boolean = false,
    currentUsername: String? = null,
    moderation: ModerationActions = ModerationActions(),
) {
    val totalItems = recipe.ingredients.size + recipe.categories.size + recipe.notes.size + 3

    with(sharedTransactionScope) {
        item {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.W700,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp).then(
                    Modifier.sharedElement(
                        rememberSharedContentState(key = "recipe-title-${recipe.id}"),
                        animatedVisibilityScope,
                    )
                )
            )

            // The author line. Report user and Block user hang off it; they are offered only
            // on recipes that are not yours.
            val author = recipe.ownerName
            if (!author.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.author_line, author),
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AuthorMenuButton(moderation)
                }
            }

            if (recipe.removedByAdmin) {
                val reason = recipe.removalReason
                Text(
                    text = if (reason.isNullOrBlank()) stringResource(Res.string.removed_by_admin_banner_no_reason)
                    else stringResource(Res.string.removed_by_admin_banner, reason),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
                )
            }

            Text(
                text = recipe.description,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp).then(
                    Modifier.sharedElement(
                        rememberSharedContentState(key = "recipe-description-${recipe.id}"),
                        animatedVisibilityScope,
                    )
                )
            )

            // Photos: the first one is already the header image above, so list only the rest.
            // Recipes have one photo now; extras exist only on older recipes.
            val extraPhotos = recipe.photoUrls.drop(1)
            if (extraPhotos.isNotEmpty()) {
                PhotoSection(extraPhotos)
            }

            // Videos
            if (recipe.videoLinks.isNotEmpty()) {
                VideoSection(recipe.videoLinks)
            }

            // Nutrition info
            if (recipe.nutrition != null) {
                AnimateInEffect(
                    recipe = recipe,
                    intervalStart = 0f,
                    content = {
                        NutritionSection(recipe.nutrition!!)
                    }
                )
            }

            // Categories
            if (recipe.categories.isNotEmpty()) {
                AnimateInEffect(
                    recipe = recipe,
                    intervalStart = 0.05f,
                    content = {
                        CategoriesSection(recipe.categories)
                    }
                )
            }

            AnimateInEffect(
                recipe = recipe,
                intervalStart = 0.1f,
                content = {
                    Text(
                        text = stringResource(Res.string.ingredients_title),
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.W700,
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    )
                }
            )
        }

        if (recipe.ingredients.isNotEmpty()) {
            itemsIndexed(recipe.ingredients) { index, ingredient ->
                AnimateInEffect(
                    intervalStart = (index + 1) / totalItems.toFloat(),
                    recipe = recipe,
                    content = {
                        IngredientItem(recipe, ingredient)
                    }
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(Res.string.no_ingredients),
                    style = MaterialTheme.typography.body2,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }

        // Notes section
        item {
            AnimateInEffect(
                recipe = recipe,
                intervalStart = (recipe.ingredients.size + 1) / totalItems.toFloat(),
                content = {
                    Text(
                        text = stringResource(Res.string.notes_title),
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.W700,
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    )
                }
            )
        }

        if (recipe.notes.isNotEmpty()) {
            itemsIndexed(recipe.notes) { index, note ->
                AnimateInEffect(
                    recipe = recipe,
                    intervalStart = (recipe.ingredients.size + index + 2) / totalItems.toFloat(),
                    content = {
                        // The backend lets you delete your own notes, and anyone's on a recipe you manage.
                        val canDelete = currentUsername != null && (note.userName == currentUsername || recipe.canManage)
                        NoteItem(note, onDelete = if (canDelete) onDeleteNote else null)
                    }
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(Res.string.no_notes),
                    style = MaterialTheme.typography.body2,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }

        // Add note input
        if (onAddNote != null) {
            item {
                AddNoteInput(onAddNote = onAddNote, isAddingNote = isAddingNote)
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NutritionSection(nutrition: model.NutritionInfo) {
    Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
        Text(
            text = stringResource(Res.string.nutrition_title),
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.W700,
            color = primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NutritionChip("${nutrition.caloriesPer100g.toInt()} ${stringResource(Res.string.kcal)}", Modifier.weight(1f))
            NutritionChip("${stringResource(Res.string.protein_short)}: ${nutrition.proteinPer100g.toInt()}${stringResource(Res.string.grams_short)}", Modifier.weight(1f))
            NutritionChip("${stringResource(Res.string.fat_short)}: ${nutrition.fatPer100g.toInt()}${stringResource(Res.string.grams_short)}", Modifier.weight(1f))
            NutritionChip("${stringResource(Res.string.carbs_short)}: ${nutrition.carbsPer100g.toInt()}${stringResource(Res.string.grams_short)}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NutritionChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(categories: List<model.Category>) {
    Column(modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
        Text(
            text = stringResource(Res.string.categories_title),
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.W700,
            color = honey,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = 2.dp,
                    backgroundColor = Color.White,
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.body2,
                        color = honey,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddNoteInput(onAddNote: (String) -> Unit, isAddingNote: Boolean) {
    var noteText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            placeholder = { Text(stringResource(Res.string.add_note_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = primary,
                cursorColor = primary,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (noteText.isNotBlank()) {
                    onAddNote(noteText)
                    noteText = ""
                    // Return inserts a newline in this multi-line field, so the button is where
                    // the note is done; clearing focus also lowers the keyboard.
                    focusManager.clearFocus()
                }
            },
            enabled = noteText.isNotBlank() && !isAddingNote,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = primary),
        ) {
            if (isAddingNote) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = white, strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.add), color = white)
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    // Input: "2026-04-01T21:43:48.721Z" or "2026-04-01T21:43:48.721-07:00"
    // Output: "01.04.2026 21:43"
    return try {
        val datePart = isoDate.substringBefore("T")
        val timePart = isoDate.substringAfter("T").take(5)
        val parts = datePart.split("-")
        "${parts[2]}.${parts[1]}.${parts[0]} $timePart"
    } catch (_: Exception) {
        isoDate
    }
}

@Composable
private fun NoteItem(note: model.RecipeNote, onDelete: ((String) -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.note,
                style = MaterialTheme.typography.body2,
            )
            Text(
                text = "- ${note.userName ?: stringResource(Res.string.unknown_user)}" + if (note.createdAt != null) ", ${formatDate(note.createdAt)}" else "",
                style = MaterialTheme.typography.caption,
                color = androidx.compose.ui.graphics.Color.Gray,
            )
        }
        if (onDelete != null) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(Res.string.delete_note),
                tint = androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.size(20.dp).clickable { onDelete(note.id) },
            )
        }
    }
}

@Composable
private fun PhotoSection(photoUrls: List<String>) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(Res.string.photos_title),
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.W700,
            color = primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        val pagerState = rememberPagerState(pageCount = { photoUrls.size })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
        ) { page ->
            coil3.compose.AsyncImage(
                model = data.resolveImageUrl(photoUrls[page]),
                contentDescription = stringResource(Res.string.photo_content_description, page + 1),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    .background(androidx.compose.ui.graphics.Color.LightGray, RoundedCornerShape(12.dp))
                    .then(androidx.compose.foundation.shape.RoundedCornerShape(12.dp).let {
                        Modifier
                    }),
            )
        }
        if (photoUrls.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(photoUrls.size) { index ->
                    Box(
                        modifier = Modifier.padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .background(
                                if (pagerState.currentPage == index) primary
                                else androidx.compose.ui.graphics.Color.LightGray,
                                CircleShape,
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSection(videoLinks: List<String>) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(Res.string.videos_title),
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.W700,
            color = primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Only web links are shown or opened: a stored javascript: link would run in the web app.
        videoLinks.filter { data.Links.isWebLink(it) }.forEach { link ->
            val uriHandler = LocalUriHandler.current
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { uriHandler.openUri(link.trim()) },
                shape = RoundedCornerShape(12.dp),
                elevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(Res.string.watch_video),
                        tint = primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = link,
                        style = MaterialTheme.typography.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


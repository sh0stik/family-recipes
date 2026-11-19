package recipeslist

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import model.Recipe
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

/**
 * Narrowest a recipe tile may get. The grid fits as many columns of at least this width as the
 * window allows, the same on every platform, so no platform flag decides it.
 *
 * A 360dp portrait phone must get exactly one column. 340dp does that for 360 and 412dp phones
 * (and 600dp foldables), gives two columns from 680dp and three from 1020dp, and leaves ~200dp for
 * the text beside the 80dp image. Do not go below ~300dp: the title column gets too narrow.
 */
internal val RECIPE_TILE_MIN_WIDTH = 340.dp

enum class RecipeSort { NEWEST, NAME, UPDATED }

// createdAt/updatedAt are ISO-8601 strings, so lexicographic desc == chronological desc.
private fun List<Recipe>.applySort(sort: RecipeSort): List<Recipe> = when (sort) {
    RecipeSort.NEWEST -> sortedByDescending { it.createdAt ?: "" }
    RecipeSort.NAME -> sortedBy { it.title.lowercase() }
    RecipeSort.UPDATED -> sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecipesListScreen(
    /** The home lists: Mine, Shared, Public. Signed out, only Public is ever filled. */
    sections: RecipeSections,
    /** The chip that is selected. Ignored signed out, where only Public is shown. */
    selectedGroup: RecipeGroup = RecipeGroup.MINE,
    onGroupChange: (RecipeGroup) -> Unit = {},
    onClick: (recipe: Recipe) -> Unit,
    sharedTransactionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCreateRecipe: (() -> Unit)? = null,
    searchQuery: String = "",
    onSearchChange: ((String) -> Unit)? = null,
    onCategories: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null,
    isLoggedIn: Boolean = false,
    /** Search results, split the same way; only the selected group is shown. */
    searchSections: RecipeSections = RecipeSections(),
    /**
     * True while the first load is in flight. Without this an empty list is indistinguishable from
     * "you have no recipes", so every launch flashed the empty-state message before the data
     * arrived.
     */
    isLoading: Boolean = false,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(sugar)
    ) {
        val listState = rememberLazyGridState()
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val isSearching = onSearchChange != null && searchQuery.isNotBlank()
        var sortOption by remember { mutableStateOf(RecipeSort.NEWEST) }
        val group = RecipeGroup.effective(selectedGroup, isLoggedIn)
        val shown = (if (isSearching) searchSections else sections).of(group)
        // Another group starts at its top, not wherever the last one was scrolled to.
        LaunchedEffect(group) { listState.scrollToItem(0) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with search + action buttons
            if (onSearchChange != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { onSearchChange(it) },
                        placeholder = { Text(stringResource(Res.string.search_recipes_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                            autoCorrectEnabled = false,
                        ),
                        // Results update as you type, so the Search key just gets the keyboard
                        // out of the way of them.
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = primary,
                            backgroundColor = white,
                        ),
                    )

                    if (onCategories != null) {
                        IconButton(onClick = onCategories) {
                            Icon(Icons.Default.List, contentDescription = stringResource(Res.string.categories_button), tint = primary)
                        }
                    }

                    if (onProfile != null) {
                        IconButton(onClick = onProfile) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(Res.string.profile_button), tint = primary)
                        }
                    }
                }
            }

            // Group chips: one group at a time. Signed out there is only Public, so no chips.
            if (isLoggedIn) {
                GroupChips(
                    selected = group,
                    onSelect = onGroupChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Sort selector — shown whenever there's a list to order.
            if (onSearchChange != null && shown.isNotEmpty()) {
                SortSelector(
                    sortOption = sortOption,
                    onSortChange = { sortOption = it },
                    modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 4.dp),
                )
            }

            when {
                // Still loading the first page: a spinner, never an "empty" message.
                shown.isEmpty() && isLoading && !isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = primary)
                    }
                }
                // Searching and nothing in this group matches.
                shown.isEmpty() && isSearching -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(Res.string.search_no_results), color = Color.Gray)
                    }
                }
                // The group has nothing in it (no active search): a friendly placeholder.
                shown.isEmpty() -> {
                    EmptyRecipesState(
                        group = group,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        state = listState,
                        columns = GridCells.Adaptive(minSize = RECIPE_TILE_MIN_WIDTH),
                        modifier = Modifier.weight(1f),
                    ) {
                        val sorted = shown.applySort(sortOption)
                        items(sorted.size) { index ->
                            val recipe = sorted[index]
                            if (isSearching) {
                                RecipeListItem(sharedTransactionScope, animatedVisibilityScope, recipe, onClick)
                            } else {
                                RecipeListItemWrapper(
                                    scrollDirection = listState.isScrollingUp(),
                                    child = {
                                        RecipeListItem(
                                            recipe = recipe,
                                            onClick = onClick,
                                            sharedTransitionScope = sharedTransactionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB for creating recipes
        if (onCreateRecipe != null) {
            FloatingActionButton(
                onClick = onCreateRecipe,
                backgroundColor = primary,
                contentColor = white,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .windowInsetsPadding(WindowInsets.systemBars),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.create_recipe))
            }
        }
    }
}

@Composable
private fun sortLabel(sort: RecipeSort): String = stringResource(
    when (sort) {
        RecipeSort.NEWEST -> Res.string.sort_newest
        RecipeSort.NAME -> Res.string.sort_name
        RecipeSort.UPDATED -> Res.string.sort_updated
    }
)

@Composable
private fun SortSelector(
    sortOption: RecipeSort,
    onSortChange: (RecipeSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { expanded = true }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sortLabel(sortOption), style = MaterialTheme.typography.body2, color = primary)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(Res.string.sort_label), tint = primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecipeSort.entries.forEach { option ->
                DropdownMenuItem(onClick = {
                    onSortChange(option)
                    expanded = false
                }) {
                    Text(
                        sortLabel(option),
                        color = if (option == sortOption) primary else Color.Unspecified,
                    )
                }
            }
        }
    }
}

@Composable
private fun groupLabel(group: RecipeGroup): String = stringResource(
    when (group) {
        RecipeGroup.MINE -> Res.string.section_mine
        RecipeGroup.SHARED -> Res.string.section_shared
        RecipeGroup.PUBLIC -> Res.string.section_public
    }
)

// Mine / Shared / Public. Exactly one is selected. The row scrolls sideways when the
// labels are wider than a narrow phone (large fonts, Russian), so nothing is clipped.
@Composable
private fun GroupChips(
    selected: RecipeGroup,
    onSelect: (RecipeGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeGroup.entries.forEach { group ->
            val isSelected = group == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) primary else white,
                contentColor = if (isSelected) white else primary,
                border = BorderStroke(1.dp, primary),
                modifier = Modifier.selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = { onSelect(group) },
                ),
            ) {
                Text(
                    text = groupLabel(group),
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    modifier = Modifier.heightIn(min = 20.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyRecipesState(
    group: RecipeGroup,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                when (group) {
                    RecipeGroup.MINE -> Res.string.empty_home_title
                    RecipeGroup.SHARED -> Res.string.empty_shared_title
                    RecipeGroup.PUBLIC -> Res.string.empty_public_title
                }
            ),
            style = MaterialTheme.typography.h6,
            color = primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when (group) {
                    RecipeGroup.MINE -> Res.string.empty_home_message
                    RecipeGroup.SHARED -> Res.string.empty_shared_message
                    RecipeGroup.PUBLIC -> Res.string.empty_public_message
                }
            ),
            style = MaterialTheme.typography.body2,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LazyGridState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

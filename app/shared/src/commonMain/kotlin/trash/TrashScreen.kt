package trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import data.RecipeRepository
import kotlinx.coroutines.launch
import model.Recipe
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@Composable
fun TrashScreen(
    recipeRepository: RecipeRepository,
    onRecipeRestored: () -> Unit,
    goBack: () -> Unit,
) {
    var trashedRecipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var isEmptying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recipeRepository.getTrashedRecipes().onSuccess {
            trashedRecipes = it
            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(Res.string.trash_title)) },
                backgroundColor = primary,
                contentColor = white,
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    if (trashedRecipes.isNotEmpty()) {
                        IconButton(onClick = { showEmptyDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.empty_trash_button))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(sugar).padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = primary,
                )
            } else if (trashedRecipes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.trash_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trashedRecipes, key = { it.id }) { recipe ->
                        TrashRecipeItem(
                            recipe = recipe,
                            onRestore = {
                                scope.launch {
                                    recipeRepository.restoreFromTrash(recipe.id).onSuccess {
                                        trashedRecipes = trashedRecipes.filter { it.id != recipe.id }
                                        onRecipeRestored()
                                    }
                                }
                            },
                            onDeleteForever = {
                                scope.launch {
                                    recipeRepository.deletePermanently(recipe.id).onSuccess {
                                        trashedRecipes = trashedRecipes.filter { it.id != recipe.id }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text(stringResource(Res.string.empty_trash_title)) },
            text = { Text(stringResource(Res.string.empty_trash_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyDialog = false
                        isEmptying = true
                        scope.launch {
                            recipeRepository.emptyTrash().onSuccess {
                                trashedRecipes = emptyList()
                            }
                            isEmptying = false
                        }
                    },
                ) {
                    Text(stringResource(Res.string.delete_all), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TrashRecipeItem(
    recipe: Recipe,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    var isRestoring by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Medium,
            )
            if (recipe.description.isNotBlank()) {
                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    maxLines = 2,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        isRestoring = true
                        onRestore()
                    },
                    enabled = !isRestoring,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = primary),
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = white,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(Res.string.restore), color = white)
                    }
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(Res.string.delete_forever), color = Color.Red)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.delete_forever_title)) },
            text = { Text(stringResource(Res.string.delete_forever_message, recipe.title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteForever()
                }) {
                    Text(stringResource(Res.string.delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

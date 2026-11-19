package categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import data.RecipeRepository
import kotlinx.coroutines.launch
import model.Category
import model.Recipe
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@Composable
fun CategoriesScreen(
    recipeRepository: RecipeRepository,
    onRecipeClick: (Recipe) -> Unit,
    goBack: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var filteredRecipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingRecipes by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recipeRepository.getAllCategories().onSuccess {
            categories = it
            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(selectedCategory?.name ?: stringResource(Res.string.categories_screen_title))
                },
                backgroundColor = primary,
                contentColor = white,
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCategory != null) {
                            selectedCategory = null
                            filteredRecipes = emptyList()
                        } else {
                            goBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
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
            } else if (selectedCategory == null) {
                // Show categories as tiles grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(categories) { category ->
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clickable {
                                selectedCategory = category
                                isLoadingRecipes = true
                                scope.launch {
                                    recipeRepository.searchRecipes(categoryId = category.id).onSuccess {
                                        filteredRecipes = it
                                        isLoadingRecipes = false
                                    }.onFailure {
                                        isLoadingRecipes = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            elevation = 2.dp,
                            backgroundColor = white,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Column {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.subtitle1,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (category.parentCategory != null) {
                                        Text(
                                            text = category.parentCategory!!.name,
                                            style = MaterialTheme.typography.caption,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Show filtered recipes
                if (isLoadingRecipes) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = primary,
                    )
                } else if (filteredRecipes.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.no_recipes_in_category),
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredRecipes) { recipe ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onRecipeClick(recipe) },
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
                                    if (recipe.ingredients.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(Res.string.ingredients_count, recipe.ingredients.size),
                                            style = MaterialTheme.typography.caption,
                                            color = primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

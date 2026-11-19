package recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.ChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import data.RecipeRepository
import data.apiCode
import errors.errorText
import moderation.TermsDialog
import model.Visibility
import kotlinx.coroutines.launch
import model.Category
import model.Ingredient
import model.MeasurementUnit
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@OptIn(ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun EditRecipeScreen(
    recipeRepository: RecipeRepository,
    recipeId: String? = null,
    copyFromId: String? = null,
    onRecipeSaved: () -> Unit,
    goBack: () -> Unit,
) {
    val isCopyMode = copyFromId != null
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // Copies start Only me: what the original allowed says nothing about who should see the copy.
    var visibility by remember { mutableStateOf(Visibility.ONLY_ME) }
    var originalVisibility by remember { mutableStateOf(Visibility.ONLY_ME) }
    // From the server: only the owner (or an admin on a family recipe) may change who sees it.
    // A copy is a new recipe of your own.
    var canManage by remember { mutableStateOf(isCopyMode) }
    var removedByAdmin by remember { mutableStateOf(false) }
    var removalReason by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    // Once a copy has been created, saving again carries on with that recipe instead of making
    // another one.
    var savedId by remember { mutableStateOf(recipeId) }

    var pendingIngredients by remember { mutableStateOf<List<PendingIngredient>>(emptyList()) }
    var originalIngredientIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showIngredientDialog by remember { mutableStateOf(false) }
    // Name of an ingredient being created; drives the nutrition prompt.
    var newIngredientName by remember { mutableStateOf<String?>(null) }
    val newIngredient = remember { NewIngredientFlow() }

    var allIngredients by remember { mutableStateOf<List<Ingredient>>(emptyList()) }
    var allUnits by remember { mutableStateOf<List<MeasurementUnit>>(emptyList()) }

    var allCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // The one photo (uploaded or linked) and the video links in the form, and what the server has.
    var photoSlot by remember { mutableStateOf(PhotoSlot()) }
    var currentVideoLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var savedMedia by remember { mutableStateOf(SavedMedia()) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Bring a failure into view: the Save button is in the top bar, however far the form is scrolled.
    LaunchedEffect(error) { if (error != null) listState.animateScrollToItem(0) }

    val copySuffix = stringResource(Res.string.copy_suffix)
    LaunchedEffect(recipeId, copyFromId) {
        recipeRepository.getAllIngredients().onSuccess { allIngredients = it }
        recipeRepository.getAllUnits().onSuccess { allUnits = it }
        recipeRepository.getAllCategories().onSuccess { allCategories = it }
        val loadId = copyFromId ?: recipeId
        if (loadId != null) {
            recipeRepository.getRecipe(loadId).fold(
                onSuccess = { recipe ->
                    if (recipe != null) {
                        name = if (isCopyMode) "${recipe.title}$copySuffix" else recipe.title
                        description = recipe.description
                        if (!isCopyMode) {
                            visibility = recipe.visibility
                            originalVisibility = recipe.visibility
                            canManage = recipe.canManage
                            removedByAdmin = recipe.removedByAdmin
                            removalReason = recipe.removalReason
                        }
                        val existing = recipe.ingredients.map { ri ->
                            PendingIngredient(
                                ingredientId = ri.ingredientId,
                                ingredientName = ri.ingredientName,
                                quantity = ri.quantity,
                                unitId = null,
                                unitName = ri.unitAbbreviation,
                            )
                        }
                        pendingIngredients = existing
                        if (!isCopyMode) {
                            originalIngredientIds = recipe.ingredients.map { it.ingredientId }.toSet()
                            val catIds = recipe.categories.map { it.id }.toSet()
                            originalCategoryIds = catIds
                            savedMedia = SavedMedia(recipe.photoUrls.toSet(), recipe.videoLinks.toSet())
                        }
                        selectedCategoryIds = recipe.categories.map { it.id }.toSet()
                        // A recipe from before the one-photo rule may have several: editing shows
                        // them so extras can be removed; a copy takes only the first.
                        photoSlot = PhotoSlot(if (isCopyMode) recipe.photoUrls.take(1) else recipe.photoUrls)
                        currentVideoLinks = recipe.videoLinks
                    }
                    isLoading = false
                },
                onFailure = {
                    error = it
                    isLoading = false
                }
            )
        } else {
            isLoading = false
        }
    }

    /**
     * Saves in this order: the recipe itself (where the server's refusals about who may see it
     * come back), then ingredients, categories, photos and links. Stops at once when the first
     * step is refused. A later step that fails (a link the server will not take) is reported and
     * the screen stays open, with what did go through remembered so a second Save does not repeat it.
     */
    fun save() {
        if (name.isBlank()) return
        isSaving = true
        error = null
        scope.launch {
            var id = savedId
            if (id == null) {
                // Copy mode, first save: create the new recipe with the chosen visibility.
                val created = recipeRepository.createRecipe(name, description, visibility = visibility)
                val e = created.exceptionOrNull()
                if (e != null) {
                    if (e.apiCode == "TERMS_REQUIRED") showTerms = true else error = e
                    isSaving = false
                    return@launch
                }
                id = created.getOrThrow().id
                savedId = id
                originalVisibility = visibility
            } else {
                val changedVisibility = canManage && visibility != originalVisibility
                val updated = recipeRepository.updateRecipe(
                    recipeId = id,
                    name = name,
                    description = description,
                    visibility = if (changedVisibility) visibility else null,
                )
                val e = updated.exceptionOrNull()
                if (e != null) {
                    if (e.apiCode == "TERMS_REQUIRED") showTerms = true else error = e
                    isSaving = false
                    return@launch
                }
                originalVisibility = visibility
            }

            var failure: Throwable? = null
            fun failed(e: Throwable) { if (failure == null) failure = e }

            val currentIngredientIds = pendingIngredients.map { it.ingredientId }.toSet()
            for (ingredientId in originalIngredientIds - currentIngredientIds) {
                recipeRepository.removeIngredientFromRecipe(id, ingredientId)
                    .onSuccess { originalIngredientIds = originalIngredientIds - ingredientId }
                    .onFailure(::failed)
            }
            for (pi in pendingIngredients.filter { it.ingredientId !in originalIngredientIds }) {
                recipeRepository.addIngredientToRecipe(
                    recipeId = id,
                    ingredientId = pi.ingredientId,
                    quantity = pi.quantity,
                    unitId = pi.unitId,
                ).onSuccess { originalIngredientIds = originalIngredientIds + pi.ingredientId }
                    .onFailure(::failed)
            }

            for (catId in originalCategoryIds - selectedCategoryIds) {
                recipeRepository.removeCategoryFromRecipe(id, catId)
                    .onSuccess { originalCategoryIds = originalCategoryIds - catId }
                    .onFailure(::failed)
            }
            for (catId in selectedCategoryIds - originalCategoryIds) {
                recipeRepository.addCategoryToRecipe(id, catId)
                    .onSuccess { originalCategoryIds = originalCategoryIds + catId }
                    .onFailure(::failed)
            }

            // Photo and video links: removals first, so replacing the photo never trips the
            // server's one-photo limit.
            val media = saveMedia(recipeMediaApi(recipeRepository), id, savedMedia, photoSlot.photos, currentVideoLinks)
            savedMedia = media.saved
            // A link the server refused would be refused again: take it out of the form.
            photoSlot = photoSlot.copy(photos = photoSlot.photos - media.refusedPhotos)
            currentVideoLinks = currentVideoLinks - media.refusedVideos
            media.failure?.let(::failed)

            val problem = failure
            if (problem != null) {
                error = problem
                isSaving = false
            } else {
                onRecipeSaved()
            }
        }
    }

    /**
     * Creates the ingredient named in the nutrition prompt. Success adds it to the picker's list and
     * closes the prompt; a refusal stays on the prompt, and TERMS_REQUIRED asks for the content rules
     * first (see [NewIngredientFlow]).
     */
    fun createIngredient(request: NewIngredientRequest, afterTerms: Boolean = false) {
        newIngredient.started()
        scope.launch {
            val result = recipeRepository.createIngredient(
                name = request.name,
                caloriesPer100g = request.caloriesPer100g,
                proteinPer100g = request.proteinPer100g,
                fatPer100g = request.fatPer100g,
                carbsPer100g = request.carbsPer100g,
            )
            newIngredient.finished(request, result, afterTerms)?.let { created ->
                allIngredients = allIngredients + created
                newIngredientName = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(if (isCopyMode) stringResource(Res.string.copy_recipe_title) else stringResource(Res.string.edit_recipe_title)) },
                backgroundColor = primary,
                contentColor = white,
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { save() },
                        // Not while a photo uploads: its URL is not known yet.
                        enabled = !isSaving && !isLoading && name.isNotBlank() && !photoSlot.isUploading,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = white, strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(Res.string.save), color = white, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(sugar).padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // First in the list and scrolled into view: the Save button is in the top bar, and a message
                // at the bottom of a long form was never seen.
                error?.let { e ->
                    item {
                        Text(
                            text = errorText(e, removalReason),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.recipe_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = primary,
                            focusedLabelColor = primary,
                            cursorColor = primary,
                        ),
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(Res.string.description_label)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 5,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = primary,
                            focusedLabelColor = primary,
                            cursorColor = primary,
                        ),
                    )
                }

                if (canManage) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (removedByAdmin) {
                                val reason = removalReason
                                Text(
                                    text = if (reason.isNullOrBlank()) stringResource(Res.string.removed_by_admin_banner_no_reason)
                                    else stringResource(Res.string.removed_by_admin_banner, reason),
                                    color = MaterialTheme.colors.error,
                                    style = MaterialTheme.typography.body2,
                                )
                            }
                            VisibilityPicker(selected = visibility, onSelect = { visibility = it })
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.ingredients_section),
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { showIngredientDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_ingredient), tint = primary)
                        }
                    }
                }

                items(pendingIngredients) { pi ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pi.ingredientName, fontWeight = FontWeight.Medium)
                                Text(
                                    "${pi.quantity} ${pi.unitName ?: ""}".trim(),
                                    style = MaterialTheme.typography.caption,
                                    color = Color.Gray,
                                )
                            }
                            IconButton(onClick = {
                                pendingIngredients = pendingIngredients.filter { it !== pi }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete), tint = Color.Red)
                            }
                        }
                    }
                }

                item {
                    PhotoSlotEditor(
                        slot = photoSlot,
                        onChange = { photoSlot = it },
                        upload = recipeRepository::uploadPhoto,
                    )
                }

                item {
                    VideoLinksEditor(videos = currentVideoLinks, onChange = { currentVideoLinks = it })
                }

                item {
                    Text(
                        stringResource(Res.string.categories_section),
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                    )
                }

                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allCategories.forEach { category ->
                            val selected = category.id in selectedCategoryIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategoryIds = if (selected) {
                                        selectedCategoryIds - category.id
                                    } else {
                                        selectedCategoryIds + category.id
                                    }
                                },
                                colors = ChipDefaults.filterChipColors(
                                    selectedBackgroundColor = primary,
                                    selectedContentColor = white,
                                ),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text(category.name)
                            }
                        }
                    }
                }

            }
        }
    }

    if (showTerms) {
        TermsDialog(
            onAccept = { recipeRepository.acceptTerms() },
            onAccepted = {
                showTerms = false
                save()
            },
            onDismiss = { showTerms = false },
        )
    }

    if (showIngredientDialog) {
        AddIngredientDialog(
            ingredients = allIngredients,
            units = allUnits,
            onDismiss = { showIngredientDialog = false },
            onAdd = { pi ->
                pendingIngredients = pendingIngredients + pi
                showIngredientDialog = false
            },
            // Ask for nutrition instead of creating straight away with zeroed macros.
            onCreateIngredient = { ingredientName -> newIngredientName = ingredientName },
        )
    }

    newIngredientName?.let { pendingName ->
        NewIngredientDialog(
            ingredientName = pendingName,
            busy = newIngredient.busy,
            error = newIngredient.error,
            onDismiss = {
                newIngredientName = null
                newIngredient.clearError()
            },
            onConfirm = { calories, protein, fat, carbs ->
                createIngredient(NewIngredientRequest(pendingName, calories, protein, fat, carbs))
            },
        )
    }

    // The same content rules prompt as for sharing a recipe; accepting sends the ingredient again,
    // declining leaves the nutrition prompt open with nothing created.
    if (newIngredient.awaitingTerms != null) {
        TermsDialog(
            onAccept = { recipeRepository.acceptTerms() },
            onAccepted = { newIngredient.termsAccepted()?.let { createIngredient(it, afterTerms = true) } },
            onDismiss = { newIngredient.termsDeclined() },
        )
    }

}

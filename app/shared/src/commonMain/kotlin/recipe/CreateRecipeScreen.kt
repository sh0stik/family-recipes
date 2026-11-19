package recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import data.RecipeRepository
import data.apiCode
import errors.errorText
import moderation.TermsDialog
import model.Visibility
import dismissKeyboardOnTap
import kotlinx.coroutines.launch
import model.Ingredient
import model.MeasurementUnit
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@Composable
fun CreateRecipeScreen(
    recipeRepository: RecipeRepository,
    onRecipeCreated: () -> Unit,
    goBack: () -> Unit,
    /** Admins may file the recipe in the family collection instead of under their own account. */
    isAdmin: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(Visibility.ONLY_ME) }
    var familyCollection by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    // Once the recipe exists, saving again carries on with it instead of creating a second one,
    // and does not repeat what already went through.
    var progress by remember { mutableStateOf(NewRecipeProgress()) }

    // The one photo (uploaded while the form is open, attached right after the recipe is created)
    // and the video links.
    var photoSlot by remember { mutableStateOf(PhotoSlot()) }
    var videoLinks by remember { mutableStateOf<List<String>>(emptyList()) }

    // Ingredients to add after creation
    var pendingIngredients by remember { mutableStateOf<List<PendingIngredient>>(emptyList()) }
    var showIngredientDialog by remember { mutableStateOf(false) }
    // Name of an ingredient being created; drives the nutrition prompt.
    var newIngredientName by remember { mutableStateOf<String?>(null) }
    val newIngredient = remember { NewIngredientFlow() }

    // Reference data
    var allIngredients by remember { mutableStateOf<List<Ingredient>>(emptyList()) }
    var allUnits by remember { mutableStateOf<List<MeasurementUnit>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Bring a failure into view: the Save button is in the top bar, however far the form is scrolled.
    LaunchedEffect(error) { if (error != null) listState.animateScrollToItem(0) }

    LaunchedEffect(Unit) {
        recipeRepository.getAllIngredients().onSuccess { allIngredients = it }
        recipeRepository.getAllUnits().onSuccess { allUnits = it }
    }

    fun save() {
        if (name.isBlank()) return
        isLoading = true
        error = null
        scope.launch {
            val form = NewRecipeForm(
                name = name,
                description = description.ifBlank { null },
                familyCollection = familyCollection,
                visibility = visibility,
                ingredients = pendingIngredients,
                photos = photoSlot.photos,
                videos = videoLinks,
            )
            when (val result = saveNewRecipe(newRecipeApi(recipeRepository), form, progress)) {
                is NewRecipeSaveResult.Saved -> onRecipeCreated()
                is NewRecipeSaveResult.NotCreated -> {
                    if (result.error.apiCode == "TERMS_REQUIRED") showTerms = true else error = result.error
                    isLoading = false
                }
                is NewRecipeSaveResult.PartlySaved -> {
                    // The recipe exists; the form stays open with what failed, and Save carries on.
                    progress = result.progress
                    photoSlot = photoSlot.copy(photos = photoSlot.photos - result.refusedPhotos)
                    videoLinks = videoLinks - result.refusedVideos
                    error = result.error
                    isLoading = false
                }
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
                title = { Text(stringResource(Res.string.new_recipe_title)) },
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
                        enabled = !isLoading && name.isNotBlank() && !photoSlot.isUploading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = white, strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(Res.string.save), color = white, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
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
                        text = errorText(e),
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

            item {
                VisibilityPicker(selected = visibility, onSelect = { visibility = it })
            }

            if (isAdmin) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(Res.string.family_collection_switch), style = MaterialTheme.typography.body1)
                            Text(
                                stringResource(Res.string.family_collection_hint),
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray,
                            )
                        }
                        Switch(
                            checked = familyCollection,
                            onCheckedChange = { familyCollection = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = primary),
                        )
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
                VideoLinksEditor(videos = videoLinks, onChange = { videoLinks = it })
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

data class PendingIngredient(
    val ingredientId: String,
    val ingredientName: String,
    val quantity: Double,
    val unitId: String?,
    val unitName: String?,
)

@Composable
fun AddIngredientDialog(
    ingredients: List<Ingredient>,
    units: List<MeasurementUnit>,
    onDismiss: () -> Unit,
    onAdd: (PendingIngredient) -> Unit,
    onCreateIngredient: ((String) -> Unit)? = null,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var quantity by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf<MeasurementUnit?>(null) }
    var unitExpanded by remember { mutableStateOf(false) }

    val filteredIngredients = remember(searchQuery, ingredients) {
        if (searchQuery.isBlank()) ingredients.take(20)
        else ingredients.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(20)
    }

    val exactMatch = remember(searchQuery, ingredients) {
        ingredients.any { it.name.equals(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_ingredient_title)) },
        text = {
            // A dialog is its own window, out of reach of the app-wide tap-to-dismiss, so it
            // needs its own.
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.dismissKeyboardOnTap().verticalScroll(rememberScrollState()),
            ) {
                if (selectedIngredient == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(Res.string.search_ingredient_placeholder)) },
                        singleLine = true,
                        // Done's default action hides the keyboard; the default return key does
                        // nothing on iOS.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (searchQuery.isNotBlank() && !exactMatch && onCreateIngredient != null) {
                        TextButton(
                            onClick = { onCreateIngredient(searchQuery) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Res.string.create_ingredient, searchQuery), color = primary)
                        }
                    }

                    Column(modifier = Modifier.heightIn(max = 200.dp)) {
                        filteredIngredients.forEach { ingredient ->
                            Text(
                                text = ingredient.name,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { selectedIngredient = ingredient }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                        }
                    }
                } else {
                    Text(stringResource(Res.string.selected_label, selectedIngredient!!.name), fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(stringResource(Res.string.quantity_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Unit selector
                    Box {
                        OutlinedTextField(
                            value = selectedUnit?.let { "${it.name} (${it.abbreviation})" } ?: stringResource(Res.string.no_unit),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.unit_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                disabledTextColor = MaterialTheme.colors.onSurface,
                                disabledBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                disabledLabelColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            ),
                        )
                        // Transparent overlay to capture clicks
                        Box(
                            modifier = Modifier.matchParentSize().clickable { unitExpanded = true }
                        )
                        DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            DropdownMenuItem(onClick = {
                                selectedUnit = null
                                unitExpanded = false
                            }) {
                                Text(stringResource(Res.string.no_unit))
                            }
                            units.forEach { unit ->
                                DropdownMenuItem(onClick = {
                                    selectedUnit = unit
                                    unitExpanded = false
                                }) {
                                    Text("${unit.name} (${unit.abbreviation})")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ing = selectedIngredient
                    val qty = quantity.toDoubleOrNull()
                    if (ing != null && qty != null && qty > 0) {
                        onAdd(PendingIngredient(
                            ingredientId = ing.id,
                            ingredientName = ing.name,
                            quantity = qty,
                            unitId = selectedUnit?.id,
                            unitName = selectedUnit?.abbreviation,
                        ))
                    }
                },
                enabled = selectedIngredient != null && quantity.toDoubleOrNull() != null,
            ) {
                Text(stringResource(Res.string.add), color = primary)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedIngredient != null) {
                    selectedIngredient = null
                    quantity = ""
                    selectedUnit = null
                } else {
                    onDismiss()
                }
            }) {
                Text(if (selectedIngredient != null) stringResource(Res.string.back) else stringResource(Res.string.cancel))
            }
        },
    )
}

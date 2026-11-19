import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.setSingletonImageLoaderFactory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import recipeappkmp.generated.resources.*
import i18n.AppLocale
import auth.ForgotPasswordScreen
import auth.LoginScreen
import auth.RegisterScreen
import auth.ResetPasswordScreen
import categories.CategoriesScreen
import data.AuthRepository
import data.NetworkModule
import data.photoImageLoaderFactory
import data.RecipeRepository
import data.TokenStorage
import data.isSessionRejected
import details.ModerationActions
import details.RecipeDetails
import details.ShareRecipeDialog
import details.systemShowsCopyConfirmation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moderation.AdminRemoveDialog
import moderation.BlockUserDialog
import moderation.ReportDialog
import navigation.DeepLinks
import profile.BlockedUsersScreen
import profile.ProfileScreen
import recipe.CreateRecipeScreen
import recipe.EditRecipeScreen
import recipeslist.RecipeGroup
import recipeslist.RecipeSections
import recipeslist.RecipesListScreen
import recipeslist.classifySearch
import recipeslist.sectionsOf
import sensor.SensorManager
import trash.TrashScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App(
    sensorManager: SensorManager?,
    isLarge: Boolean = false,
    tokenStorage: TokenStorage,
    // Edge-swipe back is for iOS, which has no system back gesture of its own. Android already
    // pops the back stack from its system back gesture, so enabling this there would double up.
    enableSwipeBack: Boolean = false,
) {

    // Recipe photos: an explicit HTTP engine and a User-Agent Wikimedia accepts (data/PhotoLoading.kt).
    // Null on the web, which keeps Coil's default loader.
    photoImageLoaderFactory()?.let { setSingletonImageLoaderFactory(it) }

    val fontFamily = getFontFamily()
    val navController = rememberNavController()

    val networkModule = remember { NetworkModule(tokenStorage) }
    val authRepository = remember { AuthRepository(networkModule, tokenStorage) }
    val recipeRepository = remember { RecipeRepository(networkModule) }
    val scope = rememberCoroutineScope()
    var currentLocale by remember { mutableStateOf("en") }

    AppLocale(currentLocale) {
    MaterialTheme(typography = getTypography(fontFamily)) {

        // The main list's three sections: Mine, Shared, Public.
        var sections by remember { mutableStateOf(RecipeSections()) }
        // Which of the three the list shows, remembered across restarts in the settings store.
        var selectedGroup by remember {
            mutableStateOf(RecipeGroup.fromSaved(tokenStorage.getSetting(RecipeGroup.SETTING_KEY)))
        }
        var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
        var refreshKey by remember { mutableStateOf(0) }
        // Distinguishes "still loading" from "genuinely empty" for the list screen.
        var isRecipesLoading by remember { mutableStateOf(true) }
        var currentUsername by remember { mutableStateOf("") }
        var currentEmail by remember { mutableStateOf("") }
        var currentIsAdmin by remember { mutableStateOf(false) }

        // Search state: query plus results split into the same three sections.
        var searchQuery by remember { mutableStateOf("") }
        var searchSections by remember { mutableStateOf(RecipeSections()) }

        // Shown on the sign-in screen when the app signed the user out itself.
        var loginNotice by remember { mutableStateOf<StringResource?>(null) }

        // The server no longer accepts this sign-in (password changed on another device or this
        // one, account suspended, token expired). `isLoggedIn` only says a token is stored, so
        // without this the app sat signed in with a blank name and empty lists, even after a
        // restart. Forget the session, show the signed-out list and put the sign-in screen on
        // top with the reason. Whichever request finds out first calls this; the rest find
        // isLoggedIn already false. A user's own sign-out goes through onLogout instead and
        // shows nothing.
        fun endRejectedSession() {
            authRepository.forgetSession()
            if (!isLoggedIn) return
            isLoggedIn = false
            sections = RecipeSections()
            searchSections = RecipeSections()
            currentUsername = ""
            currentEmail = ""
            currentIsAdmin = false
            loginNotice = Res.string.session_ended
            if (navController.currentDestination?.route != RecipeAppScreen.Login.name) {
                navController.navigate(RecipeAppScreen.Login.name) {
                    popUpTo(RecipeAppScreen.List.name) { inclusive = false }
                }
            }
        }

        // Any request that came back 401 with the stored token, from any screen.
        LaunchedEffect(networkModule) {
            networkModule.sessionMonitor.rejections.collect { if (it > 0) endRejectedSession() }
        }

        // Load current user profile
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                recipeRepository.getMe()
                    .onSuccess { profile ->
                        currentUsername = profile.username
                        currentEmail = profile.email
                        currentIsAdmin = profile.isAdmin
                        currentLocale = profile.preferredLocale
                    }
                    .onFailure { e ->
                        // Holding a token does not mean the session is alive. Only a rejection
                        // signs out; offline, the cached profile or nothing is shown instead.
                        if (e.isSessionRejected()) endRejectedSession()
                    }
            }
        }

        // Load the list. Signed in: Mine, Shared and Public, each from its own query so
        // one failing only hides its own section. Signed out: Public only.
        LaunchedEffect(isLoggedIn, refreshKey) {
            isRecipesLoading = true
            if (isLoggedIn) {
                val mine = recipeRepository.getMyRecipes().getOrElse { e ->
                    println("RECIPE ERROR: ${e.message}")
                    // Only a rejection means the session is gone. This used to sign the user out
                    // on *any* failure, so a moment offline threw away the token and forced a
                    // fresh login.
                    if (e.isSessionRejected()) endRejectedSession()
                    sections.mine
                }
                val shared = recipeRepository.getSharedWithMeRecipes().getOrElse { e ->
                    println("SHARED RECIPES ERROR: ${e.message}")
                    sections.shared
                }
                val public = recipeRepository.getPublicRecipes().getOrElse { e ->
                    println("PUBLIC RECIPES ERROR: ${e.message}")
                    sections.public
                }
                if (isLoggedIn) sections = sectionsOf(mine, shared, public)
            } else {
                recipeRepository.getPublicRecipes().fold(
                    onSuccess = { sections = RecipeSections(public = it) },
                    onFailure = { println("PUBLIC RECIPES ERROR: ${it.message}") },
                )
            }
            isRecipesLoading = false
        }

        val clipboardManager = LocalClipboardManager.current
        val startDestination = RecipeAppScreen.List.name

        var currentRecipe by remember { mutableStateOf<model.Recipe?>(null) }
        var copyFromRecipeId by remember { mutableStateOf<String?>(null) }

        // A recipe link (see navigation/DeepLinks). Fetched first rather than sending a guest to
        // log in straight away: public recipes open without an account. Only when the server
        // refuses a signed-out user does the link wait on the login screen, and re-keying on
        // isLoggedIn opens it as soon as they sign in.
        val pendingRecipeId by DeepLinks.pendingRecipeId.collectAsState()
        var linkFailed by remember { mutableStateOf(false) }
        LaunchedEffect(pendingRecipeId, isLoggedIn) {
            val id = pendingRecipeId ?: return@LaunchedEffect
            recipeRepository.getRecipe(id).fold(
                onSuccess = { recipe ->
                    DeepLinks.clear()
                    if (recipe != null) {
                        currentRecipe = recipe
                        navController.navigate(RecipeAppScreen.Details.name)
                    } else {
                        linkFailed = true
                    }
                },
                onFailure = { e ->
                    if (!isLoggedIn && e.isSessionRejected()) {
                        if (navController.currentDestination?.route != RecipeAppScreen.Login.name) {
                            navController.navigate(RecipeAppScreen.Login.name)
                        }
                    } else {
                        // Signed in and still refused (not shared with them), or unreachable.
                        DeepLinks.clear()
                        linkFailed = true
                    }
                },
            )
        }
        LaunchedEffect(linkFailed) {
            if (linkFailed) {
                delay(4000)
                linkFailed = false
            }
        }

        // A short confirmation on the list: the account is gone, a report was sent, someone was
        // blocked. Set before going back to the list, which is where it is drawn.
        var listNotice by remember { mutableStateOf<StringResource?>(null) }
        LaunchedEffect(listNotice) {
            if (listNotice != null) {
                delay(5000)
                listNotice = null
            }
        }

        // Leaving a screen must take its keyboard with it — iOS otherwise keeps it up over the
        // next screen.
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(navController) {
            navController.currentBackStackEntryFlow.collect {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }

        // The entry most recently removed by edge swipe. By the time it is popped the swipe has
        // already slid it off screen, so its pop skips the usual crossfade: fading it out would
        // first bring it back into view. Keyed by entry id, so arrow and system-back pops keep
        // the default transition without any flag to reset.
        var swipedEntryId by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
        // The sugar background shows behind a screen while it is being swiped away.
        SharedTransitionLayout(modifier = Modifier.fillMaxSize().background(sugar).dismissKeyboardOnTap()) {
            val sharedTransitionScope = this
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .edgeSwipeBack(
                        enabled = enableSwipeBack,
                        canGoBack = { navController.previousBackStackEntry != null },
                        onBack = {
                            swipedEntryId = navController.currentBackStackEntry?.id
                            navController.popBackStack()
                        },
                    ),
                // Same 700ms crossfade NavHost uses by default, except for a swiped-away entry.
                // The exit is an instant fade rather than ExitTransition.None: None would keep the
                // old screen drawn — above the one being revealed — for as long as a shared
                // element is still flying back to the list.
                popEnterTransition = {
                    if (initialState.id == swipedEntryId) EnterTransition.None else fadeIn(tween(700))
                },
                popExitTransition = {
                    if (initialState.id == swipedEntryId) fadeOut(snap()) else fadeOut(tween(700))
                },
            ) {
                composable(route = RecipeAppScreen.Login.name) {
                    // The reason applies to this visit only, however the screen is left.
                    DisposableEffect(Unit) { onDispose { loginNotice = null } }
                    LoginScreen(
                        authRepository = authRepository,
                        notice = loginNotice,
                        onLoginSuccess = {
                            isLoggedIn = true
                            navController.navigate(RecipeAppScreen.List.name) {
                                popUpTo(RecipeAppScreen.Login.name) { inclusive = true }
                            }
                        },
                        onNavigateToRegister = {
                            navController.navigate(RecipeAppScreen.Register.name)
                        },
                        onNavigateToForgotPassword = {
                            navController.navigate(RecipeAppScreen.ForgotPassword.name)
                        },
                        onNavigateHome = {
                            navController.popBackStack(RecipeAppScreen.List.name, false)
                        },
                    )
                }

                composable(route = RecipeAppScreen.Register.name) {
                    RegisterScreen(
                        authRepository = authRepository,
                        onRegisterSuccess = {
                            isLoggedIn = true
                            refreshKey++
                            navController.navigate(RecipeAppScreen.List.name) {
                                popUpTo(RecipeAppScreen.List.name) { inclusive = false }
                            }
                        },
                        onNavigateToLogin = {
                            navController.popBackStack()
                        },
                    )
                }

                composable(route = RecipeAppScreen.List.name) {
                    val listAnimatedVisibilityScope = this
                    Box(modifier = Modifier.fillMaxSize()) {
                    RecipesListScreen(
                        animatedVisibilityScope = listAnimatedVisibilityScope,
                        sharedTransactionScope = sharedTransitionScope,
                        sections = sections,
                        selectedGroup = selectedGroup,
                        onGroupChange = { group ->
                            selectedGroup = group
                            tokenStorage.saveSetting(RecipeGroup.SETTING_KEY, group.name)
                        },
                        isLoading = isRecipesLoading,
                        onClick = { recipe ->
                            currentRecipe = recipe
                            navController.navigate(RecipeAppScreen.Details.name)
                        },
                        onCreateRecipe = if (isLoggedIn) { { navController.navigate(RecipeAppScreen.CreateRecipe.name) } } else null,
                        searchQuery = searchQuery,
                        onSearchChange = { query ->
                            searchQuery = query
                            if (query.isBlank()) {
                                searchSections = RecipeSections()
                            } else {
                                // searchRecipes spans everything the user may see (own, shared,
                                // public); classify the results into the three sections.
                                scope.launch {
                                    recipeRepository.searchRecipes(keyword = query, ingredientName = query).onSuccess { results ->
                                        searchSections = classifySearch(results, currentUsername)
                                    }
                                }
                            }
                        },
                        searchSections = searchSections,
                        onCategories = {
                            navController.navigate(RecipeAppScreen.Categories.name)
                        },
                        onProfile = {
                            // Signed out, the account icon must lead to a real sign-in screen —
                            // not to Profile's old built-in login form (removed; it had no
                            // forgot-password link, which made password reset unreachable).
                            if (isLoggedIn) {
                                navController.navigate(RecipeAppScreen.Profile.name)
                            } else {
                                navController.navigate(RecipeAppScreen.Login.name)
                            }
                        },
                        isLoggedIn = isLoggedIn,
                    )

                    val notice = listNotice
                    if (notice != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .navigationBarsPadding(),
                            backgroundColor = Color(0xFF323232),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = stringResource(notice),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    }
                }

                composable(route = RecipeAppScreen.Details.name) {
                    var showDeleteDialog by remember { mutableStateOf(false) }
                    var isAddingNote by remember { mutableStateOf(false) }
                    // Distinguishes "the enrich fetch failed" from a genuinely empty recipe.
                    // Without this, a failed getRecipe() left `currentRecipe` as the thin
                    // RecipeListCard object it was navigated with, and the screen rendered that
                    // as if it were the full recipe — "No ingredients added yet" / "No notes yet"
                    // looked like real data instead of a failed load. That once masked an
                    // Unauthorized for hours.
                    var detailsError by remember { mutableStateOf<String?>(null) }
                    var showShareDialog by remember { mutableStateOf(false) }
                    // What is being reported: the recipe, or the person who wrote it.
                    var reportingUser by remember { mutableStateOf<Boolean?>(null) }
                    var showBlockDialog by remember { mutableStateOf(false) }
                    var showAdminRemoveDialog by remember { mutableStateOf(false) }
                    // Copying to the clipboard is otherwise silent — Share looked like it did nothing.
                    // Holds what was copied ("Link copied" / "Copied"), null when hidden.
                    var copiedMessage by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(copiedMessage) {
                        if (copiedMessage != null) {
                            delay(2000)
                            copiedMessage = null
                        }
                    }
                    // Confirmation after reporting the author: the recipe screen stays open, so
                    // the thanks is shown here rather than on the list.
                    var reportNotice by remember { mutableStateOf<StringResource?>(null) }
                    LaunchedEffect(reportNotice) {
                        if (reportNotice != null) {
                            delay(4000)
                            reportNotice = null
                        }
                    }
                    val textCopiedLabel = stringResource(Res.string.share_copied)
                    val linkCopiedLabel = stringResource(Res.string.share_link_copied)

                    // Re-fetch recipe when returning from edit, or when a different recipe is
                    // opened (keying on refreshKey alone meant switching recipes without an
                    // intervening edit/refresh would not re-run this fetch at all).
                    LaunchedEffect(refreshKey, currentRecipe?.id) {
                        val id = currentRecipe?.id ?: return@LaunchedEffect
                        detailsError = null
                        recipeRepository.getRecipe(id).fold(
                            onSuccess = { updated ->
                                if (updated != null) currentRecipe = updated
                            },
                            onFailure = { e ->
                                // Only a genuine rejection means the session is gone; a
                                // transient failure must still be visible, not silently swallowed.
                                if (e.isSessionRejected()) {
                                    endRejectedSession()
                                } else {
                                    detailsError = e.message
                                }
                            }
                        )
                    }

                    val detailsAnimatedVisibilityScope = this
                    val ingredientsHeader = stringResource(Res.string.share_ingredients_header)
                    val recipe = currentRecipe
                    if (recipe != null) {
                        // Edit controls and notes follow the server's canEdit (the owner, the
                        // recipe's editors, an admin on a family recipe). Delete, visibility and
                        // "who can edit" follow canManage (the owner, or an admin on a family
                        // recipe). Never decided here by comparing usernames.
                        val canEdit = isLoggedIn && recipe.canEdit
                        val canManage = isLoggedIn && recipe.canManage
                        val canActOnAuthor = isLoggedIn && !recipe.canManage && recipe.ownerId != null &&
                            recipe.ownerName != currentUsername
                        fun copyAsText() {
                            val text = buildString {
                                append(recipe.title)
                                if (recipe.description.isNotBlank()) append("\n${recipe.description}")
                                if (recipe.ingredients.isNotEmpty()) {
                                    append("\n\n$ingredientsHeader")
                                    recipe.ingredients.forEach { ing ->
                                        val unit = ing.unitAbbreviation ?: ing.unitName ?: ""
                                        append("\n- ${ing.ingredientName}: ${ing.quantity} $unit".trimEnd())
                                    }
                                }
                            }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                            copiedMessage = textCopiedLabel
                        }
                        fun copyLink() {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(DeepLinks.recipeUrl(recipe.id)))
                            copiedMessage = linkCopiedLabel
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                        RecipeDetails(
                            animatedVisibilityScope = detailsAnimatedVisibilityScope,
                            sharedTransactionScope = sharedTransitionScope,
                            isLarge = isLarge,
                            sensorManager = sensorManager,
                            recipe = recipe,
                            goBack = { navController.popBackStack() },
                            onEdit = if (canEdit) { { navController.navigate(RecipeAppScreen.EditRecipe.name) } } else null,
                            onDelete = if (canManage) { { showDeleteDialog = true } } else null,
                            onCopy = if (isLoggedIn) { {
                                copyFromRecipeId = recipe.id
                                navController.navigate(RecipeAppScreen.EditRecipe.name)
                            } } else null,
                            onAddNote = if (canEdit) { { noteText: String ->
                                isAddingNote = true
                                scope.launch {
                                    recipeRepository.addNote(recipe.id, noteText).onSuccess {
                                        recipeRepository.getRecipe(recipe.id).onSuccess { updatedRecipe ->
                                            if (updatedRecipe != null) currentRecipe = updatedRecipe
                                        }
                                    }
                                    isAddingNote = false
                                }
                            } } else null,
                            onDeleteNote = if (isLoggedIn) { { noteId: String ->
                                scope.launch {
                                    recipeRepository.deleteNote(noteId).onSuccess {
                                        recipeRepository.getRecipe(recipe.id).onSuccess { updatedRecipe ->
                                            if (updatedRecipe != null) currentRecipe = updatedRecipe
                                        }
                                    }
                                }
                            } } else null,
                            isAddingNote = isAddingNote,
                            currentUsername = currentUsername,
                            moderation = ModerationActions(
                                // Any recipe you do not manage can be reported, and its author too.
                                onReport = if (isLoggedIn && !recipe.canManage) { { reportingUser = false } } else null,
                                // Admins can take any Family or Public recipe out of view.
                                onRemoveFromPublic = if (currentIsAdmin && recipe.visibility != model.Visibility.ONLY_ME) {
                                    { showAdminRemoveDialog = true }
                                } else null,
                                onReportUser = if (canActOnAuthor) { { reportingUser = true } } else null,
                                onBlockUser = if (canActOnAuthor) { { showBlockDialog = true } } else null,
                            ),
                            // Choosing who can edit is for whoever manages the recipe; everyone else
                            // gets the link, which is what you would forward — it opens the recipe
                            // with its photos and latest edits, for anyone it is visible to.
                            onShare = { if (canManage) showShareDialog = true else copyLink() },
                        )

                        val copied = copiedMessage
                        // Where the system confirms the copy itself (Android 13+), ours would only
                        // sit underneath its preview.
                        if (copied != null && !systemShowsCopyConfirmation()) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .navigationBarsPadding(),
                                backgroundColor = Color(0xFF323232),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = copied,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }

                        val sentNotice = reportNotice
                        if (sentNotice != null) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .navigationBarsPadding(),
                                backgroundColor = Color(0xFF323232),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = stringResource(sentNotice),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }

                        // A visible banner with retry rather than a silently stale screen: the
                        // ingredients/notes on screen may still be the thin list-card data, not
                        // the full recipe.
                        if (detailsError != null) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .navigationBarsPadding(),
                                backgroundColor = Color(0xFFB00020),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.recipe_load_failed),
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { refreshKey++ }) {
                                        Text(
                                            stringResource(Res.string.retry),
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                        }

                        if (showShareDialog) {
                            ShareRecipeDialog(
                                recipe = recipe,
                                currentUsername = currentUsername,
                                recipeRepository = recipeRepository,
                                onCopyLink = {
                                    showShareDialog = false
                                    copyLink()
                                },
                                onCopyText = {
                                    showShareDialog = false
                                    copyAsText()
                                },
                                onDismiss = { changed ->
                                    showShareDialog = false
                                    // Re-fetch so the recipe's sharedWith matches what was just changed.
                                    if (changed) refreshKey++
                                },
                            )
                        }

                        val reporting = reportingUser
                        if (reporting != null) {
                            ReportDialog(
                                title = if (reporting) stringResource(Res.string.report_user_title, recipe.ownerName ?: "")
                                    else stringResource(Res.string.report_recipe_title),
                                onSend = { reason, comment ->
                                    if (reporting) recipeRepository.reportUser(recipe.ownerId ?: "", reason, comment)
                                    else recipeRepository.reportRecipe(recipe.id, reason, comment)
                                },
                                onSent = {
                                    reportingUser = null
                                    if (reporting) {
                                        // Reporting the author hides nothing, so stay on the recipe.
                                        reportNotice = Res.string.report_sent_user
                                    } else {
                                        // The server hides a reported recipe, so leave it and fetch
                                        // the lists again: it is gone from them.
                                        listNotice = Res.string.report_sent_recipe
                                        refreshKey++
                                        navController.popBackStack()
                                    }
                                },
                                onDismiss = { reportingUser = null },
                            )
                        }

                        if (showAdminRemoveDialog) {
                            AdminRemoveDialog(
                                onRemove = { reason -> recipeRepository.adminUnpublishRecipe(recipe.id, reason) },
                                onRemoved = {
                                    showAdminRemoveDialog = false
                                    listNotice = Res.string.admin_removed_notice
                                    refreshKey++
                                    navController.popBackStack()
                                },
                                onDismiss = { showAdminRemoveDialog = false },
                            )
                        }

                        if (showBlockDialog) {
                            BlockUserDialog(
                                username = recipe.ownerName ?: "",
                                onBlock = { recipeRepository.blockUser(recipe.ownerId ?: "") },
                                onBlocked = {
                                    showBlockDialog = false
                                    listNotice = Res.string.block_done
                                    refreshKey++
                                    navController.popBackStack()
                                },
                                onDismiss = { showBlockDialog = false },
                            )
                        }

                        if (showDeleteDialog) {
                            androidx.compose.material.AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { androidx.compose.material.Text(stringResource(Res.string.delete_recipe_title)) },
                                text = { androidx.compose.material.Text(stringResource(Res.string.delete_recipe_message)) },
                                confirmButton = {
                                    androidx.compose.material.TextButton(onClick = {
                                        showDeleteDialog = false
                                        scope.launch {
                                            recipeRepository.moveToTrash(recipe.id)
                                            refreshKey++
                                            navController.popBackStack()
                                        }
                                    }) {
                                        androidx.compose.material.Text(stringResource(Res.string.delete), color = androidx.compose.ui.graphics.Color.Red)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material.TextButton(onClick = { showDeleteDialog = false }) {
                                        androidx.compose.material.Text(stringResource(Res.string.cancel))
                                    }
                                },
                            )
                        }
                    }
                }

                composable(route = RecipeAppScreen.CreateRecipe.name) {
                    CreateRecipeScreen(
                        recipeRepository = recipeRepository,
                        isAdmin = currentIsAdmin,
                        onRecipeCreated = {
                            refreshKey++
                            navController.popBackStack()
                        },
                        goBack = { navController.popBackStack() },
                    )
                }

                composable(route = RecipeAppScreen.EditRecipe.name) {
                    val recipe = currentRecipe
                    val copyId = copyFromRecipeId
                    if (recipe != null || copyId != null) {
                        EditRecipeScreen(
                            recipeRepository = recipeRepository,
                            recipeId = if (copyId == null) recipe?.id else null,
                            copyFromId = copyId,
                            onRecipeSaved = {
                                copyFromRecipeId = null
                                refreshKey++
                                navController.popBackStack()
                            },
                            goBack = {
                                copyFromRecipeId = null
                                navController.popBackStack()
                            },
                        )
                    }
                }

                composable(route = RecipeAppScreen.Categories.name) {
                    CategoriesScreen(
                        recipeRepository = recipeRepository,
                        onRecipeClick = { recipe ->
                            currentRecipe = recipe
                            navController.navigate(RecipeAppScreen.Details.name)
                        },
                        goBack = { navController.popBackStack() },
                    )
                }

                composable(route = RecipeAppScreen.Profile.name) {
                    ProfileScreen(
                        authRepository = authRepository,
                        recipeRepository = recipeRepository,
                        username = currentUsername,
                        email = currentEmail,
                        currentLocale = currentLocale,
                        onLogout = {
                            isLoggedIn = false
                            sections = RecipeSections()
                            currentUsername = ""
                            currentEmail = ""
                            currentIsAdmin = false
                            navController.popBackStack()
                        },
                        isAdmin = currentIsAdmin,
                        onAccountDeleted = {
                            // The session is already cleared by AuthRepository.deleteAccount.
                            isLoggedIn = false
                            sections = RecipeSections()
                            currentUsername = ""
                            currentEmail = ""
                            currentIsAdmin = false
                            listNotice = Res.string.account_deleted
                            refreshKey++
                            navController.popBackStack()
                        },
                        // Signing in again after a password change failed; the device is signed out.
                        onSessionEnded = { endRejectedSession() },
                        onNavigateToTrash = {
                            navController.navigate(RecipeAppScreen.Trash.name)
                        },
                        onNavigateToBlocked = {
                            navController.navigate(RecipeAppScreen.BlockedUsers.name)
                        },
                        onLocaleChanged = { currentLocale = it },
                        onProfileUpdated = {
                            scope.launch {
                                recipeRepository.getMe().onSuccess { profile ->
                                    currentUsername = profile.username
                                    currentEmail = profile.email
                                    currentIsAdmin = profile.isAdmin
                                    currentLocale = profile.preferredLocale
                                }
                            }
                        },
                        goBack = { navController.popBackStack() },
                    )
                }

                composable(route = RecipeAppScreen.Trash.name) {
                    TrashScreen(
                        recipeRepository = recipeRepository,
                        onRecipeRestored = { refreshKey++ },
                        goBack = { navController.popBackStack() },
                    )
                }

                composable(route = RecipeAppScreen.BlockedUsers.name) {
                    BlockedUsersScreen(
                        recipeRepository = recipeRepository,
                        // Unblocking brings their recipes back, so the lists are fetched again.
                        onChanged = { refreshKey++ },
                        goBack = { navController.popBackStack() },
                    )
                }

                composable(route = RecipeAppScreen.ForgotPassword.name) {
                    ForgotPasswordScreen(
                        authRepository = authRepository,
                        onNavigateToReset = {
                            navController.navigate(RecipeAppScreen.ResetPassword.name)
                        },
                        goBack = { navController.popBackStack() },
                    )
                }

                composable(route = RecipeAppScreen.ResetPassword.name) {
                    ResetPasswordScreen(
                        authRepository = authRepository,
                        onSuccess = {
                            navController.navigate(RecipeAppScreen.Login.name) {
                                popUpTo(RecipeAppScreen.Login.name) { inclusive = true }
                            }
                        },
                        goBack = { navController.popBackStack() },
                    )
                }
            }
        }

        // A recipe link that could not be opened. Drawn above the NavHost rather than on one
        // screen: a link arrives on whatever screen is showing, and on any but the list the
        // failure used to pass without a word, as if the link had been ignored.
        if (linkFailed) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                backgroundColor = Color(0xFF323232),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.link_open_failed),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        }
    }
    }
}

enum class RecipeAppScreen {
    Login, Register, List, Details, CreateRecipe, EditRecipe, Categories, Profile, Trash,
    ForgotPassword, ResetPassword, BlockedUsers,
}

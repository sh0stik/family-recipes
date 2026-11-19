package profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AuthRepository
import errors.errorText
import data.RecipeRepository
import kotlinx.coroutines.launch
import navigation.LegalLinks
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProfileScreen(
    authRepository: AuthRepository,
    recipeRepository: RecipeRepository,
    username: String,
    email: String = "",
    currentLocale: String = "en",
    onLogout: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToBlocked: () -> Unit,
    onProfileUpdated: () -> Unit,
    /**
     * Fired with the newly saved language as soon as the mutation succeeds. The app applies it
     * directly rather than waiting for [onProfileUpdated] to re-read the profile — that read can
     * fail (or serve a stale cache), and a failed refresh must not leave the UI in the old
     * language after a save that actually worked.
     */
    onLocaleChanged: (String) -> Unit = {},
    /** Admins cannot delete their own account (the server refuses), so the entry is hidden. */
    isAdmin: Boolean = false,
    /** The account is gone and the session already cleared; the app returns to the signed-out list. */
    onAccountDeleted: () -> Unit = {},
    /**
     * The password was changed but signing in again with it failed: the device is signed out and
     * the app says the session has ended.
     */
    onSessionEnded: () -> Unit = {},
    goBack: () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    // Keyed on the parameter itself: `username`/`currentLocale` arrive asynchronously from
    // App.kt's getMe() call, which has not necessarily resolved by the time this screen first
    // composes. An unkeyed `remember` would latch onto that first (blank/default) value and never
    // update, which is exactly what produced "Привет, !" with a blank name.
    var displayedUsername by remember(username) { mutableStateOf(username) }
    var editedUsername by remember(username) { mutableStateOf(username) }
    var displayedLocale by remember(currentLocale) { mutableStateOf(currentLocale) }
    var selectedLocale by remember(currentLocale) { mutableStateOf(currentLocale) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var showLocaleDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var successMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    val scope = rememberCoroutineScope()

    val hasChanges = editedUsername != displayedUsername || selectedLocale != displayedLocale || newPassword.isNotBlank()
    val passwordsMatch = newPassword.isBlank() || newPassword == confirmPassword
    val isPasswordLongEnough = newPassword.isBlank() || newPassword.length >= 6
    val passwordReady = newPassword.isBlank() || (isPasswordLongEnough && passwordsMatch && currentPassword.isNotBlank())

    // Only languages that actually have a translation. `es`/`fr`/`de` were offered but had no
    // string resources, so choosing one silently fell back to the default locale.
    val locales = listOf(
        "en" to "English",
        "ru" to "Русский",
    )

    val selectedLocaleName = locales.find { it.first == selectedLocale }?.second ?: selectedLocale

    if (isEditing) {
        // --- Edit mode: keep the TopAppBar with save ---
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(stringResource(Res.string.editing_title)) },
                    backgroundColor = primary,
                    contentColor = white,
                    navigationIcon = {
                        IconButton(onClick = {
                            isEditing = false
                            editedUsername = displayedUsername
                            selectedLocale = displayedLocale
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            error = null
                            successMessage = null
                        }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        if (hasChanges && passwordReady) {
                            TextButton(
                                onClick = {
                                    isSaving = true
                                    error = null
                                    successMessage = null
                                    scope.launch {
                                        val changingPassword = newPassword.isNotBlank() && currentPassword.isNotBlank()
                                        val result = saveProfile(
                                            ProfileChanges(
                                                username = displayedUsername,
                                                newUsername = editedUsername.takeIf { it != displayedUsername },
                                                newLocale = selectedLocale.takeIf { it != displayedLocale },
                                                currentPassword = currentPassword.takeIf { changingPassword },
                                                newPassword = newPassword.takeIf { changingPassword },
                                            ),
                                            profileApi(authRepository, recipeRepository),
                                        )
                                        isSaving = false
                                        when (result) {
                                            ProfileSaveResult.Saved -> {
                                                displayedUsername = editedUsername
                                                if (selectedLocale != displayedLocale) {
                                                    onLocaleChanged(selectedLocale)
                                                }
                                                displayedLocale = selectedLocale
                                                successMessage = "✓"
                                                currentPassword = ""
                                                newPassword = ""
                                                confirmPassword = ""
                                                isEditing = false
                                                onProfileUpdated()
                                            }
                                            is ProfileSaveResult.Failed -> {
                                                error = result.error
                                                if (result.passwordChanged) {
                                                    // Already in place: sending the old one as
                                                    // "current" again would be refused.
                                                    currentPassword = ""
                                                    newPassword = ""
                                                    confirmPassword = ""
                                                }
                                            }
                                            ProfileSaveResult.SessionEnded -> onSessionEnded()
                                        }
                                    }
                                },
                                enabled = !isSaving,
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = white, strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(Res.string.save), color = white, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().background(sugar).padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                OutlinedTextField(
                    value = editedUsername,
                    onValueChange = { editedUsername = it },
                    label = { Text(stringResource(Res.string.username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = primary,
                        focusedLabelColor = primary,
                        cursorColor = primary,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = 2.dp,
                ) {
                    ListItem(
                        text = { Text(stringResource(Res.string.language_label)) },
                        secondaryText = { Text(selectedLocaleName) },
                        modifier = Modifier.clickable { showLocaleDialog = true },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    stringResource(Res.string.change_password_section),
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error = null },
                    label = { Text(stringResource(Res.string.current_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    // Without this the IME treats the field as ordinary text: it learns the
                    // password and offers it back as a suggestion, masking notwithstanding.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = primary,
                        focusedLabelColor = primary,
                        cursorColor = primary,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(Res.string.new_password_label)) },
                    singleLine = true,
                    isError = !isPasswordLongEnough,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = primary,
                        focusedLabelColor = primary,
                        cursorColor = primary,
                    ),
                )
                if (!isPasswordLongEnough) {
                    Text(
                        stringResource(Res.string.password_min_length),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(Res.string.confirm_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = !passwordsMatch && confirmPassword.isNotBlank(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = primary,
                        focusedLabelColor = primary,
                        cursorColor = primary,
                    ),
                )

                if (!passwordsMatch && confirmPassword.isNotBlank()) {
                    Text(
                        stringResource(Res.string.passwords_not_match),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    )
                }

                error?.let { e ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText(e), color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                }
            }
        }
    } else {
        // --- Main profile view ---
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(stringResource(Res.string.profile_title)) },
                    backgroundColor = primary,
                    contentColor = white,
                    navigationIcon = {
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                )
            }
        ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(sugar).padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 140.dp),
            ) {
                // Greeting
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                    Text(
                        text = stringResource(Res.string.greeting),
                        style = MaterialTheme.typography.h5,
                        color = Color.Gray,
                    )
                    Text(
                        text = "${displayedUsername}!",
                        style = MaterialTheme.typography.h4,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.body1,
                        color = Color.Gray,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Menu items
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ProfileMenuItem(
                        icon = Icons.Default.Edit,
                        label = stringResource(Res.string.edit_profile),
                        onClick = { isEditing = true },
                    )

                    Divider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                    ProfileMenuItem(
                        icon = Icons.Default.Delete,
                        label = stringResource(Res.string.trash),
                        onClick = onNavigateToTrash,
                    )

                    Divider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                    ProfileMenuItem(
                        icon = Icons.Default.Close,
                        label = stringResource(Res.string.blocked_users_title),
                        onClick = onNavigateToBlocked,
                    )

                    Divider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                    ProfileMenuItem(
                        icon = Icons.Default.Lock,
                        label = stringResource(Res.string.privacy_policy),
                        onClick = { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) },
                    )

                    Divider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                    ProfileMenuItem(
                        icon = Icons.Default.Check,
                        label = stringResource(Res.string.terms_dialog_title),
                        onClick = { uriHandler.openUri(LegalLinks.TERMS) },
                    )

                    Divider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        label = stringResource(Res.string.support),
                        onClick = { uriHandler.openUri(LegalLinks.SUPPORT) },
                    )
                }

                // Success message
                if (successMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = successMessage!!,
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // Logout pinned to bottom, with account deletion quietly below it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                TextButton(
                    onClick = {
                        isLoggingOut = true
                        scope.launch {
                            authRepository.logout()
                            onLogout()
                        }
                    },
                    enabled = !isLoggingOut,
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Red, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Default.ExitToApp, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.logout), color = Color.Red)
                    }
                }
                if (!isAdmin) {
                    TextButton(onClick = { showDeleteAccountDialog = true }, enabled = !isLoggingOut) {
                        Text(
                            stringResource(Res.string.delete_account),
                            color = Color.Red.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }
            }
        }
        }
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            authRepository = authRepository,
            recipeRepository = recipeRepository,
            onDismiss = { showDeleteAccountDialog = false },
            onDeleted = {
                showDeleteAccountDialog = false
                onAccountDeleted()
            },
        )
    }

    if (showLocaleDialog) {
        AlertDialog(
            onDismissRequest = { showLocaleDialog = false },
            title = { Text(stringResource(Res.string.choose_language)) },
            text = {
                Column {
                    locales.forEach { (code, name) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    selectedLocale = code
                                    showLocaleDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedLocale == code,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = primary),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLocaleDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.body1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp),
        )
    }
}


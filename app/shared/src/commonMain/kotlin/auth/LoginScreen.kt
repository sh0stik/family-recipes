package auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import data.AuthRepository
import errors.errorText
import honey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateHome: (() -> Unit)? = null,
    /** Why the user is here, shown above the form: the server ended their session. */
    notice: StringResource? = null,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Whitespace around the username is stripped before use — phone keyboards and autocomplete
    // add trailing spaces readily. The password is passed through exactly as typed: spaces are
    // legitimate password characters, and trimming here but not at signup would lock out any
    // account whose password legitimately begins or ends with one.
    val trimmedUsername = username.trim()

    // No length rule on sign-in. The minimum applies when a password is *set*; enforcing it here
    // would stop anyone whose password predates the rule from even submitting the form. Whether
    // the credential is right is the server's decision.
    val canSubmit = trimmedUsername.isNotBlank() && password.isNotEmpty()

    // imePadding + scroll keep the submit button reachable above the keyboard instead of
    // hidden under it.
    Box(
        modifier = Modifier.fillMaxSize().background(sugar).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.app_title),
                style = MaterialTheme.typography.h3,
                fontWeight = FontWeight.Bold,
                color = primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.login_subtitle),
                style = MaterialTheme.typography.subtitle1,
                color = Color.Gray,
            )

            if (notice != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF323232),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(notice),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text(stringResource(Res.string.username_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Next,
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text(stringResource(Res.string.password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    if (canSubmit) {
                        isLoading = true
                        scope.launch {
                            authRepository.login(trimmedUsername, password).fold(
                                onSuccess = { onLoginSuccess() },
                                onFailure = { error = it; isLoading = false }
                            )
                        }
                    }
                }),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.forgot_password_link),
                color = primary,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.align(Alignment.End)
                    .clickable { onNavigateToForgotPassword() },
            )

            error?.let { e ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorText(e),
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (canSubmit) {
                        isLoading = true
                        error = null
                        scope.launch {
                            authRepository.login(trimmedUsername, password).fold(
                                onSuccess = { onLoginSuccess() },
                                onFailure = { error = it; isLoading = false }
                            )
                        }
                    }
                },
                enabled = !isLoading && canSubmit,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = primary),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = white,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.login_button), color = white, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.no_account), color = Color.Gray)
                Text(
                    text = stringResource(Res.string.register_link),
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() },
                )
            }
        }

        // Back arrow in the top-left corner, matching every other screen.
        if (onNavigateHome != null) {
            IconButton(
                onClick = onNavigateHome,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back), tint = primary)
            }
        }
    }
}

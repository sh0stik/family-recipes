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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import data.AuthRepository
import errors.errorText
import kotlinx.coroutines.launch
import navigation.LegalLinks
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Trimmed once here and used for both validation and submission, so what the form checks is
    // exactly what gets sent. The password is never trimmed: spaces are valid password characters.
    val trimmedUsername = username.trim()
    val trimmedEmail = email.trim()
    val trimmedInviteCode = inviteCode.trim()

    val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    val isEmailValid = trimmedEmail.isBlank() || emailRegex.matches(trimmedEmail)
    val isPasswordLongEnough = password.isBlank() || password.length >= MIN_PASSWORD_LENGTH
    val passwordsMatch = confirmPassword.isBlank() || password == confirmPassword
    val canSubmit = trimmedUsername.isNotBlank() && emailRegex.matches(trimmedEmail)
            && password.length >= MIN_PASSWORD_LENGTH && password == confirmPassword
            && trimmedInviteCode.isNotBlank() && acceptedTerms

    fun doRegister() {
        if (!canSubmit) return
        isLoading = true
        error = null
        scope.launch {
            authRepository.register(
                trimmedUsername,
                trimmedEmail,
                password,
                trimmedInviteCode,
                acceptedTerms = acceptedTerms,
            ).fold(
                onSuccess = { onRegisterSuccess() },
                onFailure = { error = it; isLoading = false }
            )
        }
    }

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
                text = stringResource(Res.string.register_title),
                style = MaterialTheme.typography.h3,
                fontWeight = FontWeight.Bold,
                color = primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.register_subtitle),
                style = MaterialTheme.typography.subtitle1,
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text(stringResource(Res.string.email_label)) },
                singleLine = true,
                isError = !isEmailValid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary,
                ),
            )
            if (!isEmailValid) {
                Text(
                    stringResource(Res.string.invalid_email),
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text(stringResource(Res.string.password_label)) },
                singleLine = true,
                isError = !isPasswordLongEnough,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
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

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text(stringResource(Res.string.confirm_password_label)) },
                singleLine = true,
                isError = !passwordsMatch,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    // Next rather than Done: the invite code field now follows this one.
                    imeAction = ImeAction.Next,
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary,
                ),
            )
            if (!passwordsMatch) {
                Text(
                    stringResource(Res.string.passwords_not_match),
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Registration is invite-only: the backend rejects sign-ups without the shared code,
            // so that anyone who finds the public URL cannot create an account.
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it; error = null },
                label = { Text(stringResource(Res.string.invite_code_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); doRegister() }),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary,
                ),
            )
            Text(
                stringResource(Res.string.invite_code_hint),
                color = Color.Gray,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
            )

            // The content rules: required, so the server can record the acceptance with the
            // account. The label toggles the box; the link opens the full rules.
            val uriHandler = LocalUriHandler.current
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it; error = null },
                    colors = CheckboxDefaults.colors(checkedColor = primary),
                )
                Text(
                    text = stringResource(Res.string.terms_accept_checkbox),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.weight(1f).clickable { acceptedTerms = !acceptedTerms; error = null },
                )
            }
            Text(
                text = stringResource(Res.string.terms_read_full),
                color = primary,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.fillMaxWidth()
                    .clickable { uriHandler.openUri(LegalLinks.TERMS) }
                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
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
                onClick = { doRegister() },
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
                    Text(stringResource(Res.string.create_account_button), color = white, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.already_have_account), color = Color.Gray)
                Text(
                    text = stringResource(Res.string.login_button),
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() },
                )
            }

            // Apple wants the privacy policy reachable in the app, and sign-up is where data is
            // first collected.
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.privacy_policy),
                color = Color.Gray,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) }
                    .padding(8.dp),
            )
        }

        // Back arrow in the top-left corner, matching every other screen.
        IconButton(
            onClick = onNavigateToLogin,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.back), tint = primary)
        }
    }
}

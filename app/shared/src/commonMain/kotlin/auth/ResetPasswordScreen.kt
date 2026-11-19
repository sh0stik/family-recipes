package auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import data.AuthRepository
import errors.errorText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

@Composable
fun ResetPasswordScreen(
    authRepository: AuthRepository,
    onSuccess: () -> Unit,
    goBack: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    // Reset tokens are pasted out of an email, which routinely carries a trailing newline.
    val trimmedToken = token.trim()

    val isPasswordLongEnough = newPassword.isBlank() || newPassword.length >= MIN_PASSWORD_LENGTH
    val passwordsMatch = confirmPassword.isBlank() || newPassword == confirmPassword
    val canSubmit = trimmedToken.isNotBlank() && newPassword.length >= MIN_PASSWORD_LENGTH &&
            newPassword == confirmPassword

    val resetErrorText = stringResource(Res.string.reset_error)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(Res.string.new_password_title)) },
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
        // Three fields stack the submit button low enough for the keyboard to cover it;
        // imePadding + scroll keep it reachable.
        Column(
            modifier = Modifier.fillMaxSize().background(sugar).padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.reset_password_description),
                style = MaterialTheme.typography.body1,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it; error = null },
                label = { Text(stringResource(Res.string.reset_code_label)) },
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

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; error = null },
                label = { Text(stringResource(Res.string.new_password_label)) },
                singleLine = true,
                isError = !isPasswordLongEnough,
                visualTransformation = PasswordVisualTransformation(),
                // Without this the IME treats the field as ordinary text: it learns the password
                // and offers it back as a suggestion, masking on screen notwithstanding.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text(stringResource(Res.string.confirm_password_label)) },
                singleLine = true,
                isError = !passwordsMatch && confirmPassword.isNotBlank(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                )
            }

            error?.let { e ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorText(e),
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        authRepository.resetPassword(trimmedToken, newPassword).fold(
                            onSuccess = { onSuccess() },
                            onFailure = {
                                error = it
                                isLoading = false
                            }
                        )
                    }
                },
                enabled = canSubmit && !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = primary),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = white, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.change_password_button), color = white, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

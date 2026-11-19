package auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
fun ForgotPasswordScreen(
    authRepository: AuthRepository,
    onNavigateToReset: () -> Unit,
    goBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val isValidEmail = email.isBlank() || email.contains("@")
    // Validate and send the same trimmed value; mail clients and keyboards add stray spaces.
    val trimmedEmail = email.trim()
    val canSubmit = trimmedEmail.isNotBlank() && trimmedEmail.contains("@")

    val codeSentTemplate = stringResource(Res.string.code_sent_to, email)
    val sendErrorText = stringResource(Res.string.send_error)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(Res.string.forgot_password_title)) },
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
        Column(
            modifier = Modifier.fillMaxSize().background(sugar).padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.forgot_password_description),
                style = MaterialTheme.typography.body1,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null; successMessage = null },
                label = { Text("Email") },
                singleLine = true,
                isError = !isValidEmail,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary,
                ),
            )
            if (!isValidEmail) {
                Text(
                    stringResource(Res.string.invalid_email_short),
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

            if (successMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = successMessage!!,
                    color = Color(0xFF4CAF50),
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
                    successMessage = null
                    scope.launch {
                        authRepository.requestPasswordReset(trimmedEmail).fold(
                            onSuccess = {
                                successMessage = codeSentTemplate
                                isLoading = false
                            },
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
                    Text(stringResource(Res.string.send_code_button), color = white, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToReset) {
                Text(stringResource(Res.string.already_have_code), color = primary)
            }
        }
    }
}

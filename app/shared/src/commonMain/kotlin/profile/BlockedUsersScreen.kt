package profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import data.RecipeRepository
import errors.errorText
import kotlinx.coroutines.launch
import model.User
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import sugar
import white

/** Profile -> Blocked users: the people you blocked, each with an Unblock button. */
@Composable
fun BlockedUsersScreen(
    recipeRepository: RecipeRepository,
    /** Called after someone is unblocked, so the lists can be fetched again. */
    onChanged: () -> Unit,
    goBack: () -> Unit,
) {
    var blocked by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    // People whose Unblock is in flight: their button is disabled so a double tap sends one request.
    var pending by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recipeRepository.getBlockedUsers().fold(
            onSuccess = { blocked = it },
            onFailure = { error = it },
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(Res.string.blocked_users_title)) },
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
        Box(modifier = Modifier.fillMaxSize().background(sugar).padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = primary)
                blocked.isEmpty() && error == null -> Text(
                    text = stringResource(Res.string.blocked_users_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.Gray,
                )
                else -> Column {
                    error?.let { e ->
                        Text(
                            errorText(e),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(blocked, key = { it.id }) { user ->
                            Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(user.username, modifier = Modifier.weight(1f))
                                    TextButton(
                                        enabled = user.id !in pending,
                                        onClick = {
                                            pending = pending + user.id
                                            error = null
                                            scope.launch {
                                                recipeRepository.unblockUser(user.id).fold(
                                                    onSuccess = {
                                                        blocked = blocked.filter { it.id != user.id }
                                                        onChanged()
                                                    },
                                                    onFailure = { error = it },
                                                )
                                                pending = pending - user.id
                                            }
                                        },
                                    ) {
                                        Text(stringResource(Res.string.unblock), color = primary)
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

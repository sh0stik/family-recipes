import androidx.compose.runtime.Composable
import data.TokenStorage


@Composable
fun MainView() {
    App(sensorManager = null, isLarge = true, tokenStorage = TokenStorage())
}
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import data.TokenStorage
import kotlinx.browser.document
import kotlinx.browser.window
import navigation.DeepLinks

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // A recipe link (/recipe/<id>) reaches this page through nginx's index.html fallback. Queue
    // it for App(), then put the address back to / so a refresh shows the list rather than
    // reopening the recipe — the app has no URL routing beyond this.
    if (DeepLinks.handleUrl(window.location.href)) {
        window.history.replaceState(null, "", "/")
    }

    ComposeViewport(document.body!!) {
        // isLarge=false keeps the recipe DETAIL screen on its single-column layout in the browser.
        // It no longer touches the recipe list: the list is a GridCells.Adaptive grid
        // (RecipesList.kt) that fits as many columns as the window is wide, on every platform, so
        // one browser build serves phones (1 column) and wide desktops (several) without a flag.
        // Previously the list was Fixed(3) when isLarge was true, which squeezed phone browsers into
        // 3 columns with titles clipped to one character; do not reintroduce a fixed count.
        App(
            sensorManager = null,
            isLarge = false,
            tokenStorage = TokenStorage(),
        )
    }
}

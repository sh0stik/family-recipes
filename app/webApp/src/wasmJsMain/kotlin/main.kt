import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        // Keep in step with jsMain/main.kt: isLarge=false is for the detail screen; the recipe list
        // adapts its column count to the window width by itself.
        // NOTE: this source set has no wasmJs() target in webApp/build.gradle.kts, so nothing
        // here is compiled; the App(...) call below is also missing the required tokenStorage
        // argument and would not build as-is. Kept only so the two entry points do not drift.
        App(null, false)
    }
}

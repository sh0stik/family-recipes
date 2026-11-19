package i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

// On JS/browser the resource locale ultimately comes from the browser; this still drives the
// CompositionLocal so the rest of the app reads a consistent value and recomposes on change.
actual object LocalAppLocale {
    private val local = staticCompositionLocalOf { "en" }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual fun providesAll(value: String?): Array<ProvidedValue<*>> =
        arrayOf(local provides (value ?: "en"))
}

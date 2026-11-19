package i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    private val local = staticCompositionLocalOf { Locale.getDefault().toString() }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual fun providesAll(value: String?): Array<ProvidedValue<*>> {
        val configuration = LocalConfiguration.current
        if (default == null) {
            default = Locale.getDefault()
        }
        val new = value?.let { Locale(it) } ?: default!!
        Locale.setDefault(new)

        // A NEW Configuration instance, not the current one mutated in place. Compose Resources
        // remembers its ResourceEnvironment keyed on the Configuration object, so mutating that
        // object leaves the remembered value stale and the language only changes on next launch.
        val localised = Configuration(configuration).apply { setLocale(new) }

        return arrayOf(
            local provides new.toString(),
            LocalConfiguration provides localised,
        )
    }
}

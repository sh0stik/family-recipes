package i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    private val local = staticCompositionLocalOf { Locale.getDefault().toString() }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual fun providesAll(value: String?): Array<ProvidedValue<*>> {
        if (default == null) {
            default = Locale.getDefault()
        }
        val new = value?.let { Locale(it) } ?: default!!
        Locale.setDefault(new)
        return arrayOf(local provides new.toString())
    }
}

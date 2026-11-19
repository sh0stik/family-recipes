package i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

actual object LocalAppLocale {
    private const val LANG_KEY = "AppleLanguages"
    private val default: List<*>? = NSUserDefaults.standardUserDefaults.arrayForKey(LANG_KEY)
    private val local = staticCompositionLocalOf { "en" }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual fun providesAll(value: String?): Array<ProvidedValue<*>> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) {
            defaults.setObject(default, LANG_KEY)
        } else {
            defaults.setObject(listOf(value), LANG_KEY)
        }
        return arrayOf(local provides (value ?: "en"))
    }
}

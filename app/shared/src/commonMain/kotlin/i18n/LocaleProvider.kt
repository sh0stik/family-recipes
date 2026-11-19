package i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Runtime locale override for Compose Multiplatform string resources.
 *
 * Compose `stringResource` resolves `values/` vs `values-en/` from the *platform* (system) locale,
 * so changing a locale in app state alone changes nothing. Each platform's [LocalAppLocale] actual
 * overrides the platform default locale; [AppLocale] then re-keys its subtree so every string
 * resource re-resolves when the tag changes.
 *
 * Tag is a language code such as "en" or "ru"; `null` restores the original system locale.
 */
expect object LocalAppLocale {
    val current: String @Composable get

    /**
     * Returns every CompositionLocal that has to change for a locale switch to take effect.
     *
     * More than the locale tag itself on some platforms: Android must also publish a *new*
     * `Configuration` instance, because Compose Resources caches its resource environment keyed on
     * that object. Mutating the existing instance leaves the cache stale, and the language then
     * only changes after the process restarts.
     */
    @Composable fun providesAll(value: String?): Array<ProvidedValue<*>>
}

@Composable
fun AppLocale(languageTag: String?, content: @Composable () -> Unit) {
    // Deliberately no `key(languageTag)` here. Re-keying does force strings to re-resolve, but it
    // also discards every `remember` in the app — login state, navigation, loaded recipes — so a
    // language change remounted the whole app and re-ran its startup effects. One of those treats
    // a failed load as an invalid token and signs the user out, so switching language logged you
    // out. The platform locals returned above already invalidate the resource environment.
    CompositionLocalProvider(*LocalAppLocale.providesAll(languageTag)) {
        content()
    }
}

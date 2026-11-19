package navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recipe links: `https://recipes.example.com/recipe/<id>`.
 *
 * Every platform hands its incoming URL to [handleUrl] — the web entry point at startup, Android's
 * MainActivity from its intent, iOS from SwiftUI's onOpenURL / universal-link activity — and
 * `App()` opens whatever lands in [pendingRecipeId]. A flow rather than a parameter to `App()`,
 * because Android and iOS can receive a link while the app is already running.
 */
object DeepLinks {
    // A constant, not a build property: the same host is also fixed in the Android manifest's
    // intent-filter and iosApp.entitlements, and the deployed website must serve the matching
    // .well-known/assetlinks.json and apple-app-site-association files (not included here).
    // Changing it means changing all of those together. recipes.example.com is a placeholder.
    const val WEB_HOST = "recipes.example.com"

    private val _pendingRecipeId = MutableStateFlow<String?>(null)
    val pendingRecipeId: StateFlow<String?> = _pendingRecipeId.asStateFlow()

    fun recipeUrl(id: String): String = "https://$WEB_HOST/recipe/$id"

    /** Queues the recipe from a recipe link. Returns false for any other URL, which is ignored. */
    fun handleUrl(url: String): Boolean {
        val id = recipeIdFrom(url) ?: return false
        _pendingRecipeId.value = id
        return true
    }

    fun clear() {
        _pendingRecipeId.value = null
    }

    // Only the path is checked. Android and iOS deliver nothing but WEB_HOST links (the OS filters
    // on the intent-filter / associated domain), and the web app passes its own location, which
    // is localhost while developing.
    internal fun recipeIdFrom(url: String): String? {
        val path = url.trim()
            .substringAfter("://")
            .substringBefore('#').substringBefore('?')
            .substringAfter('/', "")
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "recipe") return null
        return segments[1].takeIf { id -> id.all { it.isLetterOrDigit() || it == '-' } }
    }
}

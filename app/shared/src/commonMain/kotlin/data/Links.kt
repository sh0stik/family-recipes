package data

/**
 * Photo and video links are typed in by users and shown to everyone who can see the recipe, so
 * only plain web links are ever opened or loaded. A `javascript:` link opened by the web build
 * would run in the page and could read the login token from localStorage; `data:`, `file:`,
 * `intent:` and similar schemes have no business in a recipe either.
 */
object Links {
    private val anyScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** True for an `http://` or `https://` link with something after the scheme. */
    fun isWebLink(url: String?): Boolean {
        val u = url?.trim() ?: return false
        val lower = u.lowercase()
        val prefix = when {
            lower.startsWith("https://") -> "https://"
            lower.startsWith("http://") -> "http://"
            else -> return false
        }
        return u.length > prefix.length && u.none { it.isWhitespace() }
    }

    /**
     * What an image loader may fetch for a stored photo: web links as they are, server paths such
     * as `/uploads/abc.jpg` prefixed with [baseUrl], and null for anything carrying another scheme.
     */
    fun imageUrl(url: String?, baseUrl: String): String? {
        if (url.isNullOrBlank()) return null
        val u = url.trim()
        if (isWebLink(u)) return u
        if (anyScheme.containsMatchIn(u) || u.startsWith("//")) return null
        return "$baseUrl${if (u.startsWith("/")) u else "/$u"}"
    }
}

package data

import coil3.ImageLoader
import coil3.PlatformContext

/**
 * The User-Agent the apps send when they download a recipe photo. Photos are links people paste,
 * often to Wikimedia Commons, and upload.wikimedia.org answers 403 to a missing or generic
 * User-Agent ("Please set a user-agent and respect our robot policy", https://w.wiki/4wJS). Its
 * policy asks for the app's name and version and a way to reach the owner.
 */
object PhotoUserAgent {
    const val APP_ID = "com.daria.recipes"
    const val CONTACT_URL = "https://recipes.example.com/support.html"

    /** Used when the platform cannot tell the app's version (the desktop target). */
    const val FALLBACK_VERSION = "1.0"

    /** E.g. `MomsRecipes/1.0 (Android; com.daria.recipes; +https://recipes.example.com/support.html)`. */
    fun of(version: String?, platform: String): String {
        val v = version?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) } ?: FALLBACK_VERSION
        return "MomsRecipes/$v ($platform; $APP_ID; +$CONTACT_URL)"
    }
}

/**
 * The image loader every AsyncImage uses, installed once by App(). On Android, iOS and desktop it
 * downloads photos with a Ktor client on an explicit engine (OkHttp, Darwin) that sends
 * [PhotoUserAgent]; Coil's default loader there had no engine at all and failed every photo
 * before sending anything. Null on the web: the browser fetches photos itself, sends its own
 * User-Agent and does not let a page change it, so Coil's default loader stays.
 */
expect fun photoImageLoaderFactory(): ((PlatformContext) -> ImageLoader)?

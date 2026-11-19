package navigation

/**
 * The static pages served next to the web app (webApp/src/jsMain/resources). Apple requires the
 * privacy policy to be reachable from inside the app as well as from the store listing.
 */
object LegalLinks {
    const val PRIVACY_POLICY = "https://${DeepLinks.WEB_HOST}/privacy.html"
    const val TERMS = "https://${DeepLinks.WEB_HOST}/terms.html"
    const val SUPPORT = "https://${DeepLinks.WEB_HOST}/support.html"
}

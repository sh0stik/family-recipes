package data

import kotlinx.browser.localStorage

// Backed by localStorage rather than a field: the web app is a page, and a refresh or a
// reopened tab must not sign the user out. (The wasmJs actual keeps the token in memory
// and does lose it on reload.)
actual class TokenStorage {
    actual fun getToken(): String? = localStorage.getItem(KEY)

    actual fun saveToken(token: String) {
        localStorage.setItem(KEY, token)
    }

    actual fun clearToken() {
        localStorage.removeItem(KEY)
    }

    actual fun getSetting(key: String): String? = localStorage.getItem("recipe_app_setting_$key")

    actual fun saveSetting(key: String, value: String) {
        localStorage.setItem("recipe_app_setting_$key", value)
    }

    private companion object {
        const val KEY = "recipe_app_token"
    }
}

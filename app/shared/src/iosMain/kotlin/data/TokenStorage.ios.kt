package data

import platform.Foundation.NSUserDefaults

actual class TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getToken(): String? = defaults.stringForKey(KEY)

    actual fun saveToken(token: String) {
        defaults.setObject(token, forKey = KEY)
    }

    actual fun clearToken() {
        defaults.removeObjectForKey(KEY)
    }

    actual fun getSetting(key: String): String? = defaults.stringForKey("recipe_settings.$key")

    actual fun saveSetting(key: String, value: String) {
        defaults.setObject(value, forKey = "recipe_settings.$key")
    }

    private companion object {
        const val KEY = "recipe_auth.jwt_token"
    }
}

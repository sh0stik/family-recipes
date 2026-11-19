package data

import android.content.Context
import android.content.SharedPreferences

actual class TokenStorage(private val context: Context) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("recipe_auth", Context.MODE_PRIVATE)

    actual fun getToken(): String? = prefs.getString("jwt_token", null)

    actual fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    actual fun clearToken() {
        prefs.edit().remove("jwt_token").apply()
    }

    private val settings: SharedPreferences
        get() = context.getSharedPreferences("recipe_settings", Context.MODE_PRIVATE)

    actual fun getSetting(key: String): String? = settings.getString(key, null)

    actual fun saveSetting(key: String, value: String) {
        settings.edit().putString(key, value).apply()
    }
}

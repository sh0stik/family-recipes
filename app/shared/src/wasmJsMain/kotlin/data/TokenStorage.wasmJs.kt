package data

actual class TokenStorage {
    private var token: String? = null

    actual fun getToken(): String? = token
    actual fun saveToken(token: String) { this.token = token }
    actual fun clearToken() { token = null }

    private val settings = mutableMapOf<String, String>()
    actual fun getSetting(key: String): String? = settings[key]
    actual fun saveSetting(key: String, value: String) { settings[key] = value }
}

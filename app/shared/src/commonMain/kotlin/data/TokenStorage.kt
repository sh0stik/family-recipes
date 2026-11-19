package data

expect class TokenStorage {
    fun getToken(): String?
    fun saveToken(token: String)
    fun clearToken()

    /**
     * Small non-secret preferences (the list's selected group). Unlike the token these survive
     * sign-out. Android, iOS and the web (js) keep them across restarts; the desktop and wasmJs
     * actuals hold them for the session only.
     */
    fun getSetting(key: String): String?
    fun saveSetting(key: String, value: String)
}

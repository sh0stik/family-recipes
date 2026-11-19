package data

import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.cache.normalized.apolloStore

class AuthRepository(private val networkModule: NetworkModule, private val tokenStorage: TokenStorage) {

    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val response = networkModule.apolloClient.mutation(LoginMutation(username, password)).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Login failed"))
            } else {
                val token = response.data?.login ?: throw Exception("No token received")
                tokenStorage.saveToken(token)
                // Drop any cached data from a previous account/guest session so the new
                // user never sees stale recipes/profile (default fetch policy is CacheFirst).
                networkModule.apolloClient.apolloStore.clearAll()
                Result.success(token)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        inviteCode: String,
        acceptedTerms: Boolean,
    ): Result<String> {
        return try {
            val response = networkModule.apolloClient.mutation(
                CreateUserMutation(username, email, password, inviteCode, Optional.present(acceptedTerms))
            ).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Registration failed"))
            } else {
                val token = response.data?.createUser ?: throw Exception("No token received")
                tokenStorage.saveToken(token)
                networkModule.apolloClient.apolloStore.clearAll()
                Result.success(token)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out. The token is forgotten before the server is told, and the logout request carries
     * it explicitly: if the server has already rejected the token, its 401 then finds nothing
     * stored and is not mistaken for the session ending on its own (see [SessionMonitor]), which
     * would show "Your session has ended" after the user simply chose to sign out.
     */
    suspend fun logout(): Result<Boolean> {
        val token = tokenStorage.getToken()
        tokenStorage.clearToken()
        return try {
            if (token == null) return Result.success(true)
            val response = networkModule.apolloClient.mutation(LogoutMutation())
                .addHttpHeader("Authorization", "Bearer $token")
                .execute()
            Result.success(response.data?.logout ?: true)
        } catch (e: Exception) {
            Result.success(true)
        } finally {
            networkModule.apolloClient.apolloStore.clearAll()
        }
    }

    /**
     * Forgets the session on this device without asking the server: for a sign-in the server has
     * already rejected, where a logout request would only be refused too. Clears the cache as
     * [logout] does, so nothing from the account is shown afterwards.
     */
    fun forgetSession() {
        tokenStorage.clearToken()
        networkModule.apolloClient.apolloStore.clearAll()
    }

    /**
     * Permanently deletes the signed-in account. On success the session is cleared exactly as in
     * [logout]. A failure carries the server's error code (see [DeleteAccountException]) so the
     * dialog can say "wrong password" instead of a raw English message.
     */
    suspend fun deleteAccount(password: String): Result<Unit> {
        return try {
            val response = networkModule.apolloClient.mutation(DeleteAccountMutation(password)).execute()
            val error = response.errors?.firstOrNull()
            if (error != null) {
                Result.failure(DeleteAccountException(error.extensions?.get("code") as? String, error.message))
            } else {
                tokenStorage.clearToken()
                networkModule.apolloClient.apolloStore.clearAll()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestPasswordReset(email: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                RequestPasswordResetMutation(email)
            ).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Request failed"))
            } else {
                Result.success(response.data?.requestPasswordReset ?: false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                ChangePasswordMutation(currentPassword, newPassword)
            ).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Change failed"))
            } else {
                Result.success(response.data?.changePassword ?: false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs this device in again, for after the password was changed (or the username, with the
     * password at hand): the server refuses every token issued before the change, so the stored
     * one is now useless. It is cleared first, because the server refuses any request carrying it
     * — the login included. On success the new token is stored and the cache cleared, as [login]
     * does. On failure the device is left signed out, never holding the refused token.
     */
    suspend fun signInAgain(username: String, password: String): Result<String> {
        tokenStorage.clearToken()
        return login(username, password).onFailure { forgetSession() }
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<Boolean> {
        return try {
            val response = networkModule.apolloClient.mutation(
                ResetPasswordMutation(token, newPassword)
            ).execute()
            if (response.hasErrors()) {
                Result.failure(response.firstApiError() ?: ApiException(null, "Reset failed"))
            } else {
                Result.success(response.data?.resetPassword ?: false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = tokenStorage.getToken() != null
}

/** deleteAccount failed. [code] is WRONG_PASSWORD, ADMIN_CANNOT_DELETE, RATE_LIMITED, or null. */
class DeleteAccountException(val code: String?, message: String) : Exception(message)

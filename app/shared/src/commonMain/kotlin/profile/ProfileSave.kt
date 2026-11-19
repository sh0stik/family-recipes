package profile

import data.AuthRepository
import data.RecipeRepository

/** What the edit form asks to change. A null field is left as it is. */
data class ProfileChanges(
    /** The name the account has now, before this save. */
    val username: String,
    val newUsername: String? = null,
    val newLocale: String? = null,
    val currentPassword: String? = null,
    val newPassword: String? = null,
)

/** The calls a profile save makes. [profileApi] wires them to the repositories. */
interface ProfileApi {
    suspend fun updateLocale(locale: String): Result<Boolean>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Boolean>
    suspend fun updateUsername(newUsername: String): Result<Boolean>
    /** Replaces the stored token with a new one, or leaves the device signed out. */
    suspend fun signInAgain(username: String, password: String): Result<String>
}

fun profileApi(auth: AuthRepository, recipes: RecipeRepository) = object : ProfileApi {
    override suspend fun updateLocale(locale: String) = recipes.updateLocale(locale)
    override suspend fun changePassword(currentPassword: String, newPassword: String) =
        auth.changePassword(currentPassword, newPassword)
    override suspend fun updateUsername(newUsername: String) = recipes.updateUsername(newUsername)
    override suspend fun signInAgain(username: String, password: String) = auth.signInAgain(username, password)
}

sealed interface ProfileSaveResult {
    data object Saved : ProfileSaveResult

    /**
     * Something was refused; [error] is the last refusal. The rest may have been saved:
     * [passwordChanged] says whether the new password is already in place, so the form does not
     * send the old one as "current" again.
     */
    data class Failed(val error: Throwable, val passwordChanged: Boolean) : ProfileSaveResult

    /**
     * The password changed, but signing in again with it failed. The device is signed out; the
     * app says the session has ended.
     */
    data object SessionEnded : ProfileSaveResult
}

/**
 * Saves the profile edits. Two things about the server decide the order:
 *
 * - Changing the password makes it refuse every token issued before, this device's included, so
 *   the device signs in again straight away with the new password and keeps working.
 * - The token names the account by its username, so renaming makes the token anonymous: nothing
 *   after a rename can be sent with it. The rename therefore goes last, and when the password was
 *   changed in the same save the device signs in once more, under the new name.
 *
 * Each change is tried even when an earlier one was refused, as before.
 */
suspend fun saveProfile(changes: ProfileChanges, api: ProfileApi): ProfileSaveResult {
    var error: Throwable? = null
    var newPassword: String? = null

    changes.newLocale?.let { locale ->
        api.updateLocale(locale).onFailure { error = it }
    }

    if (changes.currentPassword != null && changes.newPassword != null) {
        api.changePassword(changes.currentPassword, changes.newPassword).fold(
            onSuccess = {
                if (api.signInAgain(changes.username, changes.newPassword).isFailure) {
                    return ProfileSaveResult.SessionEnded
                }
                newPassword = changes.newPassword
            },
            onFailure = { error = it },
        )
    }

    changes.newUsername?.let { name ->
        api.updateUsername(name).fold(
            onSuccess = {
                val password = newPassword
                if (password != null && api.signInAgain(name, password).isFailure) {
                    return ProfileSaveResult.SessionEnded
                }
            },
            onFailure = { error = it },
        )
    }

    return error?.let { ProfileSaveResult.Failed(it, passwordChanged = newPassword != null) }
        ?: ProfileSaveResult.Saved
}

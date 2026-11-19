package profile

import data.ApiException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileSaveTest {
    /** Records each call and answers from the given results; never actually suspends. */
    private class FakeApi(
        val locale: Result<Boolean> = Result.success(true),
        val password: Result<Boolean> = Result.success(true),
        val rename: Result<Boolean> = Result.success(true),
        val signIns: MutableList<Result<String>> = mutableListOf(),
    ) : ProfileApi {
        val calls = mutableListOf<String>()
        override suspend fun updateLocale(locale: String) = this.locale.also { calls += "locale $locale" }
        override suspend fun changePassword(currentPassword: String, newPassword: String) =
            password.also { calls += "password $currentPassword>$newPassword" }
        override suspend fun updateUsername(newUsername: String) = rename.also { calls += "rename $newUsername" }
        override suspend fun signInAgain(username: String, password: String): Result<String> {
            calls += "sign in $username/$password"
            return if (signIns.isEmpty()) Result.success("token-${calls.size}") else signIns.removeAt(0)
        }
    }

    private fun save(changes: ProfileChanges, api: ProfileApi): ProfileSaveResult {
        var outcome: Result<ProfileSaveResult>? = null
        suspend { saveProfile(changes, api) }.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return outcome!!.getOrThrow()
    }

    private val refused = Result.failure<String>(ApiException("INVALID_CREDENTIALS", "Invalid username or password"))

    @Test
    fun aNewPasswordSignsThisDeviceInAgainWithIt() {
        val api = FakeApi()
        val result = save(ProfileChanges("anna", currentPassword = "old-pass", newPassword = "new-pass"), api)
        assertEquals(ProfileSaveResult.Saved, result)
        assertEquals(listOf("password old-pass>new-pass", "sign in anna/new-pass"), api.calls)
    }

    @Test
    fun ifSigningInAgainFailsTheSessionHasEnded() {
        val api = FakeApi(signIns = mutableListOf(refused))
        val result = save(ProfileChanges("anna", currentPassword = "old-pass", newPassword = "new-pass"), api)
        assertEquals(ProfileSaveResult.SessionEnded, result)
    }

    @Test
    fun aWrongCurrentPasswordChangesNothingAndKeepsTheSession() {
        val wrong = ApiException("WRONG_PASSWORD", "Current password is incorrect")
        val api = FakeApi(password = Result.failure(wrong))
        val result = save(ProfileChanges("anna", currentPassword = "typo", newPassword = "new-pass"), api)
        assertEquals(ProfileSaveResult.Failed(wrong, passwordChanged = false), result)
        assertTrue(api.calls.none { it.startsWith("sign in") })
    }

    @Test
    fun renamingWithANewPasswordSignsInUnderTheNewName() {
        // The rename goes after the password change, while the device has a token, and the token
        // names the old account name, so the last sign-in uses the new one.
        val api = FakeApi()
        val result = save(
            ProfileChanges("anna", newUsername = "anya", newLocale = "ru", currentPassword = "old-pass", newPassword = "new-pass"),
            api,
        )
        assertEquals(ProfileSaveResult.Saved, result)
        assertEquals(
            listOf("locale ru", "password old-pass>new-pass", "sign in anna/new-pass", "rename anya", "sign in anya/new-pass"),
            api.calls,
        )
    }

    @Test
    fun aFailedSignInAfterTheRenameEndsTheSession() {
        val api = FakeApi(signIns = mutableListOf(Result.success("t1"), refused))
        val result = save(
            ProfileChanges("anna", newUsername = "anya", currentPassword = "old-pass", newPassword = "new-pass"),
            api,
        )
        assertEquals(ProfileSaveResult.SessionEnded, result)
    }

    @Test
    fun aRefusedRenameStillKeepsTheNewPasswordAndTheSession() {
        val taken = ApiException("USERNAME_TAKEN", "Username already taken")
        val api = FakeApi(rename = Result.failure(taken))
        val result = save(
            ProfileChanges("anna", newUsername = "boris", currentPassword = "old-pass", newPassword = "new-pass"),
            api,
        )
        assertEquals(ProfileSaveResult.Failed(taken, passwordChanged = true), result)
        assertEquals(listOf("password old-pass>new-pass", "sign in anna/new-pass", "rename boris"), api.calls)
    }

    @Test
    fun withoutAPasswordChangeThereIsNoSignIn() {
        val api = FakeApi()
        assertIs<ProfileSaveResult.Saved>(save(ProfileChanges("anna", newLocale = "en"), api))
        assertEquals(listOf("locale en"), api.calls)
    }
}

package data

import kotlinx.coroutines.runBlocking
import profile.ProfileChanges
import profile.ProfileSaveResult
import profile.profileApi
import profile.saveProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Changing the password through the app's own repositories and Apollo client, against a fake
 * server that behaves like the backend: once the password changes, any request carrying the old
 * token gets 401 — a login included.
 */
class PasswordChangeSignInTest {
    private class Backend(private val loginAnswer: FakeAnswer) {
        var passwordChanged = false

        fun answer(request: ReceivedRequest): FakeAnswer {
            if (request.authorization == "Bearer old-token" && passwordChanged) {
                return FakeAnswer(401, FakeGraphqlServer.REJECTED_BODY)
            }
            return when {
                "changePassword" in request.body -> {
                    passwordChanged = true
                    FakeAnswer(200, """{"data":{"changePassword":true}}""")
                }
                "login" in request.body -> loginAnswer
                "me" in request.body && request.authorization == "Bearer new-token" -> FakeAnswer(
                    200,
                    """{"data":{"me":{"id":"1","username":"anna","email":"a@x.test","isAdmin":false,"preferredLocale":"en"}}}""",
                )
                else -> FakeAnswer(200, """{"data":null,"errors":[{"message":"Unauthorized"}]}""")
            }
        }
    }

    private val changes = ProfileChanges("anna", currentPassword = "old-pass", newPassword = "new-pass")

    @Test
    fun theDeviceStaysSignedInWithANewToken() = runBlocking {
        val backend = Backend(FakeAnswer(200, """{"data":{"login":"new-token"}}"""))
        FakeGraphqlServer(backend::answer).use { server ->
            val tokens = TokenStorage().apply { saveToken("old-token") }
            val network = NetworkModule(tokens, server.url)
            val recipes = RecipeRepository(network)

            val result = saveProfile(changes, profileApi(AuthRepository(network, tokens), recipes))

            assertEquals(ProfileSaveResult.Saved, result)
            assertEquals("new-token", tokens.getToken())
            val login = server.received.single { "login" in it.body }
            assertNull(login.authorization, "the refused token must not go out with the login")
            assertEquals(0, network.sessionMonitor.rejections.value)
            assertEquals("anna", recipes.getMe().getOrThrow().username)
        }
    }

    @Test
    fun aRefusedSignInLeavesTheDeviceSignedOut() = runBlocking {
        val backend = Backend(FakeAnswer(200, """{"data":null,"errors":[{"message":"Invalid username or password","extensions":{"code":"INVALID_CREDENTIALS"}}]}"""))
        FakeGraphqlServer(backend::answer).use { server ->
            val tokens = TokenStorage().apply { saveToken("old-token") }
            val network = NetworkModule(tokens, server.url)

            val result = saveProfile(changes, profileApi(AuthRepository(network, tokens), RecipeRepository(network)))

            assertEquals(ProfileSaveResult.SessionEnded, result)
            assertNull(tokens.getToken())
        }
    }

    @Test
    fun anUnreachableServerAtSignInLeavesTheDeviceSignedOut() = runBlocking {
        val backend = Backend(FakeAnswer(503, """{"error":"Service Unavailable"}"""))
        FakeGraphqlServer(backend::answer).use { server ->
            val tokens = TokenStorage().apply { saveToken("old-token") }
            val network = NetworkModule(tokens, server.url)

            val result = saveProfile(changes, profileApi(AuthRepository(network, tokens), RecipeRepository(network)))

            assertEquals(ProfileSaveResult.SessionEnded, result)
            assertNull(tokens.getToken())
            assertTrue(server.received.none { "login" in it.body && it.authorization != null })
        }
    }
}

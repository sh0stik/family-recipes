package data

import com.apollographql.apollo3.exception.ApolloCompositeException
import com.apollographql.apollo3.exception.ApolloHttpException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real Apollo client, as NetworkModule builds it, against a server that rejects the token the
 * way the backend's JwtAuthenticationFilter does (HTTP 401 before GraphQL), and against servers
 * that are merely broken or unreachable.
 */
class RejectedSessionTest {
    private fun signedIn(url: String): Triple<TokenStorage, NetworkModule, RecipeRepository> {
        val tokens = TokenStorage().apply { saveToken("old-token") }
        val network = NetworkModule(tokens, url)
        return Triple(tokens, network, RecipeRepository(network))
    }

    @Test
    fun aRejectedTokenArrivesAsACompositeThatOnlyHoldsThe401Inside() = runBlocking {
        FakeGraphqlServer { FakeAnswer(401, FakeGraphqlServer.REJECTED_BODY) }.use { server ->
            val (tokens, network, repo) = signedIn(server.url)

            val e = repo.getMe().exceptionOrNull()!!

            // What reached App.kt: NetworkFirst found nothing cached after the network failed, so
            // Apollo threw both failures together. Neither the message nor the cause says 401,
            // which is why matching on the message never signed anyone out.
            assertTrue(e is ApolloCompositeException, "got ${e::class.simpleName}: ${e.message}")
            assertEquals("multiple exceptions happened", e.message)
            assertFalse(e.message!!.contains("401"))
            assertFalse(e.cause!!.message.orEmpty().contains("401"))
            val http = e.suppressedExceptions.filterIsInstance<ApolloHttpException>().single()
            assertEquals(401, http.statusCode)

            assertTrue(e.isSessionRejected())
            assertNull(tokens.getToken(), "the rejected token is forgotten")
            assertEquals(1, network.sessionMonitor.rejections.value)
        }
    }

    @Test
    fun theListsAreRejectedTooAndTheNextRequestGoesOutSignedOut() = runBlocking {
        FakeGraphqlServer { request ->
            if (request.authorization != null) FakeAnswer(401, FakeGraphqlServer.REJECTED_BODY)
            else FakeAnswer(200, """{"data":{"publicRecipes":[]}}""")
        }.use { server ->
            val (tokens, network, repo) = signedIn(server.url)

            assertTrue(repo.getMyRecipes().exceptionOrNull()!!.isSessionRejected())
            assertEquals(1, network.sessionMonitor.rejections.value)
            assertNull(tokens.getToken())

            // Public was empty in the bug because it, too, carried the rejected token.
            assertTrue(repo.getPublicRecipes().isSuccess)
            assertNull(server.received.last().authorization)
        }
    }

    @Test
    fun aServerErrorKeepsTheSession() = runBlocking {
        FakeGraphqlServer { FakeAnswer(500, """{"error":"Internal Server Error"}""") }.use { server ->
            val (tokens, network, repo) = signedIn(server.url)

            val e = repo.getMe().exceptionOrNull()!!
            assertFalse(e.isSessionRejected())
            assertEquals("old-token", tokens.getToken())
            assertEquals(0, network.sessionMonitor.rejections.value)
        }
    }

    @Test
    fun beingOfflineKeepsTheSession() = runBlocking {
        val (tokens, network, repo) = signedIn(FakeGraphqlServer.unreachableUrl())

        val e = repo.getMe().exceptionOrNull()!!
        assertFalse(e.isSessionRejected(), "got ${e::class.simpleName}: ${e.message}")
        assertFalse(repo.getMyRecipes().exceptionOrNull()!!.isSessionRejected())
        assertEquals("old-token", tokens.getToken())
        assertEquals(0, network.sessionMonitor.rejections.value)
    }

    @Test
    fun aRefusalOfASignedOutRequestIsNotASessionEnding() = runBlocking {
        FakeGraphqlServer { FakeAnswer(401, FakeGraphqlServer.REJECTED_BODY) }.use { server ->
            val tokens = TokenStorage()
            val network = NetworkModule(tokens, server.url)

            RecipeRepository(network).getPublicRecipes()
            assertEquals(0, network.sessionMonitor.rejections.value)
        }
    }

    @Test
    fun signingOutWithATokenTheServerAlreadyRejectedIsAnOrdinarySignOut() = runBlocking {
        FakeGraphqlServer { FakeAnswer(401, FakeGraphqlServer.REJECTED_BODY) }.use { server ->
            val (tokens, network, _) = signedIn(server.url)

            AuthRepository(network, tokens).logout()

            assertNull(tokens.getToken())
            assertEquals("Bearer old-token", server.received.single().authorization)
            assertEquals(0, network.sessionMonitor.rejections.value, "no \"session ended\" message")
        }
    }
}

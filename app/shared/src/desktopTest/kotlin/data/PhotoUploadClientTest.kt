package data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real upload client (Ktor multipart, as on Android and iOS) through NetworkModule and the
 * repository, against a local stand-in for POST /api/photos.
 */
class PhotoUploadClientTest {
    private class Received(val contentType: String?, val authorization: String?, val body: ByteArray)

    private class FakeUploadServer(private val answer: (Received) -> Pair<Int, String>) : AutoCloseable {
        val received = mutableListOf<Received>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/photos") { exchange ->
                val request = Received(
                    exchange.requestHeaders.getFirst("Content-Type"),
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestBody.readBytes(),
                )
                synchronized(received) { received += request }
                val (status, body) = answer(request)
                val bytes = body.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val graphqlUrl = "http://127.0.0.1:${server.address.port}/graphql"
        override fun close() = server.stop(0)
    }

    private fun signedIn(url: String): Triple<TokenStorage, NetworkModule, RecipeRepository> {
        val tokens = TokenStorage().apply { saveToken("good-token") }
        val network = NetworkModule(tokens, url)
        return Triple(tokens, network, RecipeRepository(network))
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x11, 0x22, 0x33)

    @Test
    fun aPictureGoesUpAsMultipartWithTheTokenAndTheUrlComesBack() = runBlocking {
        FakeUploadServer { 200 to """{"url":"/uploads/5c1d.jpg"}""" }.use { server ->
            val (_, _, repo) = signedIn(server.graphqlUrl)
            val url = repo.uploadPhoto(jpeg).getOrThrow()
            assertEquals("/uploads/5c1d.jpg", url)

            val request = server.received.single()
            assertEquals("Bearer good-token", request.authorization)
            assertTrue(request.contentType!!.startsWith("multipart/form-data"), request.contentType)
            val body = String(request.body, Charsets.ISO_8859_1)
            // Ktor leaves the field name unquoted (name=file); servers accept both.
            assertTrue(Regex("form-data; name=\"?file\"?;").containsMatchIn(body), body)
            assertTrue(body.contains("Content-Type: image/jpeg"), body)
            assertTrue(body.contains(String(jpeg, Charsets.ISO_8859_1)), "the picture's bytes are in the body")
        }
    }

    @Test
    fun refusalsComeBackWithTheServersCode() = runBlocking {
        for ((status, code) in listOf(400 to "NOT_AN_IMAGE", 413 to "TOO_LARGE", 429 to "RATE_LIMITED")) {
            FakeUploadServer { status to """{"code":"$code"}""" }.use { server ->
                val (tokens, _, repo) = signedIn(server.graphqlUrl)
                val failure = repo.uploadPhoto(jpeg).exceptionOrNull()!!
                assertEquals(code, failure.apiCode)
                assertEquals("good-token", tokens.getToken(), "a refusal is not a sign-out")
            }
        }
    }

    @Test
    fun a401SignsTheAppOutLikeAnyRejectedRequest() = runBlocking {
        FakeUploadServer { 401 to """{"code":"UNAUTHENTICATED"}""" }.use { server ->
            val (tokens, network, repo) = signedIn(server.graphqlUrl)
            val failure = repo.uploadPhoto(jpeg).exceptionOrNull()!!
            assertTrue(failure.isSessionRejected())
            assertNull(tokens.getToken(), "the rejected token is forgotten")
            assertEquals(1, network.sessionMonitor.rejections.value)
        }
    }

    @Test
    fun anUnreachableServerIsANetworkFailureAndKeepsTheSession() = runBlocking {
        val (tokens, network, repo) = signedIn(FakeGraphqlServer.unreachableUrl())
        val failure = repo.uploadPhoto(jpeg).exceptionOrNull()!!
        assertIs<UploadNetworkException>(failure)
        assertEquals("good-token", tokens.getToken())
        assertEquals(0, network.sessionMonitor.rejections.value)
    }
}

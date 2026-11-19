package data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/** One request the fake server received: the GraphQL body and the Authorization header, if any. */
data class ReceivedRequest(val body: String, val authorization: String?)

/** What the fake server answers: an HTTP status and a body. */
data class FakeAnswer(val status: Int, val body: String)

/**
 * A local stand-in for the backend's /graphql endpoint, so the real Apollo client (with the app's
 * cache, fetch policies and interceptor) runs against answers the test chooses: a 401 from the
 * token filter, a 500, a GraphQL error, a successful login.
 */
class FakeGraphqlServer(private val answer: (ReceivedRequest) -> FakeAnswer) : AutoCloseable {
    val received = mutableListOf<ReceivedRequest>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/graphql") { exchange ->
            val request = ReceivedRequest(
                body = exchange.requestBody.readBytes().decodeToString(),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
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

    val url = "http://127.0.0.1:${server.address.port}/graphql"

    override fun close() = server.stop(0)

    companion object {
        /** The body Spring sends with response.sendError(401) from the token filter. */
        const val REJECTED_BODY =
            """{"timestamp":"2026-11-17T20:44:28.258Z","status":401,"error":"Unauthorized","path":"/graphql"}"""

        /** A URL nothing listens on: the server is unreachable, as when the phone is offline. */
        fun unreachableUrl(): String {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = s.address.port
            s.stop(0)
            return "http://127.0.0.1:$port/graphql"
        }
    }
}

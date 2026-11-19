package data

import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.exception.ApolloHttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * True when the failure means the server no longer accepts this sign-in, rather than the server
 * being unreachable or failing. Signing someone out because their phone briefly lost signal is
 * worse than letting them retry, so only a genuine rejection counts.
 *
 * The server rejects a token it no longer accepts (password changed since it was issued, account
 * suspended, token signed out) with HTTP 401 before GraphQL runs, which Apollo reports as an
 * [ApolloHttpException] with status code 401. It rarely arrives on its own: with a NetworkFirst or
 * CacheFirst query and nothing cached, Apollo throws an `ApolloCompositeException` ("multiple
 * exceptions happened") whose cause is the cache miss, with the 401 among its
 * [suppressedExceptions]. So the whole chain is searched: causes and suppressed exceptions.
 *
 * A token the server treats as anonymous (expired, or issued under a name since changed) is not
 * a 401: the request runs and a field that needs an account fails with the GraphQL error
 * "Unauthorized" ("Access Denied" from older servers). Those still only have a message, so a
 * failure that is not one of Apollo's own exceptions is matched on these words too.
 */
fun Throwable.isSessionRejected(): Boolean {
    val seen = mutableListOf<Throwable>()
    val queue = ArrayDeque<Throwable>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val e = queue.removeFirst()
        if (seen.any { it === e }) continue
        seen += e
        when {
            e is ApolloHttpException -> if (e.statusCode == 401) return true
            e !is ApolloException && e.message.isRejectionMessage() -> return true
        }
        e.cause?.let { queue.add(it) }
        queue.addAll(e.suppressedExceptions)
    }
    return false
}

private fun String?.isRejectionMessage(): Boolean =
    this != null && (contains("Unauthorized", ignoreCase = true) || contains("Access Denied", ignoreCase = true))

/**
 * Tells the app that the server has rejected the stored sign-in, whichever request found out
 * first: any screen's load, not only the ones that check for it themselves.
 *
 * [rejected] is called by the HTTP interceptor with the token a request carried when the answer
 * was 401. The token is cleared at once, so later requests go out signed out instead of failing
 * too (the server refuses even the Public list to a rejected token). It is ignored when another
 * token, or none, is stored by then: a request that was already on its way when the user signed
 * out, or signed in again, must not undo that.
 */
class SessionMonitor(
    private val currentToken: () -> String?,
    private val clearToken: () -> Unit,
) {
    private val _rejections = MutableStateFlow(0)

    /**
     * How many times a stored sign-in has been rejected and cleared. A count rather than an event,
     * so a rejection that happens before anyone is listening is not lost.
     */
    val rejections: StateFlow<Int> = _rejections.asStateFlow()

    fun rejected(token: String) {
        if (currentToken() != token) return
        clearToken()
        _rejections.update { it + 1 }
    }
}

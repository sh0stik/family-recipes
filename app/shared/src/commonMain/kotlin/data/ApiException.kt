package data

import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Error
import com.apollographql.apollo3.api.Operation

/**
 * A refusal from the server. [code] is the GraphQL error's `extensions.code` (TERMS_REQUIRED,
 * INVALID_URL, ...), null for the few refusals that still arrive without one. [word] is
 * `extensions.word`, set only for BLOCKED_WORD. The [message] is English from the server: it is
 * for logs and for [isSessionRejected] checks, never shown to a user. Screens turn the exception into
 * words with `errorText` (ErrorMessages.kt).
 */
class ApiException(val code: String?, message: String, val word: String? = null) : Exception(message)

fun Error.toApiException(): ApiException = ApiException(
    code = extensions?.get("code") as? String,
    message = message,
    word = extensions?.get("word") as? String,
)

/** The first error of the response as an [ApiException], or null when it has none. */
fun <D : Operation.Data> ApolloResponse<D>.firstApiError(): ApiException? =
    errors?.firstOrNull()?.toApiException()

/** The server code behind this failure, when it was a refusal with one. */
val Throwable.apiCode: String? get() = (this as? ApiException)?.code

/**
 * The response's data, or a failed Result carrying the server's refusal. Used for every call, so
 * no screen has to decide for itself whether "no data" meant "refused", and a refusal never turns
 * into a silent success.
 */
inline fun <D : Operation.Data, T> ApolloResponse<D>.toResult(fallback: String, map: (D) -> T): Result<T> {
    firstApiError()?.let { return Result.failure(it) }
    val d = data ?: return Result.failure(ApiException(null, fallback))
    return Result.success(map(d))
}

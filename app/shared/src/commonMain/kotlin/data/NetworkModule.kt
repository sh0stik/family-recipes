package data

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.cache.normalized.api.MemoryCacheFactory
import com.apollographql.apollo3.cache.normalized.normalizedCache
import com.apollographql.apollo3.cache.normalized.FetchPolicy
import com.apollographql.apollo3.cache.normalized.fetchPolicy

// Backend host/scheme come from the generated `AppConfig` (see the :shared generateAppConfig
// task in shared/build.gradle.kts). Debug builds default to a local backend over http; release
// builds use a build-time-supplied public host over https. Nothing is hardcoded here.

/**
 * Photos uploaded via /api/photos come back as server-relative paths like
 * "/uploads/abc.jpg". Coil can't load those without a host, so prepend it.
 * Full URLs (http/https) are returned unchanged; any other scheme (javascript:, data:, file:…)
 * gives null, so nothing is loaded. See [Links].
 */
fun resolveImageUrl(url: String?): String? = Links.imageUrl(url, AppConfig.BASE_URL)

/**
 * [serverUrl] is the backend's GraphQL endpoint; tests point it at a local stand-in.
 * [sessionMonitor] hears about every request whose sign-in the server rejected.
 */
class NetworkModule(
    private val tokenStorage: TokenStorage,
    serverUrl: String = "${AppConfig.BASE_URL}/graphql",
) {
    val sessionMonitor = SessionMonitor(tokenStorage::getToken, tokenStorage::clearToken)

    val apolloClient: ApolloClient = ApolloClient.Builder()
        .serverUrl(serverUrl)
        .addHttpInterceptor(AuthInterceptor(tokenStorage::getToken, sessionMonitor::rejected))
        .normalizedCache(MemoryCacheFactory(maxSizeBytes = 10 * 1024 * 1024))
        .fetchPolicy(FetchPolicy.CacheFirst)
        .build()

    /** Photo uploads, next to the GraphQL endpoint, with the same sign-in and the same 401 handling. */
    val photoUploader = PhotoUploader(
        endpoint = PhotoUploader.endpointFor(serverUrl),
        currentToken = tokenStorage::getToken,
        onRejected = sessionMonitor::rejected,
    )
}

/**
 * Sends the stored token with every request, and reports a 401 answer to a request that carried
 * one to [onRejected]. Every request passes through here, so a rejected sign-in is noticed
 * whichever screen asked first.
 */
class AuthInterceptor(
    private val currentToken: () -> String?,
    private val onRejected: (token: String) -> Unit,
) : HttpInterceptor {
    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain
    ): HttpResponse {
        val token = currentToken()
        val newRequest = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        val response = chain.proceed(newRequest)
        if (token != null && response.statusCode == 401) onRejected(token)
        return response
    }
}

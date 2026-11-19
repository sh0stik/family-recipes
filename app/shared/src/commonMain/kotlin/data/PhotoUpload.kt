package data

import kotlinx.coroutines.CancellationException

/** What the server answered to an upload: the HTTP status and the body. */
data class UploadReply(val status: Int, val body: String)

/**
 * Sends [jpeg] to [url] as a multipart POST with the picture in the field `file`, with
 * `Authorization: Bearer [token]` when there is one. Throws when the server cannot be reached.
 * Ktor on Android, iOS and desktop; fetch() on the web.
 */
expect suspend fun postPhoto(url: String, token: String?, jpeg: ByteArray): UploadReply

/** The server could not be reached during an upload: no connection, a timeout. */
class UploadNetworkException(cause: Throwable) : Exception("Upload failed: ${cause.message}", cause)

/**
 * Uploads a photo to the backend (`POST /api/photos`) and returns the `/uploads/<name>.jpg` URL it
 * hands back, which goes on the recipe exactly like a typed link.
 *
 * Refusals come back as [ApiException] with the server's code (NOT_AN_IMAGE, UNSUPPORTED_FORMAT,
 * TOO_LARGE, RATE_LIMITED), so the screens show them with `errorText` like every other refusal.
 * A 401 means the server no longer accepts this sign-in: the token is reported to [onRejected]
 * (SessionMonitor.rejected, as the GraphQL interceptor does), which signs the app out with the
 * "session ended" message, and the failure is one [isSessionRejected] recognises.
 */
class PhotoUploader(
    private val endpoint: String,
    private val currentToken: () -> String?,
    private val onRejected: (token: String) -> Unit,
    private val send: suspend (url: String, token: String?, jpeg: ByteArray) -> UploadReply = ::postPhoto,
) {
    suspend fun upload(jpeg: ByteArray): Result<String> {
        val token = currentToken()
        val reply = try {
            send(endpoint, token, jpeg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Result.failure(UploadNetworkException(e))
        }
        if (reply.status == 401) {
            if (token != null) onRejected(token)
            return Result.failure(ApiException(UNAUTHENTICATED, "Unauthorized: the server refused the sign-in (401)"))
        }
        if (reply.status in 200..299) {
            val url = field(reply.body, "url")
            return if (url != null && url.startsWith("/uploads/")) Result.success(url)
            else Result.failure(ApiException(null, "Unexpected upload answer: ${reply.body.take(200)}"))
        }
        return Result.failure(ApiException(field(reply.body, "code"), "Upload refused with HTTP ${reply.status}"))
    }

    companion object {
        const val UNAUTHENTICATED = "UNAUTHENTICATED"

        /** The upload endpoint next to a GraphQL endpoint such as `http://host:8090/graphql`. */
        fun endpointFor(graphqlUrl: String): String = graphqlUrl.removeSuffix("/").removeSuffix("/graphql") + "/api/photos"

        // The server's answers are flat JSON objects of strings ({"url": "..."} or {"code": "..."}).
        private fun field(json: String, name: String): String? =
            Regex("\"" + name + "\"\\s*:\\s*\"([^\"\\\\]*)\"").find(json)?.groupValues?.get(1)
    }
}

/** How the apps prepare a picked picture before uploading it. */
object PhotoPrep {
    /** Longest side of the JPEG the apps upload, as on the server. */
    const val MAX_SIDE = 2048
    const val JPEG_QUALITY = 85

    /** The size a [width]×[height] picture is scaled to: at most [MAX_SIDE] on the long side, never enlarged. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val long = maxOf(width, height)
        if (long <= MAX_SIDE) return width to height
        val scale = MAX_SIDE.toDouble() / long
        return maxOf(1, kotlin.math.round(width * scale).toInt()) to maxOf(1, kotlin.math.round(height * scale).toInt())
    }
}

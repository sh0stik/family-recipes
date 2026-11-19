package data

import errors.errorMessage
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PhotoUploaderTest {
    private fun <T> run(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return outcome!!.getOrThrow()
    }

    private val rejected = mutableListOf<String>()

    private fun uploader(token: String? = "tok", reply: () -> UploadReply) =
        PhotoUploader("http://h/api/photos", { token }, { rejected += it }, send = { _, _, _ -> reply() })

    @Test
    fun theUrlTheServerHandsBackIsTheResult() {
        val result = run { uploader { UploadReply(200, """{"url":"/uploads/0f8c.jpg"}""") }.upload(byteArrayOf(1)) }
        assertEquals("/uploads/0f8c.jpg", result.getOrThrow())
    }

    @Test
    fun theTokenAndPictureAreSentToTheUploadEndpoint() {
        var sent: Triple<String, String?, ByteArray>? = null
        val up = PhotoUploader("http://h:8090/api/photos", { "tok" }, {}, send = { url, token, jpeg ->
            sent = Triple(url, token, jpeg); UploadReply(200, """{"url":"/uploads/a.jpg"}""")
        })
        run { up.upload(byteArrayOf(4, 2)) }
        assertEquals("http://h:8090/api/photos", sent!!.first)
        assertEquals("tok", sent!!.second)
        assertTrue(sent!!.third.contentEquals(byteArrayOf(4, 2)))
    }

    @Test
    fun eachRefusalCarriesTheServersCodeAndItsOwnMessage() {
        val cases = mapOf(
            400 to "NOT_AN_IMAGE" to "error_not_an_image",
            400 to "UNSUPPORTED_FORMAT" to "error_unsupported_format",
            413 to "TOO_LARGE" to "error_too_large",
            429 to "RATE_LIMITED" to "error_rate_limited",
        )
        for ((statusAndCode, key) in cases) {
            val (status, code) = statusAndCode
            val failure = run { uploader { UploadReply(status, """{"code":"$code"}""") }.upload(byteArrayOf(1)) }.exceptionOrNull()!!
            assertEquals(code, failure.apiCode)
            assertEquals(key, errorMessage(failure).text.key)
            assertTrue(!failure.isSessionRejected())
        }
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun a401EndsTheSessionThroughTheSessionMonitor() {
        val failure = run {
            uploader { UploadReply(401, """{"code":"UNAUTHENTICATED"}""") }.upload(byteArrayOf(1))
        }.exceptionOrNull()!!
        assertEquals(listOf("tok"), rejected)
        assertTrue(failure.isSessionRejected())
        assertEquals("session_ended", errorMessage(failure).text.key)
    }

    @Test
    fun a401WithSpringsOwnBodyCountsToo() {
        // The JWT filter answers a suspended account's or a stale token with Spring's error page.
        val failure = run {
            uploader { UploadReply(401, """{"status":401,"error":"Unauthorized","path":"/api/photos"}""") }.upload(byteArrayOf(1))
        }.exceptionOrNull()!!
        assertTrue(failure.isSessionRejected())
        assertEquals(listOf("tok"), rejected)
    }

    @Test
    fun noConnectionIsANetworkFailureNotASignOut() {
        val failure = run { uploader { throw IllegalStateException("connection refused") }.upload(byteArrayOf(1)) }.exceptionOrNull()!!
        assertIs<UploadNetworkException>(failure)
        assertEquals("error_network", errorMessage(failure).text.key)
        assertTrue(!failure.isSessionRejected())
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun aServerErrorIsAGeneralFailure() {
        val failure = run { uploader { UploadReply(500, "<html>oops</html>") }.upload(byteArrayOf(1)) }.exceptionOrNull()!!
        assertEquals(null, failure.apiCode)
        assertEquals("error_generic", errorMessage(failure).text.key)
        assertTrue(!failure.isSessionRejected())
    }

    @Test
    fun theEndpointSitsNextToGraphql() {
        assertEquals("http://localhost:8080/api/photos", PhotoUploader.endpointFor("http://localhost:8080/graphql"))
        assertEquals("https://api.example.com/api/photos", PhotoUploader.endpointFor("https://api.example.com/graphql"))
    }

    @Test
    fun picturesAreScaledToAtMost2048OnTheLongSide() {
        assertEquals(2048 to 1536, PhotoPrep.targetSize(4000, 3000))
        assertEquals(1152 to 2048, PhotoPrep.targetSize(9000, 16000))
        assertEquals(800 to 600, PhotoPrep.targetSize(800, 600))
        assertEquals(2048 to 2048, PhotoPrep.targetSize(2048, 2048))
    }
}

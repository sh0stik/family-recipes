package data

import coil3.Canvas
import coil3.Image
import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The image loader App() installs, fetching a photo from a local server that records the request
 * headers. upload.wikimedia.org answers 403 unless the User-Agent names the app, and before this
 * loader existed no request went out at all: Coil's default loader had no HTTP engine.
 */
class PhotoLoaderTest {
    @Test
    fun photoDownloadsSendTheAppUserAgent() {
        val userAgents = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/photo.jpg") { exchange ->
                synchronized(userAgents) { userAgents += exchange.requestHeaders.getFirst("User-Agent") }
                exchange.responseHeaders.add("Content-Type", "image/jpeg")
                exchange.sendResponseHeaders(200, PHOTO.size.toLong())
                exchange.responseBody.use { it.write(PHOTO) }
            }
            start()
        }
        try {
            val factory = assertNotNull(photoImageLoaderFactory(), "desktop installs its own loader")
            val loader = factory(PlatformContext.INSTANCE)
            var downloaded: ByteArray? = null
            val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                .data("http://127.0.0.1:${server.address.port}/photo.jpg")
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                // Skia's native library, which decodes on desktop, is not on the test classpath.
                // The download is what is under test, so a stand-in decoder takes the bytes.
                .decoderFactory { result, _, _ ->
                    Decoder {
                        downloaded = result.source.source().readByteArray()
                        DecodeResult(image = PlaceholderImage, isSampled = false)
                    }
                }
                .build()
            val result = runBlocking { loader.execute(request) }

            assertTrue(result is SuccessResult, "photo failed: ${(result as? ErrorResult)?.throwable}")
            assertContentEquals(PHOTO, downloaded)
            assertEquals(
                listOf<String?>("MomsRecipes/1.0 (Desktop; com.daria.recipes; +https://recipes.example.com/support.html)"),
                userAgents,
            )
        } finally {
            server.stop(0)
        }
    }

    private object PlaceholderImage : Image {
        override val size = 0L
        override val width = 1
        override val height = 1
        override val shareable = true
        override fun draw(canvas: Canvas) = Unit
    }

    private companion object {
        val PHOTO = "not really a jpeg".encodeToByteArray()
    }
}

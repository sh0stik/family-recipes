package data

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent

/** The desktop target has no packaged version to read, so it sends [PhotoUserAgent.FALLBACK_VERSION]. */
actual fun photoImageLoaderFactory(): ((PlatformContext) -> ImageLoader)? =
    { context ->
        val userAgent = PhotoUserAgent.of(null, "Desktop")
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = {
                    HttpClient(OkHttp) { install(UserAgent) { agent = userAgent } }
                }))
            }
            .build()
    }

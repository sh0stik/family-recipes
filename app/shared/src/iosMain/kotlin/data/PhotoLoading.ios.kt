package data

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.UserAgent
import platform.Foundation.NSBundle

actual fun photoImageLoaderFactory(): ((PlatformContext) -> ImageLoader)? =
    { context ->
        val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        val userAgent = PhotoUserAgent.of(version, "iOS")
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = {
                    HttpClient(Darwin) { install(UserAgent) { agent = userAgent } }
                }))
            }
            .build()
    }

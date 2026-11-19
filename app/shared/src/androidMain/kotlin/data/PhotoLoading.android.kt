package data

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent

actual fun photoImageLoaderFactory(): ((PlatformContext) -> ImageLoader)? =
    { context ->
        val userAgent = PhotoUserAgent.of(appVersion(context), "Android")
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = {
                    HttpClient(OkHttp) { install(UserAgent) { agent = userAgent } }
                }))
            }
            .build()
    }

private fun appVersion(context: Context): String? = try {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: Exception) {
    null
}

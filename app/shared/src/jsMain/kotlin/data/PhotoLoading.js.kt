package data

import coil3.ImageLoader
import coil3.PlatformContext

/** The browser fetches photos with its own User-Agent; Coil's default loader stays. */
actual fun photoImageLoaderFactory(): ((PlatformContext) -> ImageLoader)? = null

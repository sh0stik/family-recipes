package data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.js.Promise

@Composable
actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    return remember {
        {
            // A plain file input, images only. Clicked from the button's own click handler, so
            // the browser treats it as the person's action and opens the file chooser.
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "image/*"
            input.onchange = {
                val file = input.files?.item(0)
                if (file != null) scope.launch { latest.value(uploadJpeg(file)) }
            }
            input.click()
        }
    }
}

/**
 * [file] as an upright JPEG of at most [PhotoPrep.MAX_SIDE] px, drawn on a canvas.
 * createImageBitmap applies the EXIF orientation ("from-image"). A format the browser cannot
 * decode (HEIC outside Safari) is [PickedPhoto.Unreadable].
 */
private suspend fun uploadJpeg(file: File): PickedPhoto = try {
    val options = js("({ imageOrientation: 'from-image' })")
    val bitmap: dynamic = window().createImageBitmap(file, options).unsafeCast<Promise<Any>>().await()
    val (w, h) = PhotoPrep.targetSize((bitmap.width as Number).toInt(), (bitmap.height as Number).toInt())
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = w
    canvas.height = h
    val context: dynamic = canvas.getContext("2d")
    context.fillStyle = "#ffffff"
    context.fillRect(0, 0, w, h)
    context.imageSmoothingQuality = "high"
    context.drawImage(bitmap, 0, 0, w, h)
    bitmap.close()
    val blob = Promise<Blob?> { resolve, _ ->
        canvas.toBlob({ resolve(it) }, "image/jpeg", PhotoPrep.JPEG_QUALITY / 100.0)
    }.await()
    if (blob == null) PickedPhoto.Unreadable
    else {
        val buffer = blob.asDynamic().arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
        PickedPhoto.Ready(Int8Array(buffer).unsafeCast<ByteArray>())
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    PickedPhoto.Unreadable
}

private fun window(): dynamic = kotlinx.browser.window

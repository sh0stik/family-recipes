package data

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.w3c.fetch.RequestInit
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.xhr.FormData

/** fetch() with a FormData body; the browser sets the multipart boundary itself. */
actual suspend fun postPhoto(url: String, token: String?, jpeg: ByteArray): UploadReply {
    val blob = Blob(arrayOf(jpeg.unsafeCast<Int8Array>()), BlobPropertyBag(type = "image/jpeg"))
    val form = FormData()
    form.append("file", blob, "photo.jpg")
    val headers = js("({})")
    if (token != null) headers["Authorization"] = "Bearer $token"
    val response = window.fetch(url, RequestInit(method = "POST", headers = headers, body = form)).await()
    return UploadReply(response.status.toInt(), response.text().await())
}

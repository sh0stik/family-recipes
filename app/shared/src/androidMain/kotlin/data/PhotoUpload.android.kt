package data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

actual suspend fun postPhoto(url: String, token: String?, jpeg: ByteArray): UploadReply {
    val client = HttpClient(OkHttp) { expectSuccess = false }
    try {
        val response = client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append("file", jpeg, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                })
            },
        ) {
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        }
        return UploadReply(response.status.value, response.bodyAsText())
    } finally {
        client.close()
    }
}

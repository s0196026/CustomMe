package com.example.customme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class CloudinaryException(message: String) : Exception(message)

/**
 * Загрузка изображений в Cloudinary через unsigned upload preset.
 * api_secret в приложении не используется и храниться не должен.
 */
object Cloudinary {

    private const val CLOUD_NAME = "dccxzaqm"
    private const val UPLOAD_PRESET = "customme_unsigned_storage"
    private const val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
    private const val DELIVERY_MARKER = "/image/upload/"

    /** Загружает JPEG и возвращает постоянную https-ссылку (secure_url). */
    suspend fun upload(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val boundary = "----CustomMe${UUID.randomUUID()}"
        val connection = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            connection.outputStream.buffered().use { out ->
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n" +
                        "$UPLOAD_PRESET\r\n").toByteArray()
                )
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"\r\n" +
                        "Content-Type: image/jpeg\r\n\r\n").toByteArray()
                )
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrNull()
                throw CloudinaryException(message ?: "ошибка сервера ($code)")
            }

            JSONObject(body).getString("secure_url")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Облегчённая версия картинки для показа: авто-формат, авто-качество, ширина не больше [width].
     * В Firestore всегда хранится исходная ссылка; ссылки не из Cloudinary возвращаются без изменений.
     */
    fun optimized(url: String, width: Int): String {
        if (!url.contains("res.cloudinary.com") || !url.contains(DELIVERY_MARKER)) return url
        return url.replaceFirst(DELIVERY_MARKER, "${DELIVERY_MARKER}f_auto,q_auto,c_limit,w_$width/")
    }
}

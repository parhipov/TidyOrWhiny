package com.tidyorwhiny.app.ai

import android.graphics.Bitmap
import android.util.Base64
import com.tidyorwhiny.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Qwen on the HAL gateway, as Photo3D's core/llm.py talks to it: an OpenAI-style
 * /chat/completions, one user turn, thinking off, retried on what the gateway fails quickly on.
 * The gateway, model and key come from qwen.json through BuildConfig; only the family build has them.
 */
class QwenClient(
    private val baseUrl: String = BuildConfig.QWEN_BASE_URL,
    val model: String = BuildConfig.QWEN_MODEL,
    private val apiKey: String = BuildConfig.QWEN_API_KEY,
) {
    val enabled get() = BuildConfig.FAMILY && apiKey.isNotBlank()

    /** One user turn of content parts, the reply text. */
    suspend fun chat(content: JSONArray): String = withContext(Dispatchers.IO) {
        if (!enabled) throw NoKeyException()
        // chat_template_kwargs is the only enable_thinking the gateway obeys; thinking costs
        // seconds and tokens for the same verdict.
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("temperature", 0.3)
            .put("stream", false)
            .put("enable_thinking", false)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            .toString()
            .toByteArray()
        var err = "no answer"
        for (k in 0 until ATTEMPTS) {
            try {
                val conn = (URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    conn.outputStream.use { it.write(body) }
                    val code = conn.responseCode
                    val text = (if (code < 400) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    when {
                        code in RETRY -> err = "HTTP $code: ${text.oneLine()}"
                        code >= 400 -> throw QwenException("HTTP $code: ${text.oneLine()}")
                        else -> return@withContext JSONObject(text).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").optString("content")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                err = e.message ?: e.javaClass.simpleName
            }
            if (k < ATTEMPTS - 1) delay(1500L * (k + 1))
        }
        throw QwenException(err)
    }

    companion object {
        const val ATTEMPTS = 3
        const val TIMEOUT_MS = 180_000      // one ask; the gateway stalls sometimes, a retry usually answers
        val RETRY = setOf(429, 500, 502, 503, 504)
        const val SEND_SIDE = 1280          // long side of a photo sent; plenty to tell a mess from a tidy room
        const val JPEG_QUALITY = 85

        fun text(t: String): JSONObject = JSONObject().put("type", "text").put("text", t)

        fun image(bitmap: Bitmap): JSONObject {
            val s = SEND_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val sent = if (s < 1f) Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true) else bitmap
            val buf = ByteArrayOutputStream()
            sent.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buf)
            val url = "data:image/jpeg;base64," + Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)
            return JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url))
        }

        private fun String.oneLine() = split(Regex("\\s+")).joinToString(" ").take(200)
    }
}

open class QwenException(message: String) : Exception(message)
class NoKeyException : QwenException("no Qwen in this build (no qwen.json)")

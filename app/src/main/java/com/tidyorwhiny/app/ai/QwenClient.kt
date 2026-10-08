package com.tidyorwhiny.app.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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
 * Qwen on the HAL gateway or an Ollama box, as Photo3D's core/llm.py talks to it: an OpenAI-style
 * /chat/completions, one user turn, thinking off, retried on what the gateway fails quickly on.
 * The gateway, model and key come from qwen.json through BuildConfig; only the family build has them.
 */
class QwenClient(
    private val baseUrl: String = BuildConfig.QWEN_BASE_URL,
    val model: String = BuildConfig.QWEN_MODEL,
    private val apiKey: String = BuildConfig.QWEN_API_KEY,
) {
    val enabled get() = BuildConfig.FAMILY && apiKey.isNotBlank()

    /** One user turn of content parts, the reply text; [onSlow] when the first attempt failed and a second one starts. */
    suspend fun chat(content: JSONArray, onSlow: () -> Unit = {}): String = withContext(Dispatchers.IO) {
        if (!enabled) throw NoKeyException()
        // Thinking costs seconds and tokens for the same verdict. The vLLM gateway obeys only
        // chat_template_kwargs; Ollama ignores that and obeys only reasoning_effort "none".
        val body = body(content).put("temperature", 0.3)
        var err = "no answer"
        for (k in 0 until ATTEMPTS) {
            if (k > 0) {
                onSlow()
                delay(1500L)
            }
            try {
                val (code, text) = post(body, TIMEOUT_MS)
                when {
                    code in RETRY -> err = "HTTP $code: ${text.oneLine()}"
                    code >= 400 -> throw QwenException("HTTP $code: ${text.oneLine()}")
                    else -> return@withContext JSONObject(text).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").optString("content")
                }
            } catch (e: IOException) {
                err = e.message ?: e.javaClass.simpleName
            }
        }
        throw QwenException(err)
    }

    /**
     * A "hello" at app start, answer unread: an Ollama box loads the model on the first ask,
     * about 30 s, and better now than while a child waits for a verdict. Failures are ignored.
     */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        val t = System.currentTimeMillis()
        val result = runCatching {
            post(body(JSONArray().put(text("Привет"))).put("max_tokens", 1), WARM_UP_TIMEOUT_MS).first
        }
        Log.i(TAG, "warm-up: ${result.getOrElse { it.message }} in ${System.currentTimeMillis() - t} ms")
    }

    /** The ask, thinking off. */
    private fun body(content: JSONArray) = JSONObject()
        .put("model", model)
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        .put("stream", false)
        .put("enable_thinking", false)
        .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
        .put("reasoning_effort", "none")

    /** One POST to /chat/completions: the HTTP code and the body, error body included. */
    private fun post(body: JSONObject, readTimeoutMs: Int): Pair<Int, String> {
        val conn = (URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val ATTEMPTS = 2              // the gateway stalls sometimes, a second ask usually answers
        const val TIMEOUT_MS = 30_000       // one ask; a minute in all, nobody waits longer for a verdict
        const val WARM_UP_TIMEOUT_MS = 120_000  // nobody waits on it; long enough for a cold model to load
        private const val TAG = "Inspector"
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

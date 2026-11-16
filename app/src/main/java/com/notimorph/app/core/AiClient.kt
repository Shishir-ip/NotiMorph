package com.notimorph.app.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Single shared HTTP engine for all providers.
 * - HTTP/2 + keep-alive pool so repeat calls skip DNS + TLS (~200-400 ms saved)
 * - Tight timeouts, small max-output, low temperature -> lowest possible latency
 * - Gemini gets a derived client with a longer ceiling: its "smarter" models
 *   can queue on the free tier; the default lite model answers in ~1 s anyway.
 */
object AiClient {

    private const val TAG = "NotiMorph"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    /** Same pool, but tolerant of Gemini's occasional free-tier queueing. */
    private val geminiClient: OkHttpClient = client.newBuilder()
        .readTimeout(28, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** HTTP error carrying the status code, so retries can be selective. */
    private class HttpError(val code: Int, message: String) : IOException(message)

    // --- warm-up -------------------------------------------------------------

    private val warmLock = Any()
    private var warmSig: String? = null
    private var warmAt: Long = 0L

    /**
     * Fire-and-forget request that warms DNS + TLS for the active provider.
     * Throttled: runs when the config changes or every ~3.5 min, so the pooled
     * connection never goes cold while the app is alive (pool TTL is 5 min).
     */
    fun warmup(s: Settings) {
        if (!s.isConfigured()) return
        val url = when (s.provider) {
            Provider.GEMINI -> "https://generativelanguage.googleapis.com"
            Provider.GROQ -> "https://api.groq.com"
            Provider.CUSTOM -> s.customBaseUrl.trim().trimEnd('/')
        }
        if (url.isBlank() || !url.startsWith("http")) return
        val sig = "${s.provider.id}|${s.currentModel}|${s.currentKey.hashCode()}|$url"
        synchronized(warmLock) {
            if (sig == warmSig && SystemClockElapsed() - warmAt < 210_000L) return
            warmSig = sig
            warmAt = SystemClockElapsed()
        }
        runCatching {
            client.newCall(Request.Builder().url(url).head().build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        }
    }

    private fun SystemClockElapsed(): Long = android.os.SystemClock.elapsedRealtime()

    // --- public entry points --------------------------------------------------

    /**
     * Transform one notification. Returns the rewritten text.
     * [customPrompt] overrides the global default prompt for this app.
     */
    suspend fun transform(
        s: Settings,
        appName: String,
        title: String,
        body: String,
        customPrompt: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        val sys = PromptPresets.systemFor(customPrompt ?: s.defaultPrompt)
        val user = buildString {
            append("App: ").append(appName).append('\n')
            if (title.isNotBlank()) append("Title: ").append(title).append('\n')
            append("Message: ").append(body)
        }
        val started = SystemClockElapsed()
        try {
            val text = callWithRetry(s, sys, user)
            val cleaned = clean(text, s.maxLen)
            Log.i(TAG, "restyle ok in ${SystemClockElapsed() - started}ms via ${s.provider.id}/${s.currentModel}")
            if (cleaned.isBlank()) Result.failure(IOException("The model returned an empty message."))
            else Result.success(cleaned)
        } catch (e: Exception) {
            Log.i(TAG, "restyle FAILED after ${SystemClockElapsed() - started}ms: ${e.message?.take(140)}")
            Result.failure(e)
        }
    }

    /** One transparent retry on transient failures (network blip, 429, 5xx). */
    private suspend fun callWithRetry(s: Settings, sys: String, user: String): String {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return when (s.provider) {
                    Provider.GEMINI -> callGemini(s.geminiModel, s.geminiKey, sys, user)
                    Provider.GROQ -> callOpenAI(
                        "https://api.groq.com/openai/v1/chat/completions",
                        s.groqModel, s.groqKey, sys, user, lowReasoning = true,
                    )
                    Provider.CUSTOM -> {
                        val base = s.customBaseUrl.trim().trimEnd('/')
                        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                        callOpenAI(url, s.customModel.trim(), s.customKey, sys, user, lowReasoning = false)
                    }
                }
            } catch (e: IOException) {
                val transient = e !is HttpError || e.code == 429 || e.code >= 500
                if (!transient || attempt >= 2) throw e
                Log.i(TAG, "transient failure (attempt $attempt), retrying: ${e.message?.take(120)}")
                delay(350)
            }
        }
    }

    /** Quick self-test used by the Settings screen. */
    suspend fun test(s: Settings): Result<String> = transform(
        s,
        appName = "NotiMorph Test",
        title = "Rahim",
        body = "আপনার অর্ডার #4521 ডেলিভারির জন্য প্রস্তুত! OTP 8812 ব্যবহার করুন। Track: https://x.co/4521 🎉",
        customPrompt = null,
    )

    // --- Google Gemini --------------------------------------------------------

    /**
     * Thinking behaviour varies per model and the API rejects wrong values with
     * a bare 400, so: "lite" models get no thinking parameter at all (they are
     * non-thinking by default and answer in ~1 s); everything else tries
     * thinkingBudget=1 to stop 3.x models from burning 20-30 s on hidden
     * reasoning; any 400 falls back to the plain request.
     */
    private fun callGemini(model: String, key: String, sys: String, user: String): String {
        val wantsLowThinking = !model.contains("lite")
        return try {
            geminiOnce(model, key, sys, user, lowThinking = wantsLowThinking)
        } catch (e: HttpError) {
            if (e.code == 400 && wantsLowThinking) geminiOnce(model, key, sys, user, lowThinking = false)
            else throw e
        }
    }

    private fun geminiOnce(model: String, key: String, sys: String, user: String, lowThinking: Boolean): String {
        if (key.isBlank()) throw IOException("Add your Gemini API key in Settings → API key.")
        val generationConfig = JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", 320)
        if (lowThinking) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 1))
        }

        val payload = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user))),
                ),
            )
            .put("generationConfig", generationConfig)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        geminiClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpError(resp.code, errorText(resp.code, text))
            val root = JSONObject(text)
            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }
                ?.let { throw IOException("Blocked by safety filter: $it") }
            val parts = root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IOException("Unexpected Gemini response: ${text.take(160)}")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            return sb.toString()
        }
    }

    // --- OpenAI-compatible (Groq + custom endpoints) --------------------------

    private fun callOpenAI(
        url: String,
        model: String,
        key: String,
        sys: String,
        user: String,
        lowReasoning: Boolean,
    ): String {
        if (model.isBlank()) throw IOException("Set a model name for this provider in Settings.")
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", sys))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
            .put("temperature", 0.2)
            .put("max_tokens", 320)
            .put("stream", false)
        // Groq's GPT-OSS models reason by default — "low" cuts seconds off the
        // round-trip. Only sent to Groq; custom endpoints may reject unknown fields.
        if (lowReasoning) payload.put("reasoning_effort", "low")

        val builder = Request.Builder().url(url).post(payload.toString().toRequestBody(JSON))
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        builder.header("Content-Type", "application/json")

        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpError(resp.code, errorText(resp.code, text))
            val root = JSONObject(text)
            val content = root.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
                ?: throw IOException("Unexpected response: ${text.take(160)}")
            return content
        }
    }

    // --- helpers ---------------------------------------------------------------
    private fun errorText(code: Int, body: String): String {
        val msg = try {
            val err = JSONObject(body).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        return "HTTP $code${msg?.let { ": $it" } ?: ""}".take(220)
    }

    /** Strip model decorations (quotes, code fences) and apply the watch length limit. */
    fun clean(text: String, maxLen: Int): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '“' && t.last() == '”'))) {
            t = t.substring(1, t.length - 1).trim()
        }
        if (maxLen in 1 until t.length) {
            val cut = t.lastIndexOf(' ', maxLen).let { if (it > maxLen / 2) it else maxLen }
            t = t.take(cut).trimEnd().trimEnd(',', ';', '-') + "…"
        }
        return t
    }
}

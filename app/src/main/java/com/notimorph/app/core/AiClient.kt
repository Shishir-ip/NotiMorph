package com.notimorph.app.core

import kotlinx.coroutines.Dispatchers
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
 */
object AiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // One shared engine for warm-up AND real calls so the pre-warmed
    // connection is actually reused. Tight timeouts -> fail fast, the
    // original notification is still on the watch anyway.
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    /** Cheap fire-and-forget request to warm DNS + TLS for the active provider. */
    fun warmup(s: Settings) {
        if (!s.isConfigured()) return
        val url = when (s.provider) {
            Provider.GEMINI -> "https://generativelanguage.googleapis.com"
            Provider.GROQ -> "https://api.groq.com"
            Provider.CUSTOM -> s.customBaseUrl.trim().trimEnd('/')
        }
        if (url.isBlank() || !url.startsWith("http")) return
        client.newCall(Request.Builder().url(url).head().build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

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
        try {
            val text = when (s.provider) {
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
            val cleaned = clean(text, s.maxLen)
            if (cleaned.isBlank()) Result.failure(IOException("The model returned an empty message."))
            else Result.success(cleaned)
        } catch (e: Exception) {
            Result.failure(e)
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

    // --- Google Gemini ------------------------------------------------------

    /** Thrown when the endpoint rejects thinkingConfig; we retry without it. */
    private class ThinkingUnsupportedException : IOException("thinkingConfig rejected")

    private fun callGemini(model: String, key: String, sys: String, user: String): String {
        return try {
            geminiOnce(model, key, sys, user, disableThinking = true)
        } catch (e: ThinkingUnsupportedException) {
            geminiOnce(model, key, sys, user, disableThinking = false)
        }
    }

    private fun geminiOnce(model: String, key: String, sys: String, user: String, disableThinking: Boolean): String {
        if (key.isBlank()) throw IOException("Add your Gemini API key in Settings → API key.")
        // thinkingBudget 0 = no hidden reasoning: the single biggest latency win
        // on Flash-class models (rewriting a notification needs zero thinking).
        val generationConfig = JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", 320)
        if (disableThinking) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
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

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 400 && text.contains("thinkingConfig")) {
                    throw ThinkingUnsupportedException()
                }
                throw IOException(errorText(resp.code, text))
            }
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

    // --- OpenAI-compatible (Groq + custom endpoints) ------------------------
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
            if (!resp.isSuccessful) throw IOException(errorText(resp.code, text))
            val root = JSONObject(text)
            val content = root.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
                ?: throw IOException("Unexpected response: ${text.take(160)}")
            return content
        }
    }

    // --- helpers ------------------------------------------------------------
    private fun errorText(code: Int, body: String): String {
        val msg = try {
            val err = JSONObject(body).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
                ?: JSONObject(body).optString("message")?.takeIf { it.isNotBlank() }
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

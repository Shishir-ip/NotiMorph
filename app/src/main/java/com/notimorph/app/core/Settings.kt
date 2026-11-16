package com.notimorph.app.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Provider(
    val id: String,
    val label: String,
    val hint: String,
    val keyUrl: String,
    val keyUrlLabel: String,
) {
    GEMINI(
        "gemini", "Google Gemini",
        "Free tier available at aistudio.google.com",
        "https://aistudio.google.com/apikey", "Get a free key at aistudio.google.com",
    ),
    GROQ(
        "groq", "Groq (LPU-fast)",
        "Free tier available at console.groq.com",
        "https://console.groq.com/keys", "Get a free key at console.groq.com",
    ),
    CUSTOM(
        "custom", "Custom (OpenAI-compatible)",
        "Any OpenAI-compatible endpoint: OpenRouter, Ollama, LM Studio, vLLM…",
        "https://openrouter.ai/keys", "OpenRouter is a popular pay-as-you-go hub",
    );

    companion object {
        fun from(id: String): Provider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

object Models {
    val GEMINI = listOf(
        "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite · fastest (default)",
        "gemini-3.6-flash" to "Gemini 3.6 Flash · smarter, thinks first (slower)",
    )
    val GROQ = listOf(
        "openai/gpt-oss-120b" to "GPT-OSS 120B (default)",
        "qwen/qwen3.6-27b" to "Qwen3 27B",
    )
}

enum class ThemeMode { AUTO, LIGHT, DARK }

/** One immutable snapshot of every user preference. */
data class Settings(
    val provider: Provider = Provider.GEMINI,
    val geminiModel: String = "gemini-3.5-flash-lite",
    val groqModel: String = "openai/gpt-oss-120b",
    val customModel: String = "",
    val customBaseUrl: String = "",
    val geminiKey: String = "",
    val groqKey: String = "",
    val customKey: String = "",
    val defaultPrompt: String = PromptPresets.DEFAULT_PROMPT,
    val enabledApps: Set<String> = emptySet(),
    val appPrompts: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val dynamicColor: Boolean = true,
    val hideOriginal: Boolean = false,
    val maxLen: Int = 0, // 0 = off
) {
    val currentModel: String
        get() = when (provider) {
            Provider.GEMINI -> geminiModel
            Provider.GROQ -> groqModel
            Provider.CUSTOM -> customModel
        }

    val currentKey: String
        get() = when (provider) {
            Provider.GEMINI -> geminiKey
            Provider.GROQ -> groqKey
            Provider.CUSTOM -> customKey
        }

    fun isConfigured(): Boolean = when (provider) {
        Provider.GEMINI -> geminiKey.isNotBlank()
        Provider.GROQ -> groqKey.isNotBlank()
        Provider.CUSTOM -> customBaseUrl.isNotBlank() && customModel.isNotBlank()
    }

    fun promptFor(pkg: String): String = appPrompts[pkg] ?: defaultPrompt
}

/** Result of one transformation, kept for the History screen. */
data class HistoryEntry(
    val app: String,
    val title: String,
    val before: String,
    val after: String,
    val time: Long,
    val failed: Boolean = false,
)

class Prefs(private val context: Context) {

    private object K {
        val provider = stringPreferencesKey("provider")
        val geminiModel = stringPreferencesKey("gemini_model")
        val groqModel = stringPreferencesKey("groq_model")
        val customModel = stringPreferencesKey("custom_model")
        val customBaseUrl = stringPreferencesKey("custom_base_url")
        val geminiKey = stringPreferencesKey("gemini_key")
        val groqKey = stringPreferencesKey("groq_key")
        val customKey = stringPreferencesKey("custom_key")
        val defaultPrompt = stringPreferencesKey("default_prompt")
        val enabledApps = stringSetPreferencesKey("enabled_apps")
        val appPrompts = stringPreferencesKey("app_prompts")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val hideOriginal = booleanPreferencesKey("hide_original")
        val maxLen = intPreferencesKey("max_len")
        val history = stringPreferencesKey("history")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state

    init {
        scope.launch {
            context.dataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { p -> _state.value = p.toSettings() }
        }
    }

    private fun Preferences.toSettings(): Settings {
        val promptsJson = this[K.appPrompts] ?: "{}"
        val prompts = try {
            val o = JSONObject(promptsJson)
            buildMap { o.keys().forEach { k -> put(k, o.optString(k)) } }
        } catch (_: Exception) { emptyMap<String, String>() }

        val historyJson = this[K.history] ?: "[]"
        val history = try {
            val arr = JSONArray(historyJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                HistoryEntry(
                    app = o.optString("app"), title = o.optString("title"),
                    before = o.optString("before"), after = o.optString("after"),
                    time = o.optLong("time"), failed = o.optBoolean("failed"),
                )
            }
        } catch (_: Exception) { emptyList<HistoryEntry>() }

        return Settings(
            provider = Provider.from(this[K.provider] ?: Provider.GEMINI.id),
            geminiModel = this[K.geminiModel] ?: "gemini-3.5-flash-lite",
            groqModel = this[K.groqModel] ?: "openai/gpt-oss-120b",
            customModel = this[K.customModel] ?: "",
            customBaseUrl = this[K.customBaseUrl] ?: "",
            geminiKey = this[K.geminiKey] ?: "",
            groqKey = this[K.groqKey] ?: "",
            customKey = this[K.customKey] ?: "",
            defaultPrompt = this[K.defaultPrompt] ?: PromptPresets.DEFAULT_PROMPT,
            enabledApps = this[K.enabledApps] ?: emptySet(),
            appPrompts = prompts,
            themeMode = when (this[K.themeMode]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.AUTO
            },
            dynamicColor = this[K.dynamicColor] ?: true,
            hideOriginal = this[K.hideOriginal] ?: false,
            maxLen = this[K.maxLen] ?: 0,
        ).also { s -> _history = history }
    }

    @Volatile
    private var _history: List<HistoryEntry> = emptyList()
    val history: List<HistoryEntry> get() = _history

    private suspend fun set(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    // --- simple setters -----------------------------------------------------
    suspend fun setProvider(v: Provider) = set { it[K.provider] = v.id }
    suspend fun setGeminiModel(v: String) = set { it[K.geminiModel] = v }
    suspend fun setGroqModel(v: String) = set { it[K.groqModel] = v }
    suspend fun setCustomModel(v: String) = set { it[K.customModel] = v }
    suspend fun setCustomBaseUrl(v: String) = set { it[K.customBaseUrl] = v }
    suspend fun setGeminiKey(v: String) = set { it[K.geminiKey] = v }
    suspend fun setGroqKey(v: String) = set { it[K.groqKey] = v }
    suspend fun setCustomKey(v: String) = set { it[K.customKey] = v }
    suspend fun setDefaultPrompt(v: String) =
        set { it[K.defaultPrompt] = v.ifBlank { PromptPresets.DEFAULT_PROMPT } }
    suspend fun setThemeMode(v: ThemeMode) = set { it[K.themeMode] = v.name.lowercase() }
    suspend fun setDynamicColor(v: Boolean) = set { it[K.dynamicColor] = v }
    suspend fun setHideOriginal(v: Boolean) = set { it[K.hideOriginal] = v }
    suspend fun setMaxLen(v: Int) = set { it[K.maxLen] = v }

    // --- apps ---------------------------------------------------------------
    suspend fun setAppEnabled(pkg: String, enabled: Boolean) {
        val cur = _state.value.enabledApps
        set { it[K.enabledApps] = if (enabled) cur + pkg else cur - pkg }
    }

    suspend fun setAppPrompt(pkg: String, prompt: String) {
        val cur = _state.value.appPrompts.toMutableMap()
        if (prompt.isBlank()) cur.remove(pkg) else cur[pkg] = prompt.trim()
        set { it[K.appPrompts] = JSONObject(cur).toString() }
    }

    // --- history ------------------------------------------------------------
    fun appendHistory(entry: HistoryEntry) {
        synchronized(this) {
            _history = (listOf(entry) + _history).take(50)
            val arr = JSONArray()
            _history.forEach { h ->
                arr.put(
                    JSONObject()
                        .put("app", h.app).put("title", h.title)
                        .put("before", h.before).put("after", h.after)
                        .put("time", h.time).put("failed", h.failed)
                )
            }
            val json = arr.toString()
            scope.launch { set { it[K.history] = json } }
        }
    }

    fun clearHistory() {
        synchronized(this) { _history = emptyList() }
        scope.launch { set { it[K.history] = "[]" } }
    }
}

private val Context.dataStore by preferencesDataStore(name = "notimorph")

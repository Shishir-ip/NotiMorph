package com.notimorph.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notimorph.app.BuildConfig
import com.notimorph.app.NotiMorphApp
import com.notimorph.app.core.AiClient
import com.notimorph.app.core.Models
import com.notimorph.app.core.PromptPresets
import com.notimorph.app.core.Provider
import com.notimorph.app.core.ThemeMode
import com.notimorph.app.core.Updater
import com.notimorph.app.ui.common.SectionHeader
import kotlinx.coroutines.launch
import java.io.File

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ---------------------------------------------------------------------------
// Update dialog state machine
// ---------------------------------------------------------------------------
private sealed interface UpdateUi {
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val release: Updater.Release) : UpdateUi
    data class Downloading(val release: Updater.Release, val pct: Int) : UpdateUi
    data class Ready(val release: Updater.Release, val file: File) : UpdateUi
    data class Failed(val msg: String) : UpdateUi
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as NotiMorphApp
    val prefs = app.prefs
    val s by prefs.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showPromptEditor by remember { mutableStateOf(false) }
    var updateUi by remember { mutableStateOf<UpdateUi?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // ================= AI PROVIDER =======================================
        SectionHeader("AI PROVIDER")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Provider.entries.forEach { p ->
                    ProviderCard(
                        provider = p,
                        selected = s.provider == p,
                        onSelect = { scope.launch { prefs.setProvider(p) } },
                    )
                }

                AnimatedVisibility(visible = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (s.provider) {
                            Provider.GEMINI -> {
                                KeyField(
                                    value = s.geminiKey,
                                    onChange = { scope.launch { prefs.setGeminiKey(it) } },
                                    label = "Gemini API key",
                                )
                                ModelDropdown(
                                    options = Models.GEMINI,
                                    selected = s.geminiModel,
                                    onSelect = { scope.launch { prefs.setGeminiModel(it) } },
                                )
                            }
                            Provider.GROQ -> {
                                KeyField(
                                    value = s.groqKey,
                                    onChange = { scope.launch { prefs.setGroqKey(it) } },
                                    label = "Groq API key",
                                )
                                ModelDropdown(
                                    options = Models.GROQ,
                                    selected = s.groqModel,
                                    onSelect = { scope.launch { prefs.setGroqModel(it) } },
                                )
                            }
                            Provider.CUSTOM -> {
                                OutlinedTextField(
                                    value = s.customBaseUrl,
                                    onValueChange = { scope.launch { prefs.setCustomBaseUrl(it) } },
                                    label = { Text("Base URL (OpenAI-compatible)") },
                                    placeholder = { Text("https://openrouter.ai/api/v1") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                KeyField(
                                    value = s.customKey,
                                    onChange = { scope.launch { prefs.setCustomKey(it) } },
                                    label = "API key (empty for local servers)",
                                )
                                OutlinedTextField(
                                    value = s.customModel,
                                    onValueChange = { scope.launch { prefs.setCustomModel(it) } },
                                    label = { Text("Model name") },
                                    placeholder = { Text("meta-llama/llama-3.3-70b-instruct") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        LinkChip(
                            text = Provider.from(s.provider.id).keyUrlLabel,
                            onClick = { openUrl(context, s.provider.keyUrl) },
                        )

                        TestConnectionRow(configured = s.isConfigured())
                    }
                }
            }
        }

        // ================= DEFAULT PROMPT ====================================
        SectionHeader("DEFAULT PROMPT")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clickable { showPromptEditor = true },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoFixHigh, null,
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Default prompt",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Edit, "Edit default prompt",
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    s.defaultPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Used for every enabled app that has NO custom prompt of its own. Tap to edit or pick a preset.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ================= APPEARANCE ========================================
        SectionHeader("APPEARANCE")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { scope.launch { prefs.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            icon = {
                                when (mode) {
                                    ThemeMode.AUTO -> Icon(Icons.Rounded.BrightnessAuto, null, Modifier.size(16.dp))
                                    ThemeMode.LIGHT -> Icon(Icons.Rounded.LightMode, null, Modifier.size(16.dp))
                                    ThemeMode.DARK -> Icon(Icons.Rounded.DarkMode, null, Modifier.size(16.dp))
                                }
                            },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Material You colors", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Follow your wallpaper palette (Android 12+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.dynamicColor,
                        onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                    )
                }
            }
        }

        // ================= NOTIFICATIONS ======================================
        SectionHeader("NOTIFICATIONS")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide the original notification", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Cancel the source notification after the restyled one is posted — avoids duplicates on your watch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.hideOriginal,
                        onCheckedChange = { scope.launch { prefs.setHideOriginal(it) } },
                    )
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Watch text limit", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Hard-trim rewritten text so tiny screens never choke.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 100, 200, 300).forEach { len ->
                            FilterChip(
                                selected = s.maxLen == len,
                                onClick = { scope.launch { prefs.setMaxLen(len) } },
                                label = { Text(if (len == 0) "Off" else "$len") },
                            )
                        }
                    }
                }
            }
        }

        // ================= UPDATES ============================================
        SectionHeader("UPDATES")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Current version", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "v${BuildConfig.VERSION_NAME} · github.com/${Updater.REPO_OWNER}/${Updater.REPO_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                updateUi = UpdateUi.Checking
                                val release = Updater.latest()
                                updateUi = release.fold(
                                    onSuccess = { r ->
                                        if (Updater.isNewer(r.version, BuildConfig.VERSION_NAME)) UpdateUi.Available(r)
                                        else UpdateUi.UpToDate
                                    },
                                    onFailure = { UpdateUi.Failed(it.message ?: "Could not reach GitHub.") },
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check")
                    }
                }
            }
        }

        // ================= ABOUT ==============================================
        SectionHeader("ABOUT")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                AboutRow(Icons.Rounded.Info, "About NotiMorph", "Version, privacy, credits") {
                    nav.navigate("about")
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                AboutRow(Icons.Rounded.Code, "Source code on GitHub", Updater.REPO_URL) {
                    openUrl(context, Updater.REPO_URL)
                }
            }
        }
    }

    if (showPromptEditor) {
        PromptEditorDialog(
            initial = s.defaultPrompt,
            onSave = { scope.launch { prefs.setDefaultPrompt(it) } },
            onDismiss = { showPromptEditor = false },
        )
    }

    updateUi?.let { ui ->
        UpdateDialog(
            ui = ui,
            onDismiss = { updateUi = null },
            onDownload = { release ->
                scope.launch {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val dest = File(dir, "NotiMorph-${release.version}.apk")
                    updateUi = UpdateUi.Downloading(release, 0)
                    Updater.download(release.apkUrl, dest) { pct, _, _ ->
                        if (updateUi is UpdateUi.Downloading) updateUi = UpdateUi.Downloading(release, pct)
                    }.fold(
                        onSuccess = { updateUi = UpdateUi.Ready(release, it) },
                        onFailure = { updateUi = UpdateUi.Failed(it.message ?: "Download failed.") },
                    )
                }
            },
            onInstall = { file -> installApk(context, file) },
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------
@Composable
private fun ProviderCard(provider: Provider, selected: Boolean, onSelect: () -> Unit) {
    val icon = when (provider) {
        Provider.GEMINI -> Icons.Rounded.AutoAwesome
        Provider.GROQ -> Icons.Rounded.Bolt
        Provider.CUSTOM -> Icons.Rounded.Extension
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            Modifier.size(26.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(provider.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                provider.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun KeyField(value: String, onChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("Paste your key…") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Text(
                if (visible) "Hide" else "Show",
                modifier = Modifier.clickable { visible = !visible }.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            leadingIcon = { Icon(Icons.Rounded.SmartToy, null, Modifier.size(18.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false },
                    trailingIcon = if (id == selected) {
                        { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun LinkChip(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Rounded.OpenInNew, null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private sealed interface TestState {
    data object Idle : TestState
    data object Loading : TestState
    data class Ok(val msg: String) : TestState
    data class Err(val msg: String) : TestState
}

@Composable
private fun TestConnectionRow(configured: Boolean) {
    var state by remember { mutableStateOf<TestState>(TestState.Idle) }
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as NotiMorphApp
    val s by app.prefs.state.collectAsState()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            enabled = configured && state != TestState.Loading,
            onClick = {
                state = TestState.Loading
                scope.launch {
                    AiClient.test(s).fold(
                        onSuccess = { state = TestState.Ok(it) },
                        onFailure = { state = TestState.Err(it.message ?: "Failed") },
                    )
                }
            },
        ) {
            if (state == TestState.Loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Test connection")
        }

        AnimatedVisibility(visible = state is TestState.Ok || state is TestState.Err) {
            when (val st = state) {
                is TestState.Ok -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        st.msg,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        modifier = Modifier.width(170.dp),
                    )
                }
                is TestState.Err -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        st.msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        modifier = Modifier.width(170.dp),
                    )
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptEditorDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(
        onDismissRequest = onDismiss,
        title = "Default prompt",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Instructions sent to the AI for every enabled app without its own prompt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PromptPresets.list.forEach { preset ->
                    FilterChip(
                        selected = text == preset.prompt,
                        onClick = { text = preset.prompt },
                        label = { Text(preset.title) },
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(170.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { text = PromptPresets.DEFAULT_PROMPT }) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(text); onDismiss() }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Dialog(onDismissRequest: () -> Unit, title: String, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { content() },
        confirmButton = {},
    )
}

@Composable
private fun AboutRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Update dialog
// ---------------------------------------------------------------------------
@Composable
private fun UpdateDialog(
    ui: UpdateUi,
    onDismiss: () -> Unit,
    onDownload: (Updater.Release) -> Unit,
    onInstall: (File) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (ui !is UpdateUi.Downloading && ui !is UpdateUi.Ready) onDismiss() },
        title = {
            Text(
                when (ui) {
                    is UpdateUi.Checking -> "Checking for updates"
                    is UpdateUi.UpToDate -> "You're up to date"
                    is UpdateUi.Available -> "Update available · v${ui.release.version}"
                    is UpdateUi.Downloading -> "Downloading v${ui.release.version}"
                    is UpdateUi.Ready -> "Download complete"
                    is UpdateUi.Failed -> "Update check failed"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (ui) {
                    is UpdateUi.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Talking to GitHub…")
                        }
                    }
                    is UpdateUi.UpToDate -> {
                        Text("NotiMorph v${BuildConfig.VERSION_NAME} is the newest version. 🎉")
                    }
                    is UpdateUi.Available -> {
                        Column(
                            Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                        ) {
                            Text(
                                ui.release.notes,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "${"%.1f".format(ui.release.sizeBytes / 1048576f)} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateUi.Downloading -> {
                        LinearProgressIndicator(
                            progress = { ui.pct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${ui.pct}% — keep the app open",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateUi.Ready -> {
                        Text("The update is downloaded. Tap Install to apply it.")
                    }
                    is UpdateUi.Failed -> {
                        Text(ui.msg, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            when (ui) {
                is UpdateUi.Available -> Button(onClick = { onDownload(ui.release) }) { Text("Download") }
                is UpdateUi.Ready -> Button(onClick = { onInstall(ui.file) }) { Text("Install") }
                is UpdateUi.Failed -> Button(onClick = onDismiss) { Text("OK") }
                is UpdateUi.UpToDate -> Button(onClick = onDismiss) { Text("OK") }
                else -> {}
            }
        },
        dismissButton = {
            when (ui) {
                is UpdateUi.Checking, is UpdateUi.Downloading -> {}
                is UpdateUi.Ready -> TextButton(onClick = onDismiss) { Text("Later") }
                is UpdateUi.Available -> TextButton(onClick = onDismiss) { Text("Later") }
                else -> {}
            }
        },
    )
}

private fun installApk(context: android.content.Context, file: File) {
    val pm = context.packageManager
    if (!pm.canRequestPackageInstalls()) {
        val i = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        return
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file,
    )
    val i = Intent(Intent.ACTION_INSTALL_PACKAGE)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(i)
}

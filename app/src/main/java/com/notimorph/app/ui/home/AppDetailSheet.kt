package com.notimorph.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notimorph.app.NotiMorphApp
import com.notimorph.app.core.AiClient
import com.notimorph.app.core.PromptPresets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailSheet(entry: AppEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NotiMorphApp
    val prefs = app.prefs
    val s by prefs.state.collectAsState()

    val currentCustom = s.appPrompts[entry.pkg].orEmpty()
    var text by remember(entry.pkg, currentCustom) { mutableStateOf(currentCustom) }
    var showTest by remember { mutableStateOf(false) }
    var showDefaultEditor by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.icon?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(entry.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        entry.pkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Restyle notifications", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rewrite this app's notifications before they reach your watch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SwitchMorphic(
                    checked = s.enabledApps.contains(entry.pkg),
                    onChecked = { on ->
                        // App-level scope: survives the sheet leaving composition.
                        app.appScope.launch { prefs.setAppEnabled(entry.pkg, on) }
                    },
                )
            }

            // Custom prompt section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoFixHigh,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Custom prompt", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Optional. Leave empty to use the default prompt from Settings.",
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
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text("e.g. Translate everything to Banglish…") },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (currentCustom.isNotBlank()) {
                        TextButton(onClick = {
                            app.appScope.launch { prefs.setAppPrompt(entry.pkg, "") }
                            text = ""
                        }) { Text("Remove custom") }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { showTest = true }) { Text("Test") }
                    Button(
                        onClick = {
                            // IMPORTANT: write from the app-level scope and dismiss
                            // immediately — a composition-bound scope would be
                            // cancelled mid-write when the sheet closes.
                            app.appScope.launch { prefs.setAppPrompt(entry.pkg, text) }
                            onDismiss()
                        },
                    ) { Text("Save") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (currentCustom.isNotBlank()) "✦ Using a custom prompt for this app"
                        else "Using the default prompt",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentCustom.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showDefaultEditor = true }) {
                        Text("Edit default prompt")
                    }
                }
            }
        }
    }

    if (showDefaultEditor) {
        com.notimorph.app.ui.settings.PromptEditorDialog(
            initial = s.defaultPrompt,
            onSave = { app.appScope.launch { prefs.setDefaultPrompt(it) } },
            onDismiss = { showDefaultEditor = false },
        )
    }

    if (showTest) {
        TestPromptDialog(
            title = entry.label,
            prompt = if (text.isBlank()) null else text,
            onDismiss = { showTest = false },
        )
    }
}

/** Reusable switch with a springy feel. */
@Composable
fun SwitchMorphic(checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChecked)
}

/** Runs the active AI provider against a sample notification and shows the result. */
@Composable
fun TestPromptDialog(title: String, prompt: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NotiMorphApp
    val s by app.prefs.state.collectAsState()
    var result by remember { mutableStateOf<Result<String>?>(null) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        result = AiClient.transform(
            s,
            appName = title,
            title = "Rahim",
            body = "আপনার অর্ডার #4521 ডেলিভারির জন্য প্রস্তুত! OTP 8812 ব্যবহার করুন। Track: https://x.co/4521 🎉",
            customPrompt = prompt,
        )
        running = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Preview · $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Asking ${s.provider.label}…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    result?.fold(
                        onSuccess = { text ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CheckCircle, null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Result", style = MaterialTheme.typography.labelLarge)
                            }
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                        },
                        onFailure = { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ErrorOutline, null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Failed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                e.message ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Provider: ${s.provider.label} · ${s.currentModel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

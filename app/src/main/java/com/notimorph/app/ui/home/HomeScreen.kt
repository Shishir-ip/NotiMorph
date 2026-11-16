package com.notimorph.app.ui.home

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.notimorph.app.NotiMorphApp
import com.notimorph.app.core.NotiListener
import com.notimorph.app.core.Provider
import com.notimorph.app.ui.common.EmptyState
import com.notimorph.app.ui.common.SkeletonRow
import com.notimorph.app.ui.common.StatusPill
import com.notimorph.app.ui.common.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val icon: ImageBitmapHolder?,
)

@JvmInline
value class ImageBitmapHolder(val bitmap: androidx.compose.ui.graphics.ImageBitmap)

/** Process-wide cache so returning to Home is instant (icons decoded once). */
object AppListCache {
    @Volatile
    var entries: List<AppEntry>? = null
}

private enum class HomeTab(val label: String) {
    ENABLED("Enabled"),
    USER("User"),
    SYSTEM("System"),
    ALL("All"),
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as NotiMorphApp
    val prefs = app.prefs
    val s by prefs.state.collectAsState()
    val connected by app.listenerConnected.collectAsState()
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf(AppListCache.entries) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(HomeTab.USER) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(Unit) {
        val fresh = loadApps(context)
        AppListCache.entries = fresh
        apps = fresh
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ---- Header --------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("NotiMorph", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "AI notification restyler for your watch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { nav.navigate("history") }) {
                Icon(Icons.Rounded.History, "History")
            }
            IconButton(onClick = { nav.navigate("settings") }) {
                Icon(Icons.Rounded.Settings, "Settings")
            }
        }

        // ---- Access onboarding card ----------------------------------------
        AnimatedVisibility(visible = !connected) {
            AccessCard(onGrant = { NotiListener.openAccessSettings(context) })
        }

        // ---- Android 15+ locked-phone hint ----------------------------------
        AnimatedVisibility(visible = connected && android.os.Build.VERSION.SDK_INT >= 35) {
            LockedPhoneHintCard(
                onOpenSettings = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent("android.settings.NOTIFICATION_SETTINGS")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }

        // ---- Status pills ---------------------------------------------------
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatusPill(
                text = if (connected) "Listener on" else "Listener off",
                color = if (connected) Color(0xFF34A853) else MaterialTheme.colorScheme.error,
                icon = if (connected) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
            )
            if (s.enabledApps.isNotEmpty()) {
                StatusPill(
                    text = if (s.enabledApps.size == 1) "1 app enabled" else "${s.enabledApps.size} apps enabled",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!s.isConfigured()) {
                Box(Modifier.clickable { nav.navigate("settings") }) {
                    StatusPill(text = "No API key — tap to set up", color = Color(0xFFF9AB00), icon = Icons.Rounded.Key)
                }
            } else {
                StatusPill(text = s.provider.label, color = MaterialTheme.colorScheme.primary)
            }
        }

        // ---- Search ---------------------------------------------------------
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            placeholder = { Text("Search apps…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Rounded.Close, "Clear search",
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        // ---- Filter chips ----------------------------------------------------
        val counts = remember(apps, s.enabledApps) {
            HomeTab.entries.associateWith { t ->
                when (t) {
                    HomeTab.ENABLED -> s.enabledApps.size
                    else -> apps?.count {
                        if (t == HomeTab.SYSTEM) it.isSystem else if (t == HomeTab.USER) !it.isSystem else true
                    } ?: 0
                }
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            items(HomeTab.entries) { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text("${t.label} · ${counts[t] ?: 0}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        // ---- List ------------------------------------------------------------
        val list = remember(apps, query, tab, s.enabledApps) {
            apps?.filter { entry ->
                val matchesQuery = query.isBlank() ||
                    entry.label.contains(query, ignoreCase = true) ||
                    entry.pkg.contains(query, ignoreCase = true)
                val matchesTab = when (tab) {
                    HomeTab.ENABLED -> s.enabledApps.contains(entry.pkg)
                    HomeTab.USER -> !entry.isSystem
                    HomeTab.SYSTEM -> entry.isSystem
                    HomeTab.ALL -> true
                }
                matchesQuery && matchesTab
            }
        }

        androidx.compose.animation.Crossfade(
            targetState = list,
            label = "list",
        ) { l ->
            when {
                l == null -> Column {
                    repeat(9) { SkeletonRow() }
                }
                l.isEmpty() -> Column(Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = Icons.Rounded.Watch,
                        title = if (tab == HomeTab.ENABLED) "No apps enabled yet" else "Nothing found",
                        subtitle = if (tab == HomeTab.ENABLED)
                            "Turn on the switch for any app below and its notifications will be restyled for your watch."
                        else "Try a different search or tab.",
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(l, key = { it.pkg }) { entry ->
                        AppRow(
                            entry = entry,
                            enabled = s.enabledApps.contains(entry.pkg),
                            hasCustomPrompt = s.appPrompts.containsKey(entry.pkg),
                            onToggle = { on ->
                                scope.launch { prefs.setAppEnabled(entry.pkg, on) }
                            },
                            onClick = { selectedApp = entry },
                        )
                    }
                    item {
                        Text(
                            "Only enabled apps are restyled — every other notification is left untouched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }
    }

    selectedApp?.let { entry ->
        AppDetailSheet(entry = entry, onDismiss = { selectedApp = null })
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    enabled: Boolean,
    hasCustomPrompt: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.icon?.let {
            Image(
                bitmap = it.bitmap,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        } ?: Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasCustomPrompt) {
                    Spacer(Modifier.width(6.dp))
                    Text("✦", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                entry.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun LockedPhoneHintCard(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                RoundedCornerShape(20.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Locked-phone tip (Android 15+)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Android may hide OTP/bank text from apps while the phone is locked. " +
                    "Turning off \u201CSensitive notifications\u201D in system notification settings fixes it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onOpenSettings) { Text("Open") }
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                ),
                RoundedCornerShape(20.dp),
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Watch,
            null,
            Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Notification access needed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Allow NotiMorph to read notifications so it can restyle them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onGrant) { Text("Grant") }
    }
}

private suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.getInstalledApplications(0)
        .map { ai ->
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrElse { ai.packageName }
            val icon: Drawable? = runCatching { pm.getApplicationIcon(ai.packageName) }.getOrNull()
            val bmp = runCatching { icon?.toBitmap(72, 72) }.getOrNull()
            AppEntry(
                pkg = ai.packageName,
                label = label,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                icon = bmp?.let { ImageBitmapHolder(it.asImageBitmap()) },
            )
        }
        .filter { it.label.isNotBlank() }
        .sortedBy { it.label.lowercase() }
}

package com.notimorph.app.core

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
import com.notimorph.app.NotiMorphApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Watches every incoming notification, and for apps the user enabled, asks the
 * AI provider to restyle the text, then posts a brand-new notification with the
 * same app identity (icon, color, tap-action) but readable content.
 */
class NotiListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(permits = 4) // bound concurrent AI calls

    // Duplicate suppression: raw notification -> last seen time.
    private val seen = object : LruCache<String, Long>(64) {}
    // Instant replay cache: raw notification -> transformed text (skips the API on reposts).
    private val cached = object : LruCache<String, String>(64) {}

    private val appNameCache = object : LruCache<String, String>(128) {}

    override fun onListenerConnected() {
        super.onListenerConnected()
        (application as NotiMorphApp).listenerConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        (application as NotiMorphApp).listenerConnected.value = false
        // Ask the system to rebind so the service survives restarts.
        requestRebind(ComponentName(this, NotiListener::class.java))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as NotiMorphApp
        val s = app.prefs.state.value

        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        if (!s.enabledApps.contains(sbn.packageName)) return
        if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()
        if (body.isBlank()) return

        val key = "${sbn.packageName}|$title|$body"
        val now = SystemClock.elapsedRealtime()
        synchronized(seen) {
            val last = seen.get(key)
            if (last != null && now - last < DUPLICATE_WINDOW_MS) return
            seen.put(key, now)
        }

        val cachedText = synchronized(cached) { cached.get(key) }
        scope.launch {
            val appName = appLabel(sbn.packageName)
            if (cachedText != null) {
                deliver(sbn, s, appName, title, body, key, cachedText)
                return@launch
            }
            if (!s.isConfigured()) return@launch
            gate.withPermit {
                val result = AiClient.transform(s, appName, title, body, s.promptFor(sbn.packageName))
                val text = result.getOrNull() ?: return@withPermit
                synchronized(cached) { cached.put(key, text) }
                deliver(sbn, s, appName, title, body, key, text)
            }
        }
    }

    private suspend fun deliver(
        sbn: StatusBarNotification,
        s: Settings,
        appName: String,
        title: String,
        body: String,
        key: String,
        text: String,
    ) {
        if (text.isBlank() || text == body) return
        Notifier.post(this, sbn, appName, title, text)
        if (s.hideOriginal) {
            runCatching { cancelNotification(sbn.key) }
        }
        (application as NotiMorphApp).prefs.appendHistory(
            HistoryEntry(
                app = appName,
                title = title,
                before = body.take(180),
                after = text.take(180),
                time = System.currentTimeMillis(),
            )
        )
    }

    private fun appLabel(pkg: String): String {
        synchronized(appNameCache) { appNameCache.get(pkg) }?.let { return it }
        val label = try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
        synchronized(appNameCache) { appNameCache.put(pkg, label) }
        return label
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 5_000L

        /** Whether this app currently holds notification-listener access. */
        fun isConnected(context: Context): Boolean {
            val raw = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return raw.split(":").any {
                ComponentName.unflattenFromString(it)?.packageName == context.packageName
            }
        }

        /** Open the system screen where the user grants notification access. */
        fun openAccessSettings(context: Context) {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

package com.notimorph.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification

/** Posts the restyled notifications. */
object Notifier {

    private const val CHANNEL = "notimorph_relay"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL, "Restyled notifications", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications rewritten by NotiMorph for your smartwatch"
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }

    fun post(context: Context, sbn: StatusBarNotification, appName: String, title: String, body: String) {
        ensureChannel(context)
        val builder = Notification.Builder(context, CHANNEL)
            // Reuse the sender's own small icon + color so the watch still shows
            // a familiar look, with the restyled text as the content.
            .setSmallIcon(sbn.notification.smallIcon)
            .setContentTitle(title.ifBlank { appName })
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setSubText("✦ NotiMorph · $appName")

        sbn.notification.contentIntent?.let { builder.setContentIntent(it) }
        val color = sbn.notification.color
        if (color != 0) builder.setColor(color)
        sbn.notification.category?.let { builder.setCategory(it) }

        val id = (sbn.key + "|" + body).hashCode()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet; nothing we can do here.
        }
    }
}

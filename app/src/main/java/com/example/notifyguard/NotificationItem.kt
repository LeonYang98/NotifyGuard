package com.example.notifyguard

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

data class NotificationItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val channelId: String,
    val ongoing: Boolean,
    val postTime: Long
) {
    companion object {
        fun from(context: Context, sbn: StatusBarNotification): NotificationItem {
            val appName = runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)
            val extras = sbn.notification.extras
            return NotificationItem(
                key = sbn.key,
                pkg = sbn.packageName,
                appName = appName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                channelId = sbn.notification.channelId.orEmpty(),
                ongoing = sbn.isOngoing,
                postTime = sbn.postTime
            )
        }
    }
}
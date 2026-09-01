package com.example.notifyguard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class GuardListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refreshSnapshot()
        enforceRules()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationCenter.upsert(NotificationItem.from(this, sbn))
        if (shouldAutoHide(sbn)) {
            runCatching { cancelNotification(sbn.key) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationCenter.remove(sbn.key)
    }

    fun refreshSnapshot() {
        val list = activeNotifications?.map { NotificationItem.from(this, it) }.orEmpty()
        NotificationCenter.replaceAll(list)
    }

    fun enforceRules() {
        activeNotifications?.forEach { sbn ->
            if (shouldAutoHide(sbn)) runCatching { cancelNotification(sbn.key) }
        }
    }

    fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
    }

    private fun shouldAutoHide(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val channelId = sbn.notification.channelId.orEmpty()
        return RuleStore.matches(this, sbn.packageName, channelId)
    }

    companion object {
        @Volatile
        var instance: GuardListenerService? = null
            private set
    }
}
package com.example.notifyguard

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class GuardListenerService : NotificationListenerService() {

    companion object {
        var instance: GuardListenerService? = null
            private set

        private val EXCLUDED_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_TRANSPORT
        )
    }

    override fun onCreate() {
        super.onCreate()
        RulesStore.init(applicationContext)
        HistoryStore.init(applicationContext)
    }

    override fun onListenerConnected() {
        instance = this
        NotificationCenter.setConnected(true)
        reapplyRules()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        NotificationCenter.setConnected(false)
        try {
            requestRebind(ComponentName(this, GuardListenerService::class.java))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        NotificationCenter.setConnected(false)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        applyRules(sbn)
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        publish()
    }

    fun reapplyRules() {
        try {
            activeNotifications?.forEach { applyRules(it) }
        } catch (_: Exception) {
        }
        publish()
    }

    private fun applyRules(sbn: StatusBarNotification) {
        if (!shouldHandle(sbn)) return
        val ruleKey = RulesStore.ruleKey(sbn.packageName, channelIdOf(sbn))
        if (!RulesStore.has(ruleKey)) return
        if (RulesStore.isSuppressed(ruleKey)) return
        if (RulesStore.onRuleMatched(ruleKey)) {
            try {
                cancelNotification(sbn.key)
                // cancel 不会动我们手上这个 sbn，extras 照样读得到，所以先撤后记档。
                HistoryStore.add(
                    packageName = sbn.packageName,
                    title = extraOf(sbn, Notification.EXTRA_TITLE),
                    text = extraOf(sbn, Notification.EXTRA_TEXT),
                    channelId = channelIdOf(sbn),
                    ruleKey = ruleKey,
                    auto = true
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun shouldHandle(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val category = sbn.notification?.category
        if (category != null && EXCLUDED_CATEGORIES.contains(category)) return false
        return true
    }

    private fun channelIdOf(sbn: StatusBarNotification): String? = sbn.notification?.channelId

    private fun extraOf(sbn: StatusBarNotification, key: String): String =
        sbn.notification?.extras?.getCharSequence(key)?.toString().orEmpty()

    private fun publish() {
        val items = activeNotifications.orEmpty()
            .filter { shouldHandle(it) }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                NotificationItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = extraOf(sbn, Notification.EXTRA_TITLE),
                    text = extraOf(sbn, Notification.EXTRA_TEXT),
                    channelId = channelIdOf(sbn),
                    isOngoing = sbn.isOngoing,
                    postTime = sbn.postTime
                )
            }
        NotificationCenter.publish(items)
    }
}

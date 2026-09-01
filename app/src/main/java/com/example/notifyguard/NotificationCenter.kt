package com.example.notifyguard

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val channelId: String?,
    val isOngoing: Boolean,
    val postTime: Long
)

object NotificationCenter {

    interface Listener {
        fun onNotifications(items: List<NotificationItem>)
        fun onServiceState(connected: Boolean)
    }

    @Volatile
    private var items: List<NotificationItem> = emptyList()

    @Volatile
    var serviceConnected: Boolean = false
        private set

    private var listener: Listener? = null

    fun attach(l: Listener) {
        listener = l
        l.onNotifications(items)
        l.onServiceState(serviceConnected)
    }

    fun detach(l: Listener) {
        if (listener === l) listener = null
    }

    fun publish(newItems: List<NotificationItem>) {
        items = newItems
        listener?.onNotifications(newItems)
    }

    fun current(): List<NotificationItem> = items

    fun setConnected(connected: Boolean) {
        serviceConnected = connected
        listener?.onServiceState(connected)
    }
}

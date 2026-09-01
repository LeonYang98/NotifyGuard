package com.example.notifyguard

import android.os.Handler
import android.os.Looper

object NotificationCenter {

    @Volatile
    var items: List<NotificationItem> = emptyList()
        private set

    private val listeners = mutableSetOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun replaceAll(list: List<NotificationItem>) {
        items = sort(list)
        notifyChanged()
    }

    @Synchronized
    fun upsert(item: NotificationItem) {
        items = sort(items.filterNot { it.key == item.key } + item)
        notifyChanged()
    }

    @Synchronized
    fun remove(key: String) {
        items = items.filterNot { it.key == key }
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun notifyChanged() {
        mainHandler.post {
            synchronized(listeners) { listeners.toList() }.forEach { it.invoke() }
        }
    }

    private fun sort(list: List<NotificationItem>) = list.sortedWith(
        compareByDescending<NotificationItem> { it.ongoing }.thenByDescending { it.postTime }
    )
}
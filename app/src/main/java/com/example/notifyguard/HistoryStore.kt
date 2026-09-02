package com.example.notifyguard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 隐藏历史。每条被消除的通知都在这里留一份档，标题正文一起存下来 ——
 * 原通知已经被系统撤掉、没法再放回通知栏，但用户至少能回来看自己漏了什么。
 */
object HistoryStore {

    private const val PREFS_NAME = "hide_history"
    private const val KEY_RECORDS = "records"

    // 上限，超出后丢最旧的。SharedPreferences 是整串读写，不能让它无限长。
    private const val MAX_RECORDS = 500

    // 同一规则下标题正文都相同的通知，这个窗口内只记一条，免得刷屏应用把历史灌满。
    private const val DEDUPE_WINDOW_MS = 10_000L

    private lateinit var prefs: SharedPreferences

    // 内存里始终按「新→旧」排好，UI 直接取用，不必每次重组都解析 JSON。
    private var cache: MutableList<Record> = mutableListOf()
    private var changeCount = 0

    data class Record(
        val id: Long,
        val packageName: String,
        val title: String,
        val text: String,
        val channelId: String?,
        val ruleKey: String,
        val hiddenAt: Long,
        val auto: Boolean,
        val restored: Boolean
    )

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cache = parse(prefs.getString(KEY_RECORDS, null))
    }

    /** 每次增删改都会 +1，UI 靠它判断要不要重新读一遍。 */
    @Synchronized
    fun version(): Int = changeCount

    @Synchronized
    fun records(): List<Record> = cache.toList()

    @Synchronized
    fun count(): Int = cache.size

    @Synchronized
    fun add(
        packageName: String,
        title: String,
        text: String,
        channelId: String?,
        ruleKey: String,
        auto: Boolean
    ) {
        if (!::prefs.isInitialized) return
        val now = System.currentTimeMillis()
        val dup = cache.any {
            it.ruleKey == ruleKey && it.title == title && it.text == text &&
                now - it.hiddenAt < DEDUPE_WINDOW_MS
        }
        if (dup) return
        val id = (cache.maxOfOrNull { it.id } ?: 0L) + 1L
        cache.add(0, Record(id, packageName, title, text, channelId, ruleKey, now, auto, false))
        while (cache.size > MAX_RECORDS) cache.removeAt(cache.size - 1)
        persist()
    }

    /** 只负责把记录标成已恢复；规则本身由调用方从 [RulesStore] 里撤。 */
    @Synchronized
    fun markRestored(id: Long) {
        val i = cache.indexOfFirst { it.id == id }
        if (i < 0) return
        cache[i] = cache[i].copy(restored = true)
        persist()
    }

    @Synchronized
    fun delete(id: Long) {
        if (cache.removeAll { it.id == id }) persist()
    }

    @Synchronized
    fun clearAll() {
        cache.clear()
        persist()
    }

    private fun persist() {
        changeCount++
        val arr = JSONArray()
        cache.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("pkg", r.packageName)
            o.put("title", r.title)
            o.put("text", r.text)
            if (r.channelId != null) o.put("ch", r.channelId)
            o.put("rk", r.ruleKey)
            o.put("t", r.hiddenAt)
            o.put("auto", r.auto)
            o.put("rs", r.restored)
            arr.put(o)
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    private fun parse(raw: String?): MutableList<Record> {
        if (raw.isNullOrEmpty()) return mutableListOf()
        val out = mutableListOf<Record>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Record(
                        id = o.optLong("id", 0L),
                        packageName = o.optString("pkg"),
                        title = o.optString("title"),
                        text = o.optString("text"),
                        channelId = if (o.has("ch")) o.optString("ch") else null,
                        ruleKey = o.optString("rk"),
                        hiddenAt = o.optLong("t", 0L),
                        auto = o.optBoolean("auto", true),
                        restored = o.optBoolean("rs", false)
                    )
                )
            }
        } catch (_: Exception) {
            // 存档坏了就当没有历史，不要因为这个崩在启动路径上。
            return mutableListOf()
        }
        return out
    }
}

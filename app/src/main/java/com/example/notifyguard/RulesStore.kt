package com.example.notifyguard

import android.content.Context
import android.content.SharedPreferences

object RulesStore {
    private const val PREFS_NAME = "auto_hide_rules"
    private const val KEY_RULES = "rules"

    private const val FREQ_WINDOW_MS = 10_000L
    private const val FREQ_MAX_IN_WINDOW = 3
    private const val SUPPRESS_MS = 30_000L

    private lateinit var prefs: SharedPreferences

    private val recentHits = mutableMapOf<String, MutableList<Long>>()
    private val stats = mutableMapOf<String, Stats>()

    data class Stats(var hits: Int = 0, var suppressedUntil: Long = 0L)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun ruleKey(pkg: String, channelId: String?): String = "$pkg|${channelId ?: ""}"

    fun rules(): Set<String> = prefs.getStringSet(KEY_RULES, emptySet())?.toSet() ?: emptySet()

    fun has(ruleKey: String): Boolean = rules().contains(ruleKey)

    fun has(pkg: String, channelId: String?): Boolean = has(ruleKey(pkg, channelId))

    fun add(pkg: String, channelId: String?) {
        val set = rules().toMutableSet()
        set.add(ruleKey(pkg, channelId))
        prefs.edit().putStringSet(KEY_RULES, set).apply()
    }

    fun remove(ruleKey: String) {
        val set = rules().toMutableSet()
        set.remove(ruleKey)
        prefs.edit().putStringSet(KEY_RULES, set).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RULES).apply()
        recentHits.clear()
        stats.clear()
    }

    @Synchronized
    fun isSuppressed(ruleKey: String): Boolean {
        val s = stats[ruleKey] ?: return false
        return System.currentTimeMillis() < s.suppressedUntil
    }

    @Synchronized
    fun onRuleMatched(ruleKey: String): Boolean {
        val now = System.currentTimeMillis()
        val list = recentHits.getOrPut(ruleKey) { mutableListOf() }
        list.removeAll { now - it > FREQ_WINDOW_MS }
        if (list.size >= FREQ_MAX_IN_WINDOW) {
            stats.getOrPut(ruleKey) { Stats() }.suppressedUntil = now + SUPPRESS_MS
            return false
        }
        list.add(now)
        stats.getOrPut(ruleKey) { Stats() }.hits++
        return true
    }

    fun statsOf(ruleKey: String): Stats = stats[ruleKey] ?: Stats()
}

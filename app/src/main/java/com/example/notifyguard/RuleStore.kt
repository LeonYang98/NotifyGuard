package com.example.notifyguard

import android.content.Context

object RuleStore {

    private const val PREFS = "auto_hide_rules"
    private const val KEY_RULES = "rules"

    fun rules(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_RULES, emptySet())?.toSet().orEmpty()

    fun add(context: Context, pkg: String, channelId: String) {
        save(context, rules(context) + encode(pkg, channelId))
    }

    fun clear(context: Context) {
        save(context, emptySet())
    }

    fun matches(context: Context, pkg: String, channelId: String): Boolean {
        val all = rules(context)
        return all.contains(encode(pkg, channelId)) || all.contains(encode(pkg, ""))
    }

    private fun encode(pkg: String, channelId: String) = "$pkg|$channelId"

    private fun save(context: Context, rules: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_RULES, rules).apply()
    }
}
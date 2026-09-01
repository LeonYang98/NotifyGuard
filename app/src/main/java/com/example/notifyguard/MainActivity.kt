package com.example.notifyguard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), NotificationCenter.Listener {

    private lateinit var adapter: NotificationAdapter
    private lateinit var cardPermission: LinearLayout
    private lateinit var tvServiceState: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView

    private var onlyOngoing = false
    private var allItems: List<NotificationItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RulesStore.init(applicationContext)
        setContentView(R.layout.activity_main)

        cardPermission = findViewById(R.id.card_permission)
        tvServiceState = findViewById(R.id.tv_service_state)
        tvCount = findViewById(R.id.tv_count)
        tvEmpty = findViewById(R.id.tv_empty)

        adapter = NotificationAdapter(
            onCancel = { item -> cancelNow(item) },
            onChannel = { item -> openChannelSettings(item) },
            onRule = { item -> toggleRule(item) }
        )
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btn_grant).setOnClickListener { openListenerSettings() }
        findViewById<Button>(R.id.btn_rules).setOnClickListener { showRulesDialog() }
        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            if (GuardListenerService.instance != null) {
                GuardListenerService.instance?.reapplyRules()
                Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "监听服务未连接", Toast.LENGTH_SHORT).show()
            }
            refreshPermissionCard()
        }
        findViewById<SwitchCompat>(R.id.switch_ongoing).setOnCheckedChangeListener { _, checked ->
            onlyOngoing = checked
            applyFilter()
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationCenter.attach(this)
        refreshPermissionCard()
    }

    override fun onPause() {
        super.onPause()
        NotificationCenter.detach(this)
    }

    override fun onNotifications(items: List<NotificationItem>) {
        allItems = items
        applyFilter()
    }

    override fun onServiceState(connected: Boolean) {
        refreshPermissionCard()
    }

    private fun applyFilter() {
        val shown = if (onlyOngoing) allItems.filter { it.isOngoing } else allItems
        adapter.setItems(shown)
        val ongoingCount = allItems.count { it.isOngoing }
        tvCount.text = "共 ${allItems.size} 条通知 · 常驻 $ongoingCount 条"
        tvEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun listenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refreshPermissionCard() {
        val granted = listenerEnabled()
        cardPermission.visibility = if (granted) View.GONE else View.VISIBLE
        tvServiceState.text = when {
            !granted -> ""
            NotificationCenter.serviceConnected -> "监听服务运行中"
            else -> "已授权，等待系统启动监听服务…（若长时间未连接，请重新开关一次授权）"
        }
    }

    private fun openListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "无法打开系统设置页", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelNow(item: NotificationItem) {
        val service = GuardListenerService.instance
        if (service == null) {
            Toast.makeText(this, "监听服务未连接，请先完成授权", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            service.cancelNotification(item.key)
            Toast.makeText(this, "已隐藏", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "隐藏失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openChannelSettings(item: NotificationItem) {
        if (item.channelId != null) {
            try {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, item.channelId)
                startActivity(intent)
                Toast.makeText(this, "关闭该渠道即可永久生效", Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) {
            }
        }
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "跳转失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRule(item: NotificationItem) {
        val key = RulesStore.ruleKey(item.packageName, item.channelId)
        if (RulesStore.has(key)) {
            RulesStore.remove(key)
            Toast.makeText(this, "已取消自动隐藏", Toast.LENGTH_SHORT).show()
        } else {
            RulesStore.add(item.packageName, item.channelId)
            Toast.makeText(this, "已开启自动隐藏: 该渠道新通知将被自动消除", Toast.LENGTH_SHORT).show()
        }
        GuardListenerService.instance?.reapplyRules()
        onNotifications(NotificationCenter.current())
    }

    private fun showRulesDialog() {
        val rules = RulesStore.rules()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(pad, pad / 2, pad, pad / 2)
        val scroll = ScrollView(this).apply { addView(container) }

        var dialog: AlertDialog? = null

        if (rules.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "暂无规则。\n\n在通知条目上点「自动隐藏」即可添加；命中规则的新通知会被自动消除。"
            })
        } else {
            rules.sorted().forEach { ruleKey ->
                val pkg = ruleKey.substringBefore('|')
                val channel = ruleKey.substringAfter('|').ifEmpty { "(应用级)" }
                val name = AppInfoCache.appName(packageManager, pkg)
                val stats = RulesStore.statsOf(ruleKey)
                val suppressed = RulesStore.isSuppressed(ruleKey)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val info = TextView(this).apply {
                    val extra = if (suppressed) "  [重发过于频繁,已暂停30秒]" else ""
                    text = "$name\n渠道: $channel · 已消除 ${stats.hits} 次$extra"
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val del = Button(this).apply {
                    text = "删除"
                    setOnClickListener {
                        RulesStore.remove(ruleKey)
                        GuardListenerService.instance?.reapplyRules()
                        onNotifications(NotificationCenter.current())
                        dialog?.dismiss()
                        showRulesDialog()
                    }
                }
                row.addView(info)
                row.addView(del)
                container.addView(row)
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("自动隐藏规则 (${rules.size})")
            .setView(scroll)
            .setPositiveButton("关闭", null)
        if (rules.isNotEmpty()) {
            builder.setNeutralButton("清空全部") { _, _ ->
                RulesStore.clearAll()
                GuardListenerService.instance?.reapplyRules()
                onNotifications(NotificationCenter.current())
                Toast.makeText(this, "已清空全部规则", Toast.LENGTH_SHORT).show()
            }
        }
        dialog = builder.create()
        dialog.show()
    }
}

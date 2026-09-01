package com.example.notifyguard

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private val dataListener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = NotificationAdapter(
            onHide = { item ->
                GuardListenerService.instance?.dismiss(item.key)
                NotificationCenter.remove(item.key)
                Toast.makeText(this, "已隐藏；若对方重发，建议改用「自动隐藏」", Toast.LENGTH_SHORT).show()
            },
            onChannel = { item -> openChannelSettings(item) },
            onRule = { item ->
                RuleStore.add(this, item.pkg, item.channelId)
                GuardListenerService.instance?.enforceRules()
                Toast.makeText(this, "已加入自动隐藏：" + item.appName, Toast.LENGTH_SHORT).show()
                render()
            }
        )

        val list = findViewById<RecyclerView>(R.id.rvList)
        list.layoutManager = LinearLayoutManager(this)
        list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        list.adapter = adapter

        findViewById<Button>(R.id.btnGrant).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "在列表中找到「通知哨兵」并打开开关", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            val service = GuardListenerService.instance
            if (service == null) {
                NotificationListenerService.requestRebind(
                    ComponentName(this, GuardListenerService::class.java)
                )
                Toast.makeText(this, "正在请求系统重新连接监听服务，请稍候再点刷新", Toast.LENGTH_SHORT).show()
            } else {
                service.refreshSnapshot()
                Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnClearRules).setOnClickListener {
            RuleStore.clear(this)
            Toast.makeText(this, "已清空自动隐藏规则", Toast.LENGTH_SHORT).show()
            render()
        }
        findViewById<CheckBox>(R.id.cbOnlyOngoing).setOnCheckedChangeListener { _, _ -> render() }
    }

    override fun onResume() {
        super.onResume()
        NotificationCenter.addListener(dataListener)
        GuardListenerService.instance?.refreshSnapshot()
        render()
    }

    override fun onPause() {
        NotificationCenter.removeListener(dataListener)
        super.onPause()
    }

    private fun render() {
        val granted = hasNotificationAccess()
        findViewById<TextView>(R.id.tvStatus).text =
            if (granted) "通知使用权：已授权" else "通知使用权：未授权，请先点击下方按钮开启"
        findViewById<Button>(R.id.btnGrant).visibility = if (granted) View.GONE else View.VISIBLE

        val all = NotificationCenter.items
        val rules = RuleStore.rules(this)
        adapter.rules = rules
        val onlyOngoing = findViewById<CheckBox>(R.id.cbOnlyOngoing).isChecked
        adapter.submitList(if (onlyOngoing) all.filter { it.ongoing } else all)

        findViewById<TextView>(R.id.tvSummary).text =
            "当前通知 " + all.size + " 条 · 常驻 " + all.count { it.ongoing } + " 条 · 自动隐藏规则 " + rules.size + " 条"
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(':').any { it.startsWith(packageName) }
    }

    private fun openChannelSettings(item: NotificationItem) {
        val intent = if (item.channelId.isNotEmpty()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, item.pkg)
                .putExtra(Settings.EXTRA_CHANNEL_ID, item.channelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, item.pkg)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, item.pkg)
            )
        }
    }
}
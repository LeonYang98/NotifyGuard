package com.example.notifyguard

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.NotificationManagerCompat
import com.example.notifyguard.ui.HistoryScreen
import com.example.notifyguard.ui.MainScreen
import com.example.notifyguard.ui.NotifyGuardTheme
import com.example.notifyguard.ui.RulesDialog

class MainActivity : ComponentActivity() {

    private val itemsState = mutableStateOf<List<NotificationItem>>(emptyList())
    private val connectedState = mutableStateOf(false)
    private val permissionState = mutableStateOf(false)
    private val onlyOngoingState = mutableStateOf(false)
    private val showRulesState = mutableStateOf(false)
    private val showHistoryState = mutableStateOf(false)
    private val showRevivePromptState = mutableStateOf(false)
    private val rulesVersionState = mutableIntStateOf(0)
    private val historyVersionState = mutableIntStateOf(0)

    private val reviveHandler = Handler(Looper.getMainLooper())

    private val centerListener = object : NotificationCenter.Listener {
        override fun onNotifications(items: List<NotificationItem>) {
            itemsState.value = items
            // 自动隐藏后监听服务紧跟着会 publish 一次，借这个时机把历史计数同步过来。
            syncHistoryVersion()
        }

        override fun onServiceState(connected: Boolean) {
            connectedState.value = connected
            evaluateState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RulesStore.init(applicationContext)
        HistoryStore.init(applicationContext)
        syncHistoryVersion()
        enableEdgeToEdge()
        setContent {
            NotifyGuardTheme {
                if (showHistoryState.value) {
                    HistoryScreen(
                        historyVersion = historyVersionState.intValue,
                        rulesVersion = rulesVersionState.intValue,
                        onBack = { showHistoryState.value = false },
                        onRestore = { restoreFromHistory(it) },
                        onDelete = { deleteHistory(it) },
                        onClearAll = { clearHistory() }
                    )
                } else {
                    // 读一下 historyVersion，历史有增减时顶栏那个计数才会跟着重组。
                    val historyVersion = historyVersionState.intValue
                    MainScreen(
                        items = itemsState.value,
                        appVersion = BuildConfig.VERSION_NAME,
                        serviceConnected = connectedState.value,
                        permissionGranted = permissionState.value,
                        showRevivePrompt = showRevivePromptState.value,
                        onlyOngoing = onlyOngoingState.value,
                        rulesVersion = rulesVersionState.intValue,
                        historyCount = remember(historyVersion) { HistoryStore.count() },
                        onOnlyOngoingChange = { onlyOngoingState.value = it },
                        onGrantClick = { openListenerSettings() },
                        onReviveClick = { reviveListener() },
                        onOpenSettings = { openListenerSettings() },
                        onRefresh = { refresh() },
                        onShowRules = { showRulesState.value = true },
                        onShowHistory = { showHistoryState.value = true },
                        onCancel = { cancelNow(it) },
                        onChannel = { openChannelSettings(it) },
                        onRule = { toggleRule(it) }
                    )
                    if (showRulesState.value) {
                        RulesDialog(
                            onDismiss = { showRulesState.value = false },
                            onRulesChanged = { onRulesChanged() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationCenter.attach(centerListener)
        evaluateState()
    }

    override fun onPause() {
        super.onPause()
        NotificationCenter.detach(centerListener)
    }

    override fun onDestroy() {
        reviveHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun listenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun evaluateState() {
        permissionState.value = listenerEnabled()
        reviveHandler.removeCallbacksAndMessages(null)
        if (permissionState.value && !NotificationCenter.serviceConnected) {
            reviveHandler.postDelayed({
                if (permissionState.value && !NotificationCenter.serviceConnected) {
                    showRevivePromptState.value = true
                }
            }, 2500)
        } else {
            showRevivePromptState.value = false
        }
    }

    private fun reviveListener() {
        try {
            val cn = ComponentName(this, GuardListenerService::class.java)
            packageManager.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            showRevivePromptState.value = false
            reviveHandler.postDelayed({
                if (NotificationCenter.serviceConnected) {
                    Toast.makeText(this, "监听服务已恢复", Toast.LENGTH_SHORT).show()
                } else {
                    showRevivePromptState.value = true
                    Toast.makeText(
                        this,
                        "未能自动恢复，请点「打开设置」把授权关一次再打开",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 2000)
        } catch (e: Exception) {
            Toast.makeText(this, "修复失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        evaluateState()
        val service = GuardListenerService.instance
        if (service != null) {
            service.reapplyRules()
            Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "监听服务未连接，请确认已授权", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onRulesChanged() {
        rulesVersionState.intValue++
        GuardListenerService.instance?.reapplyRules()
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
            HistoryStore.add(
                packageName = item.packageName,
                title = item.title,
                text = item.text,
                channelId = item.channelId,
                ruleKey = RulesStore.ruleKey(item.packageName, item.channelId),
                auto = false
            )
            syncHistoryVersion()
            Toast.makeText(this, "已隐藏", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "隐藏失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncHistoryVersion() {
        historyVersionState.intValue = HistoryStore.version()
    }

    /**
     * 「恢复」能做的就是撤掉这条记录对应的自动隐藏规则 —— 用户担心的误触正是这个。
     * 已经被 cancelNotification 撤掉的那条通知没有接口能放回通知栏，只能靠历史里的正文查看。
     */
    private fun restoreFromHistory(record: HistoryStore.Record) {
        val hadRule = RulesStore.has(record.ruleKey)
        if (hadRule) {
            RulesStore.remove(record.ruleKey)
            onRulesChanged()
        }
        HistoryStore.markRestored(record.id)
        syncHistoryVersion()
        Toast.makeText(
            this,
            if (hadRule) "已撤销规则，这类通知不再自动隐藏" else "该规则本来就已不存在",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteHistory(record: HistoryStore.Record) {
        HistoryStore.delete(record.id)
        syncHistoryVersion()
    }

    private fun clearHistory() {
        HistoryStore.clearAll()
        syncHistoryVersion()
        Toast.makeText(this, "已清空隐藏历史", Toast.LENGTH_SHORT).show()
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
        onRulesChanged()
    }
}

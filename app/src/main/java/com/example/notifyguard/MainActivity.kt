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
import androidx.core.app.NotificationManagerCompat
import com.example.notifyguard.ui.MainScreen
import com.example.notifyguard.ui.NotifyGuardTheme
import com.example.notifyguard.ui.RulesDialog

class MainActivity : ComponentActivity() {

    private val itemsState = mutableStateOf<List<NotificationItem>>(emptyList())
    private val connectedState = mutableStateOf(false)
    private val permissionState = mutableStateOf(false)
    private val onlyOngoingState = mutableStateOf(false)
    private val showRulesState = mutableStateOf(false)
    private val showRevivePromptState = mutableStateOf(false)
    private val rulesVersionState = mutableIntStateOf(0)

    private val reviveHandler = Handler(Looper.getMainLooper())

    private val centerListener = object : NotificationCenter.Listener {
        override fun onNotifications(items: List<NotificationItem>) {
            itemsState.value = items
        }

        override fun onServiceState(connected: Boolean) {
            connectedState.value = connected
            evaluateState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RulesStore.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            NotifyGuardTheme {
                MainScreen(
                    items = itemsState.value,
                    serviceConnected = connectedState.value,
                    permissionGranted = permissionState.value,
                    showRevivePrompt = showRevivePromptState.value,
                    onlyOngoing = onlyOngoingState.value,
                    rulesVersion = rulesVersionState.intValue,
                    onOnlyOngoingChange = { onlyOngoingState.value = it },
                    onGrantClick = { openListenerSettings() },
                    onReviveClick = { reviveListener() },
                    onOpenSettings = { openListenerSettings() },
                    onRefresh = { refresh() },
                    onShowRules = { showRulesState.value = true },
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
        onRulesChanged()
    }
}

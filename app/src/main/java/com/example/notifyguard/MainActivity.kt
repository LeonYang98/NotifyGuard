package com.example.notifyguard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
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
    private val rulesVersionState = mutableIntStateOf(0)

    private val centerListener = object : NotificationCenter.Listener {
        override fun onNotifications(items: List<NotificationItem>) {
            itemsState.value = items
        }

        override fun onServiceState(connected: Boolean) {
            connectedState.value = connected
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
                    onlyOngoing = onlyOngoingState.value,
                    rulesVersion = rulesVersionState.intValue,
                    onOnlyOngoingChange = { onlyOngoingState.value = it },
                    onGrantClick = { openListenerSettings() },
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
        permissionState.value = listenerEnabled()
    }

    override fun onPause() {
        super.onPause()
        NotificationCenter.detach(centerListener)
    }

    private fun listenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refresh() {
        permissionState.value = listenerEnabled()
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

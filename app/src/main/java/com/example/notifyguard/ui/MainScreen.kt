package com.example.notifyguard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notifyguard.AppInfoCache
import com.example.notifyguard.NotificationItem
import com.example.notifyguard.RulesStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    items: List<NotificationItem>,
    serviceConnected: Boolean,
    permissionGranted: Boolean,
    onlyOngoing: Boolean,
    rulesVersion: Int,
    onOnlyOngoingChange: (Boolean) -> Unit,
    onGrantClick: () -> Unit,
    onRefresh: () -> Unit,
    onShowRules: () -> Unit,
    onCancel: (NotificationItem) -> Unit,
    onChannel: (NotificationItem) -> Unit,
    onRule: (NotificationItem) -> Unit
) {
    val filtered = if (onlyOngoing) items.filter { it.isOngoing } else items
    val ongoingCount = items.count { it.isOngoing }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知哨兵") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onShowRules,
                        modifier = Modifier.weight(1f)
                    ) { Text("自动隐藏规则") }
                    FilledTonalButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f)
                    ) { Text("刷新") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!permissionGranted) {
                PermissionBanner(onGrantClick)
            }
            StatusRow(
                granted = permissionGranted,
                connected = serviceConnected,
                total = items.size,
                ongoing = ongoingCount,
                onlyOngoing = onlyOngoing,
                onOnlyOngoingChange = onOnlyOngoingChange
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!permissionGranted) {
                            "完成授权后，这里会显示当前通知"
                        } else {
                            "暂无通知\n\n有新通知时会自动出现在这里"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.key }) { item ->
                        NotificationRow(
                            item = item,
                            rulesVersion = rulesVersion,
                            onCancel = onCancel,
                            onChannel = onChannel,
                            onRule = onRule
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "需要授权「通知使用权」",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "授权后才能读取并处理各 App 的通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(onClick = onGrant, modifier = Modifier.padding(top = 10.dp)) {
                Text("去授权")
            }
        }
    }
}

@Composable
private fun StatusRow(
    granted: Boolean,
    connected: Boolean,
    total: Int,
    ongoing: Int,
    onlyOngoing: Boolean,
    onOnlyOngoingChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (granted) {
            Text(
                text = if (connected) "监听服务运行中" else "已授权，等待系统启动监听服务…",
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "共 $total 条通知 · 常驻 $ongoing 条",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text("只看常驻", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = onlyOngoing, onCheckedChange = onOnlyOngoingChange)
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    rulesVersion: Int,
    onCancel: (NotificationItem) -> Unit,
    onChannel: (NotificationItem) -> Unit,
    onRule: (NotificationItem) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val icon = remember(item.packageName) { AppInfoCache.bitmap(pm, item.packageName) }
    val appName = remember(item.packageName) { AppInfoCache.appName(pm, item.packageName) }
    val hasRule = remember(rulesVersion, item.packageName, item.channelId) {
        RulesStore.has(item.packageName, item.channelId)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(42.dp)
                    )
                } else {
                    Spacer(Modifier.size(42.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.titleSmall)
                    val content = listOf(item.title, item.text)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                    Text(
                        text = content.ifBlank { "(无标题通知)" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        Text(
                            "渠道: ${item.channelId ?: "无(老应用)"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.isOngoing) {
                            Text(
                                "  ·  常驻不可滑除",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onCancel(item) }) { Text("立即隐藏") }
                TextButton(onClick = { onChannel(item) }) { Text("渠道设置") }
                TextButton(onClick = { onRule(item) }) {
                    Text(if (hasRule) "取消自动" else "自动隐藏")
                }
            }
        }
    }
}

@Composable
fun RulesDialog(onDismiss: () -> Unit, onRulesChanged: () -> Unit) {
    var rules by remember { mutableStateOf(RulesStore.rules()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动隐藏规则 (${rules.size})") },
        text = {
            if (rules.isEmpty()) {
                Text("暂无规则。\n\n在通知条目上点「自动隐藏」即可添加；命中规则的新通知会被自动消除。")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(rules.sorted()) { ruleKey ->
                        val pkg = ruleKey.substringBefore('|')
                        val channel = ruleKey.substringAfter('|').ifEmpty { "(应用级)" }
                        val name = remember(pkg) {
                            AppInfoCache.appName(context.packageManager, pkg)
                        }
                        val stats = RulesStore.statsOf(ruleKey)
                        val suppressed = RulesStore.isSuppressed(ruleKey)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "渠道: $channel · 已消除 ${stats.hits} 次" +
                                        if (suppressed) " · [重发频繁,暂停中]" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                RulesStore.remove(ruleKey)
                                rules = RulesStore.rules()
                                onRulesChanged()
                            }) { Text("删除") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = if (rules.isNotEmpty()) {
            {
                TextButton(onClick = {
                    RulesStore.clearAll()
                    rules = emptySet()
                    onRulesChanged()
                }) { Text("清空全部") }
            }
        } else null
    )
}

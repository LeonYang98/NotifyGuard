package com.example.notifyguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notifyguard.AppInfoCache
import com.example.notifyguard.HistoryStore
import com.example.notifyguard.RulesStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyVersion: Int,
    rulesVersion: Int,
    onBack: () -> Unit,
    onRestore: (HistoryStore.Record) -> Unit,
    onDelete: (HistoryStore.Record) -> Unit,
    onClearAll: () -> Unit
) {
    BackHandler(onBack = onBack)

    val records = remember(historyVersion) { HistoryStore.records() }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("隐藏历史")
                        Text(
                            "共 ${records.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RuleExplainCard()
            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有隐藏记录\n\n被规则自动消除、或你手动点「立即隐藏」的通知都会记在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(records, key = { it.id }) { record ->
                        HistoryRow(
                            record = record,
                            rulesVersion = rulesVersion,
                            timeText = timeFormat.format(Date(record.hiddenAt)),
                            onRestore = onRestore,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空隐藏历史") },
            text = {
                Text(
                    "将删除全部 ${records.size} 条记录。自动隐藏规则不受影响 —— " +
                        "要撤销规则请逐条点「恢复」，或去主页的规则列表里删。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

/** 用户最早的疑问就是「规则到底管多久、误触了怎么办」，直接写在界面上。 */
@Composable
private fun RuleExplainCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            "规则一旦添加就长期有效：之后该应用/渠道的每条新通知到达时都会被消除，直到你撤销它。\n" +
                "点「恢复」= 撤销这条记录对应的规则，以后不再自动隐藏。\n" +
                "已经被消除的那一条通知没法放回通知栏（系统不提供这个接口），但正文都留在这里可以查。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun HistoryRow(
    record: HistoryStore.Record,
    rulesVersion: Int,
    timeText: String,
    onRestore: (HistoryStore.Record) -> Unit,
    onDelete: (HistoryStore.Record) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val icon = remember(record.packageName) { AppInfoCache.bitmap(pm, record.packageName) }
    val appName = remember(record.packageName) { AppInfoCache.appName(pm, record.packageName) }
    val ruleAlive = remember(rulesVersion, record.ruleKey) { RulesStore.has(record.ruleKey) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Text(
                            text = appName.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            appName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            "  $timeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val content = listOf(record.title, record.text)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                    Text(
                        text = content.ifBlank { "(无标题通知)" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(if (record.auto) "自动隐藏" else "手动隐藏")
                            append(" · 渠道: ")
                            append(record.channelId ?: "无(老应用)")
                            if (record.restored) {
                                append(" · 已恢复")
                            } else if (!ruleAlive) {
                                append(" · 规则已撤销")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // 规则已经不在了就没什么可撤的，只留删除记录。
                if (ruleAlive) {
                    TextButton(onClick = { onRestore(record) }) { Text("恢复") }
                }
                TextButton(onClick = { onDelete(record) }) { Text("删除记录") }
            }
        }
    }
}

package com.example.notifyguard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(
    private val onCancel: (NotificationItem) -> Unit,
    private val onChannel: (NotificationItem) -> Unit,
    private val onRule: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private var items: List<NotificationItem> = emptyList()

    fun setItems(newItems: List<NotificationItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val name: TextView = view.findViewById(R.id.tv_app_name)
        val title: TextView = view.findViewById(R.id.tv_title)
        val meta: TextView = view.findViewById(R.id.tv_meta)
        val btnCancel: Button = view.findViewById(R.id.btn_cancel)
        val btnChannel: Button = view.findViewById(R.id.btn_channel)
        val btnRule: Button = view.findViewById(R.id.btn_rule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val pm = context.packageManager

        val icon = AppInfoCache.icon(pm, item.packageName)
        if (icon != null) {
            holder.icon.setImageDrawable(icon)
        } else {
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.name.text = AppInfoCache.appName(pm, item.packageName)

        val content = listOf(item.title, item.text).filter { it.isNotBlank() }.joinToString(" — ")
        holder.title.text = content.ifBlank { "(无标题通知)" }

        val meta = StringBuilder("渠道: ").append(item.channelId ?: "无(老应用)")
        if (item.isOngoing) meta.append("  ·  常驻不可滑除")
        holder.meta.text = meta

        holder.btnRule.text =
            if (RulesStore.has(item.packageName, item.channelId)) "取消自动" else "自动隐藏"

        holder.btnCancel.setOnClickListener { onCancel(item) }
        holder.btnChannel.setOnClickListener { onChannel(item) }
        holder.btnRule.setOnClickListener { onRule(item) }
    }
}

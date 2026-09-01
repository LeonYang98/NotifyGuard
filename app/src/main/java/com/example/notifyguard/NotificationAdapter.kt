package com.example.notifyguard

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(
    private val onHide: (NotificationItem) -> Unit,
    private val onChannel: (NotificationItem) -> Unit,
    private val onRule: (NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationAdapter.Holder>(DIFF) {

    var rules: Set<String> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.ivIcon)
        private val app: TextView = view.findViewById(R.id.tvApp)
        private val badges: TextView = view.findViewById(R.id.tvBadges)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val channel: TextView = view.findViewById(R.id.tvChannel)
        private val hide: Button = view.findViewById(R.id.btnHide)
        private val channelBtn: Button = view.findViewById(R.id.btnChannel)
        private val rule: Button = view.findViewById(R.id.btnRule)

        fun bind(item: NotificationItem) {
            icon.setImageDrawable(AppIcons.get(icon.context, item.pkg))
            app.text = item.appName
            val flags = buildList {
                if (item.ongoing) add("常驻")
                if (rules.contains(item.pkg + "|" + item.channelId) || rules.contains(item.pkg + "|")) {
                    add("自动隐藏中")
                }
            }
            badges.text = flags.joinToString(" · ")
            badges.visibility = if (flags.isEmpty()) View.GONE else View.VISIBLE
            title.text = item.title.ifEmpty { "（无标题）" }
            channel.text = item.pkg + " · 渠道：" + item.channelId.ifEmpty { "无" }
            hide.setOnClickListener { onHide(item) }
            channelBtn.setOnClickListener { onChannel(item) }
            rule.setOnClickListener { onRule(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                oldItem == newItem
        }
    }
}

object AppIcons {
    private val cache = HashMap<String, Drawable>()

    @Synchronized
    fun get(context: Context, pkg: String): Drawable {
        cache[pkg]?.let { return it }
        val drawable = runCatching { context.packageManager.getApplicationIcon(pkg) }
            .getOrElse { context.packageManager.defaultActivityIcon }
        cache[pkg] = drawable
        return drawable
    }
}
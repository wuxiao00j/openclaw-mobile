package com.openclaw.mobile.ui.logs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.openclaw.mobile.databinding.ItemLogBinding
import com.openclaw.mobile.utils.LogManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsAdapter : ListAdapter<LogManager.LogEntry, LogsAdapter.LogViewHolder>(DiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(
        private val binding: ItemLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogManager.LogEntry) {
            binding.apply {
                tvTime.text = timeFormat.format(Date(entry.timestamp))
                tvLevel.text = entry.level.name.first().toString()
                tvTag.text = entry.tag
                tvMessage.text = entry.message

                // 根据级别设置颜色
                val colorRes = when (entry.level) {
                    LogManager.LogLevel.VERBOSE -> android.R.color.darker_gray
                    LogManager.LogLevel.DEBUG -> android.R.color.holo_blue_dark
                    LogManager.LogLevel.INFO -> android.R.color.holo_green_dark
                    LogManager.LogLevel.WARN -> android.R.color.holo_orange_dark
                    LogManager.LogLevel.ERROR -> android.R.color.holo_red_dark
                }
                tvLevel.setTextColor(itemView.context.getColor(colorRes))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LogManager.LogEntry>() {
        override fun areItemsTheSame(
            oldItem: LogManager.LogEntry,
            newItem: LogManager.LogEntry
        ): Boolean {
            return oldItem.timestamp == newItem.timestamp && 
                   oldItem.message == newItem.message
        }

        override fun areContentsTheSame(
            oldItem: LogManager.LogEntry,
            newItem: LogManager.LogEntry
        ): Boolean {
            return oldItem == newItem
        }
    }
}

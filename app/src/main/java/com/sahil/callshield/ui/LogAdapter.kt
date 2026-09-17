package com.sahil.callshield.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sahil.callshield.R
import com.sahil.callshield.data.CallLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : ListAdapter<CallLogEntity, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view, dateFormat)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(
        itemView: View,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvLogIcon: TextView = itemView.findViewById(R.id.tvLogIcon)
        private val tvLogNumber: TextView = itemView.findViewById(R.id.tvLogNumber)
        private val tvLogDecision: TextView = itemView.findViewById(R.id.tvLogDecision)
        private val tvLogReason: TextView = itemView.findViewById(R.id.tvLogReason)
        private val tvLogTime: TextView = itemView.findViewById(R.id.tvLogTime)

        fun bind(log: CallLogEntity) {
            tvLogNumber.text = log.number.ifEmpty { "Private/Unknown Number" }
            tvLogReason.text = log.reason
            tvLogTime.text = dateFormat.format(Date(log.timestamp))

            when (log.decision) {
                "silenced" -> {
                    tvLogIcon.text = "🔕"
                    tvLogDecision.text = "SILENCED"
                    tvLogDecision.setTextColor("#F59E0B".toColorInt())
                    tvLogDecision.setBackgroundColor("#451A03".toColorInt())
                }
                "allowed" -> {
                    tvLogIcon.text = "✅"
                    tvLogDecision.text = "ALLOWED"
                    tvLogDecision.setTextColor("#10B981".toColorInt())
                    tvLogDecision.setBackgroundColor("#064E3B".toColorInt())
                }
                else -> {
                    tvLogIcon.text = "🚫"
                    tvLogDecision.text = "BLOCKED"
                    tvLogDecision.setTextColor("#EF4444".toColorInt())
                    tvLogDecision.setBackgroundColor("#450A0A".toColorInt())
                }
            }
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<CallLogEntity>() {
        override fun areItemsTheSame(oldItem: CallLogEntity, newItem: CallLogEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallLogEntity, newItem: CallLogEntity): Boolean {
            return oldItem == newItem
        }
    }
}

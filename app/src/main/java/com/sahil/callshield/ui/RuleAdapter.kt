package com.sahil.callshield.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sahil.callshield.R
import com.sahil.callshield.data.BlockRuleEntity

class RuleAdapter(
    private val onToggle: (BlockRuleEntity, Boolean) -> Unit,
    private val onDelete: (BlockRuleEntity) -> Unit
) : ListAdapter<BlockRuleEntity, RuleAdapter.RuleViewHolder>(RuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return RuleViewHolder(view, onToggle, onDelete)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RuleViewHolder(
        itemView: View,
        private val onToggle: (BlockRuleEntity, Boolean) -> Unit,
        private val onDelete: (BlockRuleEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvRuleFlag: TextView = itemView.findViewById(R.id.tvRuleFlag)
        private val tvRuleName: TextView = itemView.findViewById(R.id.tvRuleName)
        private val tvRulePattern: TextView = itemView.findViewById(R.id.tvRulePattern)
        private val tvRuleAction: TextView = itemView.findViewById(R.id.tvRuleAction)
        private val switchRuleEnabled: SwitchCompat = itemView.findViewById(R.id.switchRuleEnabled)
        private val btnDeleteRule: ImageButton = itemView.findViewById(R.id.btnDeleteRule)

        fun bind(rule: BlockRuleEntity) {
            tvRuleFlag.text = rule.flag ?: "🌐"
            tvRuleName.text = rule.name
            tvRulePattern.text = rule.pattern

            if (rule.action == "silence") {
                tvRuleAction.text = "SILENCE"
                tvRuleAction.setTextColor("#F59E0B".toColorInt())
                tvRuleAction.setBackgroundColor("#451A03".toColorInt())
            } else {
                tvRuleAction.text = "BLOCK"
                tvRuleAction.setTextColor("#EF4444".toColorInt())
                tvRuleAction.setBackgroundColor("#450A0A".toColorInt())
            }

            // Remove listener before setting checked state to avoid accidental trigger
            switchRuleEnabled.setOnCheckedChangeListener(null)
            switchRuleEnabled.isChecked = rule.enabled
            switchRuleEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(rule, isChecked)
            }

            btnDeleteRule.setOnClickListener {
                onDelete(rule)
            }
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<BlockRuleEntity>() {
        override fun areItemsTheSame(oldItem: BlockRuleEntity, newItem: BlockRuleEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BlockRuleEntity, newItem: BlockRuleEntity): Boolean {
            return oldItem == newItem
        }
    }
}

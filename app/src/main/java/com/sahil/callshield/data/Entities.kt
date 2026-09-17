package com.sahil.callshield.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_rules")
data class BlockRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val pattern: String,
    val type: String, // "prefix", "country", "wildcard", "exact"
    val action: String, // "block" or "silence"
    val enabled: Boolean,
    val skipCallLog: Boolean = true,
    val skipNotification: Boolean = false,
    val countryCode: String? = null,
    val flag: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val decision: String, // "blocked", "silenced", "allowed"
    val reason: String,
    val ruleId: String?,
    val timestamp: Long = System.currentTimeMillis()
)

package com.sahil.callshield.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM block_rules WHERE enabled = 1")
    suspend fun getActiveRules(): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules ORDER BY createdAt DESC")
    suspend fun getAllRulesList(): List<BlockRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<BlockRuleEntity>)

    @Delete
    suspend fun deleteRule(rule: BlockRuleEntity)

    @Query("DELETE FROM block_rules")
    suspend fun deleteAllRules()

    @Query("DELETE FROM block_rules WHERE id LIKE 'p-%'")
    suspend fun deleteLegacyPreloadedRules()

    @Query("UPDATE block_rules SET enabled = :enabled WHERE id = :id")
    suspend fun toggleRule(id: String, enabled: Boolean)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<CallLogEntity>>

    @Query("INSERT INTO activity_logs (number, decision, reason, ruleId, timestamp) VALUES (:rawNumber, :decision, :reason, :ruleId, :time)")
    suspend fun recordEventInternal(rawNumber: String, decision: String, reason: String, ruleId: String?, time: Long)

    suspend fun recordEvent(rawNumber: String, decision: String, reason: String, ruleId: String?) {
        recordEventInternal(rawNumber, decision, reason, ruleId, System.currentTimeMillis())
    }

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()
}

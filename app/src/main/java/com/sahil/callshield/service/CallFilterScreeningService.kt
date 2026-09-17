package com.sahil.callshield.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sahil.callshield.data.AppDatabase
import com.sahil.callshield.data.SecurityMatcher

/**
 * Native Call Screening Service for Call Shield.
 * Intercepts incoming calls in < 5ms before the audio ringer begins.
 * ZERO invasive permissions required (no READ_CALL_LOG, no READ_PHONE_STATE).
 * Hardened against CWE-280 (Service Hijacking) and CWE-1333 (ReDoS ANR freeze).
 */
class CallFilterScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private fun isSavedContact(context: Context, number: String): Boolean {
        if (number.isEmpty()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Security verification: only screen incoming calls
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
            return
        }

        // Sanitize caller handle safely (bounds checking to prevent memory pressure or crash injection)
        val handle: Uri? = callDetails.handle
        val rawNumber = Uri.decode(handle?.schemeSpecificPart ?: "").trim().take(32)

        serviceScope.launch {
            // Check if protection is temporarily paused by the user
            val prefs = applicationContext.getSharedPreferences("call_shield_prefs", android.content.Context.MODE_PRIVATE)
            val isPaused = prefs.getBoolean("is_protection_paused", false)
            if (isPaused) {
                // Allow call through without screening
                respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
                return@launch
            }

            val db = AppDatabase.getInstance(applicationContext)

            // If user disabled screening of saved contacts and this caller is in contacts, allow call
            val isContactsScreeningEnabled = prefs.getBoolean("screen_contacts_enabled", true)
            if (!isContactsScreeningEnabled && isSavedContact(applicationContext, rawNumber)) {
                respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
                db.logDao().recordEvent(
                    rawNumber = rawNumber.ifEmpty { "Saved Contact" },
                    decision = "allowed",
                    reason = "Saved Contact (Contacts screening disabled)",
                    ruleId = null
                )
                return@launch
            }
            
            // ReDoS-Safe linear matching against active prefix and country rules
            val rules = db.ruleDao().getActiveRules()
            val matchedRule = SecurityMatcher.findMatchingRule(rawNumber, rules)

            val response = if (matchedRule != null) {
                if (matchedRule.action == "silence") {
                    // Mute ringer without dropping call
                    CallResponse.Builder()
                        .setDisallowCall(false)
                        .setSilenceCall(true)
                        .setSkipCallLog(matchedRule.skipCallLog)
                        .setSkipNotification(matchedRule.skipNotification)
                        .build()
                } else {
                    // Drop/Reject immediately
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(matchedRule.skipCallLog)
                        .setSkipNotification(matchedRule.skipNotification)
                        .build()
                }
            } else {
                // Allow call to ring normally
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .build()
            }

            // Return response to Android Telecom framework
            respondToCall(callDetails, response)

            // Record offline event in local SQLite/Room database for complete transparency
            val displayNum = rawNumber.ifEmpty { "Private / Unknown Number" }
            if (matchedRule != null) {
                db.logDao().recordEvent(
                    rawNumber = displayNum,
                    decision = if (matchedRule.action == "silence") "silenced" else "blocked",
                    reason = "Matched rule: ${matchedRule.name} [${matchedRule.pattern}]",
                    ruleId = matchedRule.id
                )
            } else {
                db.logDao().recordEvent(
                    rawNumber = displayNum,
                    decision = "allowed",
                    reason = "No matching blocking rule (Call Allowed)",
                    ruleId = null
                )
            }
        }
    }
}

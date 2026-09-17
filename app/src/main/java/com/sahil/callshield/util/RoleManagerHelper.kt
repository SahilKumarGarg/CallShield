package com.sahil.callshield.util

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object RoleManagerHelper {
    const val ROLE_CALL_SCREENING = RoleManager.ROLE_CALL_SCREENING

    /**
     * Checks if the app currently holds the Call Screening role.
     */
    fun isCallScreeningRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }
        return false
    }

    /**
     * Creates intent to prompt the system role dialog (available on Android 10+).
     */
    fun createRequestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null) {
                try {
                    // Check availability first if reported by OS, or attempt intent creation directly
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    }
                } catch (_: Exception) {
                    // OEM specific exception fallback
                }
                try {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                } catch (_: Exception) {
                    // System does not support RoleManager intent; will use settings fallback
                }
            }
        }
        return null
    }

    /**
     * Opens Android System Settings directly to Default Apps screen or the App Details
     * so user can immediately enable "Caller ID & spam app" / "Call screening app".
     */
    fun openDefaultAppsOrSettings(context: Context) {
        val intentsToTry = mutableListOf<Intent>()

        // 1. Direct Default Apps settings intent (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intentsToTry.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }

        // 2. Specific manufacturer intent fallback (e.g. Samsung / Xiaomi)
        try {
            intentsToTry.add(Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$ManageDefaultAppsActivity"
                )
            })
        } catch (_: Exception) {}

        // 3. Application Details Settings for this app
        intentsToTry.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })

        // 4. Fallback: Generic settings
        intentsToTry.add(Intent(Settings.ACTION_SETTINGS))

        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Try next fallback
            }
        }
    }
}

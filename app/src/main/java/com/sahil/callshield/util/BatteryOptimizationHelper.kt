package com.sahil.callshield.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    /**
     * Attempts to request battery optimization exemption using the system dialog,
     * with multi-stage fallback to manage battery / app detail settings.
     */
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        // If not ignored yet, try standard REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        if (!isBatteryOptimizationIgnored(context)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, trying settings", e)
            }
        }

        // Fallback or explicit settings inspection
        return openBatterySettings(context)
    }

    /**
     * Robust multi-tier settings opener that works across stock Android, Samsung OneUI,
     * Xiaomi MIUI/HyperOS, Oppo/Realme ColorOS, Vivo OriginOS/Funtouch, and OnePlus OxygenOS.
     */
    fun openBatterySettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return openAppDetails(context)
        }

        val intentsToTry = mutableListOf<Intent>()

        // 1. Android M+ Manage Ignore Battery Optimizations list
        try {
            intentsToTry.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}

        // 2. OEM Specific Battery & Autostart Activities
        val oemComponents = listOf(
            // Xiaomi / HyperOS Autostart
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            // Huawei / Honor Protected Apps
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            // Samsung Device Care / Battery
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            // Oppo / Realme
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // Vivo
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        )

        for (comp in oemComponents) {
            try {
                intentsToTry.add(Intent().apply {
                    component = comp
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }

        // 3. Application Details Settings (guaranteed to exist on all Android platforms)
        try {
            intentsToTry.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}

        // 4. Generic App settings or main system settings
        try {
            intentsToTry.add(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}

        for (intent in intentsToTry) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try next
            } catch (_: SecurityException) {
                // Try next
            } catch (_: Exception) {
                // Try next
            }
        }

        return false
    }

    /**
     * Fallback to direct application details page where users can tap 'Battery' -> 'Unrestricted'.
     */
    fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}

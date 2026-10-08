package com.radardecorridas.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.radardecorridas.app.service.RideScannerAccessibilityService

object DiagnosticHelper {

    data class DiagnosticStatus(
        val hasOverlayPermission: Boolean,
        val isAccessibilityEnabledInSystem: Boolean,
        val isServiceConnected: Boolean,
        val monitoredPackages: List<String>,
        val lastEventPackage: String?,
        val lastEventTimestamp: Long,
        val lastEventTextSnippet: String?,
        val totalEventsReceived: Long
    )

    fun checkStatus(context: Context): DiagnosticStatus {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val isAccEnabled = isAccessibilityServiceEnabled(context)
        val isConnected = RideScannerAccessibilityService.isConnected
        val monitored = RideScannerAccessibilityService.SUPPORTED_PACKAGES.toList()
        val lastPkg = RideScannerAccessibilityService.lastEventPackage
        val lastTs = RideScannerAccessibilityService.lastEventTimestamp
        val lastSnippet = RideScannerAccessibilityService.lastEventSnippet
        val totalEvents = RideScannerAccessibilityService.totalEventsReceived

        return DiagnosticStatus(
            hasOverlayPermission = hasOverlay,
            isAccessibilityEnabledInSystem = isAccEnabled,
            isServiceConnected = isConnected,
            monitoredPackages = monitored,
            lastEventPackage = lastPkg,
            lastEventTimestamp = lastTs,
            lastEventTextSnippet = lastSnippet,
            totalEventsReceived = totalEvents
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false

        val expectedServiceName = RideScannerAccessibilityService::class.java.name

        // 1. Testa via getEnabledAccessibilityServiceList
        try {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    val serviceInfo = service.resolveInfo?.serviceInfo
                    if (serviceInfo != null) {
                        if (serviceInfo.packageName == context.packageName &&
                            (serviceInfo.name == expectedServiceName || serviceInfo.name.endsWith("RideScannerAccessibilityService"))
                        ) {
                            return true
                        }
                    }
                    if (service.id.contains(context.packageName) &&
                        service.id.contains("RideScannerAccessibilityService")
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Consulta alternativa via Settings.Secure
        try {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val fullExpectedId = "${context.packageName}/$expectedServiceName"
                val shortExpectedId = "${context.packageName}/.service.RideScannerAccessibilityService"
                if (settingValue.contains(fullExpectedId) ||
                    settingValue.contains(shortExpectedId) ||
                    (settingValue.contains(context.packageName) && settingValue.contains("RideScannerAccessibilityService"))
                ) {
                    return true
                }
            }
        } catch (_: Exception) {
        }

        return false
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}

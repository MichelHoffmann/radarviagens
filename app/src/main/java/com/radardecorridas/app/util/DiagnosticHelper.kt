package com.radardecorridas.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityManager
import com.radardecorridas.app.service.RideScannerAccessibilityService
import java.util.Date

data class AccessibilityEventLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long,
    val packageName: String,
    val eventType: String,
    val eventTextSnippet: String?,
    val nodeCount: Int,
    val treeTextSnippet: String,
    val parserVerdict: String,
    val isTargetApp: Boolean
)

object DiagnosticHelper {

    data class DiagnosticStatus(
        val hasOverlayPermission: Boolean,
        val isAccessibilityEnabledInSystem: Boolean,
        val isServiceConnected: Boolean,
        val monitoredPackages: List<String>,
        val lastEventPackage: String?,
        val lastEventTimestamp: Long,
        val lastEventTextSnippet: String?,
        val totalEventsReceived: Long,
        val totalRawEvents: Long,
        val lastSeenPackage: String?,
        val recentLogs: List<AccessibilityEventLog>
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
        val totalRaw = RideScannerAccessibilityService.totalRawEvents
        val lastSeen = RideScannerAccessibilityService.lastSeenPackage
        val recent = RideScannerAccessibilityService.getRecentLogs()

        return DiagnosticStatus(
            hasOverlayPermission = hasOverlay,
            isAccessibilityEnabledInSystem = isAccEnabled,
            isServiceConnected = isConnected,
            monitoredPackages = monitored,
            lastEventPackage = lastPkg,
            lastEventTimestamp = lastTs,
            lastEventTextSnippet = lastSnippet,
            totalEventsReceived = totalEvents,
            totalRawEvents = totalRaw,
            lastSeenPackage = lastSeen,
            recentLogs = recent
        )
    }

    fun clearLogs() {
        RideScannerAccessibilityService.clearLogs()
    }

    fun copyDiagnosticsToClipboard(context: Context, status: DiagnosticStatus): Boolean {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false

            val sb = java.lang.StringBuilder()
            sb.append("=== DIAGNÓSTICO RADAR DE CORRIDAS ===\n")
            sb.append("Data/Hora: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", Date())}\n")
            sb.append("Sobreposição (SYSTEM_ALERT_WINDOW): ${if (status.hasOverlayPermission) "OK" else "NEGADA"}\n")
            sb.append("Acessibilidade Habilitada no Android: ${if (status.isAccessibilityEnabledInSystem) "SIM" else "NÃO"}\n")
            sb.append("Serviço Conectado no Processo: ${if (status.isServiceConnected) "SIM" else "NÃO"}\n")
            sb.append("Total de eventos globais recebidos: ${status.totalRawEvents}\n")
            sb.append("Total de eventos de corridas (99/Uber): ${status.totalEventsReceived}\n")
            sb.append("Último pacote detectado: ${status.lastSeenPackage ?: "Nenhum"}\n")
            sb.append("Último pacote de corrida: ${status.lastEventPackage ?: "Nenhum"}\n\n")

            sb.append("--- LOGS DETALHADOS RECENTES (${status.recentLogs.size}) ---\n")
            if (status.recentLogs.isEmpty()) {
                sb.append("Nenhum log registrado ainda.\n")
            } else {
                status.recentLogs.forEachIndexed { idx, log ->
                    val timeStr = DateFormat.format("HH:mm:ss", Date(log.timestamp))
                    sb.append("[#${idx + 1}] $timeStr | Pkg: ${log.packageName} | Tipo: ${log.eventType}\n")
                    if (!log.eventTextSnippet.isNullOrBlank()) {
                        sb.append("    EventText: ${log.eventTextSnippet}\n")
                    }
                    sb.append("    Nós lidos: ${log.nodeCount} nós\n")
                    sb.append("    Trecho da árvore: ${log.treeTextSnippet}\n")
                    sb.append("    Status Parser: ${log.parserVerdict}\n\n")
                }
            }

            val clip = ClipData.newPlainText("Diagnóstico Radar", sb.toString())
            cm.setPrimaryClip(clip)
            return true
        } catch (_: Exception) {
            return false
        }
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

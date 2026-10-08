package com.radardecorridas.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.util.OcrParser
import com.radardecorridas.app.util.RideCalculator

class RideScannerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastScannedHash = 0
    private var lastScanTimestamp = 0L

    companion object {
        private const val TAG = "RideScannerService"
        val SUPPORTED_PACKAGES = arrayOf(
            "com.ubercab.driver",
            "com.ubercab",
            "com.taxis99.driver",
            "com.taxis99",
            "com.didiglobal.driver",
            "com.didichuxing.driver",
            "sinet.startup.inDriver"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = SUPPORTED_PACKAGES
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info
        Log.d(TAG, "RideScannerAccessibilityService conectado e monitorando pacotes: ${SUPPORTED_PACKAGES.joinToString()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Por padrão o Radar fica DESATIVADO até o usuário tocar em 'Ativar Radar'
        val isEnabled = prefs.getBoolean("is_enabled", false)
        if (!isEnabled || event == null) return

        val pkgName = event.packageName?.toString() ?: return
        if (SUPPORTED_PACKAGES.none { pkgName.contains(it, ignoreCase = true) || it.contains(pkgName, ignoreCase = true) }) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastScanTimestamp < 350) return

        val rootNode = event.source ?: rootInActiveWindow ?: return
        val allTexts = mutableListOf<String>()

        // Adiciona textos do evento diretamente
        event.text?.forEach { charSeq ->
            val textStr = charSeq.toString().trim()
            if (textStr.isNotEmpty()) allTexts.add(textStr)
        }

        extractAllTexts(rootNode, allTexts)

        val fullText = allTexts.joinToString(" ")
        val currentHash = fullText.hashCode()
        if (currentHash == lastScannedHash) return

        val parsedRide = OcrParser.parseRideText(fullText) ?: return

        lastScannedHash = currentHash
        lastScanTimestamp = now

        Log.d(TAG, "Corrida identificada com sucesso via acessibilidade na tela de $pkgName: R$${parsedRide.price}, ${parsedRide.totalDistanceKm}km")

        // Lê metas configuradas pelo motorista
        val minKm = prefs.getFloat("min_price_per_km", 2.00f).toDouble()
        val minHour = prefs.getFloat("min_price_per_hour", 35.00f).toDouble()
        val fuelPrice = prefs.getFloat("fuel_price", 5.85f).toDouble()
        val consumption = prefs.getFloat("vehicle_consumption", 11.5f).toDouble()

        val settings = DriverSettings(
            isEnabled = true,
            minPricePerKm = minKm,
            minPricePerHour = minHour,
            fuelPricePerLiter = fuelPrice,
            vehicleConsumptionKmPerLiter = consumption
        )

        val evaluation = RideCalculator.evaluateRide(parsedRide, settings)

        val appLabel = when {
            pkgName.contains("uber", ignoreCase = true) -> "Uber"
            pkgName.contains("99", ignoreCase = true) || pkgName.contains("didi", ignoreCase = true) -> "99"
            pkgName.contains("indrive", ignoreCase = true) -> "inDrive"
            else -> parsedRide.app.replaceFirstChar { it.uppercase() }
        }

        // Aciona o Pop-up Flutuante com o nível correspondente (Verde, Amarelo ou Vermelho)
        val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_SHOW_POPUP
            putExtra("APP_NAME", appLabel)
            putExtra("PRICE", evaluation.ride.price)
            putExtra("TOTAL_KM", evaluation.ride.totalDistanceKm)
            putExtra("TOTAL_MIN", evaluation.ride.totalDurationMin)
            putExtra("PRICE_PER_KM", evaluation.pricePerKm)
            putExtra("PRICE_PER_HOUR", evaluation.pricePerHour)
            putExtra("VERDICT", evaluation.verdict.name)
        }
        startService(overlayIntent)
    }

    private fun extractAllTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.let {
            if (it.isNotEmpty()) texts.add(it)
        }
        node.contentDescription?.toString()?.trim()?.let {
            if (it.isNotEmpty()) texts.add(it)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractAllTexts(child, texts)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "RideScannerAccessibilityService interrompido.")
    }
}

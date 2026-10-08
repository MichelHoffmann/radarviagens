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
            "com.didichuxing.passenger",
            "sinet.startup.inDriver"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            packageNames = SUPPORTED_PACKAGES
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 80
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info
        Log.i(TAG, "RideScannerAccessibilityService conectado. Monitorando pacotes: ${SUPPORTED_PACKAGES.joinToString()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val isEnabled = prefs.getBoolean("is_enabled", false)
        if (!isEnabled || event == null) return

        val pkgName = event.packageName?.toString() ?: return
        val isMatch = SUPPORTED_PACKAGES.any {
            pkgName.equals(it, ignoreCase = true) ||
            pkgName.contains(it, ignoreCase = true) ||
            it.contains(pkgName, ignoreCase = true)
        }
        if (!isMatch) return

        val now = System.currentTimeMillis()
        if (now - lastScanTimestamp < 250) return

        val allTexts = LinkedHashSet<String>()

        // 1. Captura textos do próprio evento (ex: toasters, alerts, accessibility announcements)
        event.text?.forEach { charSeq ->
            val textStr = charSeq.toString().trim()
            if (textStr.isNotEmpty()) allTexts.add(textStr)
        }

        // 2. Extrai nós da árvore a partir de event.source
        event.source?.let { sourceNode ->
            extractAllTexts(sourceNode, allTexts)
        }

        // 3. Extrai nós de rootInActiveWindow
        rootInActiveWindow?.let { rootNode ->
            extractAllTexts(rootNode, allTexts)
        }

        // 4. Se ainda tiver poucos dados ou janela flutuante, percorre todas as interactive windows
        try {
            windows?.forEach { window ->
                window.root?.let { windowRoot ->
                    extractAllTexts(windowRoot, allTexts)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro ao iterar windows: ${e.message}")
        }

        if (allTexts.isEmpty()) return

        val fullText = allTexts.joinToString(" ")
        val currentHash = fullText.hashCode()
        if (currentHash == lastScannedHash) return

        // Log detalhado de diagnóstico para confirmar o que chega ao serviço
        Log.d(TAG, "[DIAGNOSTICO] Evento recebido de $pkgName (tipo=${event.eventType}). Textos capturados (${allTexts.size}): $fullText")

        val parsedRide = OcrParser.parseRideText(fullText)
        if (parsedRide == null) {
            Log.v(TAG, "[DIAGNOSTICO] Textos analisados, mas nenhuma oferta completa reconhecida ainda.")
            return
        }

        lastScannedHash = currentHash
        lastScanTimestamp = now

        Log.i(TAG, "[SUCESSO] Oferta detectada com sucesso! App: ${parsedRide.app}, Valor: R$${parsedRide.price}, Distância: ${parsedRide.totalDistanceKm}km, Tempo: ${parsedRide.totalDurationMin}min")

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

        // Exibe o Pop-up informativo exclusivamente visual (Verde, Amarelo ou Vermelho)
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

    private fun extractAllTexts(node: AccessibilityNodeInfo?, texts: MutableSet<String>) {
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

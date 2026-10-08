package com.radardecorridas.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideData
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.util.OcrParser
import com.radardecorridas.app.util.RideCalculator

class RideScannerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastScannedHash = 0
    private var lastScanTimestamp = 0L

    companion object {
        const val UBER_PACKAGE = "com.ubercab.driver"
        const val NINETEEN_NINE_PACKAGE = "com.taxis99"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = arrayOf(UBER_PACKAGE, NINETEEN_NINE_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val isEnabled = prefs.getBoolean("is_enabled", true)
        if (!isEnabled || event == null) return

        val pkgName = event.packageName?.toString() ?: return
        if (pkgName != UBER_PACKAGE && pkgName != NINETEEN_NINE_PACKAGE) return

        val now = System.currentTimeMillis()
        if (now - lastScanTimestamp < 300) return

        val rootNode = rootInActiveWindow ?: return
        val allTexts = mutableListOf<String>()
        extractAllTexts(rootNode, allTexts)

        val fullText = allTexts.joinToString(" ")
        val currentHash = fullText.hashCode()
        if (currentHash == lastScannedHash) return
        lastScannedHash = currentHash
        lastScanTimestamp = now

        val parsedRide = OcrParser.parseRideText(fullText) ?: return

        // Lê metas salvas
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

        // Aciona o Pop-up Flutuante com o nível correspondente
        val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("APP_NAME", if (pkgName == UBER_PACKAGE) "Uber" else "99")
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
            extractAllTexts(node.getChild(i), texts)
        }
    }

    override fun onInterrupt() {}
}

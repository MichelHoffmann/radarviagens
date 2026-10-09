package com.radardecorridas.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.util.AccessibilityEventLog
import com.radardecorridas.app.util.OcrParser
import com.radardecorridas.app.util.RideCalculator
import java.util.Collections
import java.util.LinkedList

class RideScannerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastScannedHash = 0
    private var lastScanTimestamp = 0L

    companion object {
        private const val TAG = "RideScannerService"

        val SUPPORTED_PACKAGES = arrayOf(
            "com.taxis99.driver",
            "com.taxis99",
            "com.didiglobal.driver",
            "com.didiglobal.motorista",
            "com.didiglobal.passenger",
            "com.didichuxing.driver",
            "com.didichuxing.passenger",
            "com.didi.global.driver",
            "com.didi.brazil.driver",
            "com.xiaojukeji.didi.brazil.driver",
            "com.xiaojukeji.didi.brazil.customer",
            "com.ubercab.driver",
            "com.ubercab",
            "sinet.startup.inDriver",
            "com.indrive"
        )

        // Variáveis diagnósticas em tempo real
        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        var lastEventPackage: String? = null
            private set

        @Volatile
        var lastEventTimestamp: Long = 0L
            private set

        @Volatile
        var lastEventSnippet: String? = null
            private set

        @Volatile
        var totalEventsReceived: Long = 0L
            private set

        @Volatile
        var totalRawEvents: Long = 0L
            private set

        @Volatile
        var lastSeenPackage: String? = null
            private set

        private val _eventLogs = Collections.synchronizedList(LinkedList<AccessibilityEventLog>())

        fun getRecentLogs(): List<AccessibilityEventLog> {
            return synchronized(_eventLogs) {
                _eventLogs.toList()
            }
        }

        fun clearLogs() {
            synchronized(_eventLogs) {
                _eventLogs.clear()
            }
        }

        fun addLog(log: AccessibilityEventLog) {
            synchronized(_eventLogs) {
                if (_eventLogs.size >= 30) {
                    _eventLogs.removeFirst()
                }
                _eventLogs.add(log)
            }
        }

        fun isTargetPackage(pkg: String): Boolean {
            val lower = pkg.lowercase()
            return lower.contains("99") ||
                    lower.contains("didi") ||
                    lower.contains("xiaojukeji") ||
                    lower.contains("uber") ||
                    lower.contains("indriver") ||
                    lower.contains("indrive")
        }

        fun formatEventType(type: Int): String {
            return when (type) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFICATION_STATE_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
                AccessibilityEvent.TYPE_ANNOUNCEMENT -> "ANNOUNCEMENT"
                AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> "ASSIST_READING_CONTEXT"
                else -> "TIPO_$type"
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        try {
            prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar SharedPreferences em onServiceConnected", e)
        }
        Log.i(TAG, "RideScannerAccessibilityService conectado com sucesso.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val pkgName = event.packageName?.toString() ?: return

            // Verifica se é aplicativo alvo monitorado
            if (!isTargetPackage(pkgName)) return

            // Registra telemetria de diagnóstico em tempo real
            totalRawEvents++
            totalEventsReceived++
            lastSeenPackage = pkgName
            lastEventPackage = pkgName
            lastEventTimestamp = System.currentTimeMillis()

            val eventTypeName = formatEventType(event.eventType)

            // Coleta textos do evento e da hierarquia da janela
            val allTexts = LinkedHashSet<String>()

            // 1. Textos diretos da notificação ou do evento
            event.text?.forEach { charSeq ->
                val str = charSeq?.toString()?.trim()
                if (!str.isNullOrEmpty()) allTexts.add(str)
            }

            // 2. Nós da janela ativa ou fonte do evento
            val rootNode = try {
                rootInActiveWindow ?: event.source
            } catch (e: Exception) {
                event.source
            }

            if (rootNode != null) {
                try {
                    extractAllTexts(rootNode, allTexts, depth = 0, maxNodes = 100)
                } catch (e: Exception) {
                    Log.d(TAG, "Erro ao extrair nós: ${e.message}")
                }
            }

            val fullText = allTexts.joinToString(" ")
            lastEventSnippet = if (fullText.length > 120) fullText.take(120) + "..." else fullText

            // Análise com o parser de OCR
            val parsedRide = if (allTexts.isNotEmpty()) OcrParser.parseRideText(fullText) else null

            val parserStatus = when {
                parsedRide != null -> "✅ OFERTA DETECTADA (R$${parsedRide.price} | ${parsedRide.totalDistanceKm}km | ${parsedRide.totalDurationMin}min)"
                allTexts.isEmpty() -> "⚠️ Árvore de acessibilidade sem textos legíveis"
                fullText.contains("R$") || fullText.contains("aceitar", ignoreCase = true) -> "⚠️ Contém R$/Aceitar, mas campos incompletos"
                else -> "ℹ️ Tela do app monitorado (sem oferta completa)"
            }

            // Adiciona no histórico de diagnósticos para a interface
            val logEntry = AccessibilityEventLog(
                timestamp = System.currentTimeMillis(),
                packageName = pkgName,
                eventType = eventTypeName,
                eventTextSnippet = if (allTexts.isNotEmpty()) allTexts.first().take(80) else null,
                nodeCount = allTexts.size,
                treeTextSnippet = if (fullText.length > 160) fullText.take(160) + "..." else fullText,
                parserVerdict = parserStatus,
                isTargetApp = true
            )
            addLog(logEntry)

            // Se o Radar estiver desativado pelo usuário na tela Home, não exibe pop-up
            val isEnabled = if (::prefs.isInitialized) prefs.getBoolean("is_enabled", false) else false
            if (!isEnabled) {
                return
            }

            // Se nenhuma oferta válida foi encontrada, encerra
            if (parsedRide == null) return

            // Evita popups repetidos para a mesma oferta
            val currentHash = "${parsedRide.price}_${parsedRide.totalDistanceKm}_${parsedRide.totalDurationMin}".hashCode()
            val now = System.currentTimeMillis()
            if (currentHash == lastScannedHash && now - lastScanTimestamp < 8000) {
                return
            }
            lastScannedHash = currentHash
            lastScanTimestamp = now

            // Lê metas configuradas
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

            // Dispara popup flutuante
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
        } catch (t: Throwable) {
            Log.e(TAG, "Falha segura ao processar evento de acessibilidade", t)
        }
    }

    private fun extractAllTexts(
        node: AccessibilityNodeInfo?,
        texts: MutableSet<String>,
        depth: Int = 0,
        maxNodes: Int = 100
    ) {
        if (node == null || depth > 15 || texts.size >= maxNodes) return

        try {
            node.text?.toString()?.trim()?.let {
                if (it.isNotEmpty()) texts.add(it)
            }
            node.contentDescription?.toString()?.trim()?.let {
                if (it.isNotEmpty()) texts.add(it)
            }
            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (texts.size >= maxNodes) break
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (child != null) {
                    extractAllTexts(child, texts, depth + 1, maxNodes)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro em extractAllTexts: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "RideScannerAccessibilityService interrompido.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        Log.d(TAG, "RideScannerAccessibilityService desconectado (onUnbind).")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        Log.d(TAG, "RideScannerAccessibilityService destruído.")
        super.onDestroy()
    }
}

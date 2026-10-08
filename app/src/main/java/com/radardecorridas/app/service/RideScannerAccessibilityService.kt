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
        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        // Configuração dinâmica da Acessibilidade
        val info = AccessibilityServiceInfo().apply {
            // Monitora TODOS os tipos de eventos para não perder alertas, popups ou notificações
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK

            // CRUCIAL: packageNames = null faz o Android entregar eventos de qualquer app
            // Isso garante que se a 99 usar um package ligeiramente diferente no aparelho,
            // ou se a oferta vier por janela flutuante/sistema, o evento NUNCA será descartado pelo Android!
            packageNames = null

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info
        Log.i(TAG, "RideScannerAccessibilityService conectado. Monitoramento total ativado (packageNames=null, eventTypes=ALL).")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Contador bruto de qualquer evento recebido pelo Android (comprova que o serviço está recebendo eventos do OS)
        totalRawEvents++

        val pkgName = event.packageName?.toString() ?: "desconhecido"
        lastSeenPackage = pkgName

        // Verifica se é pacote alvo conhecido (99, DiDi, Uber, inDrive)
        val isTarget = isTargetPackage(pkgName)

        // Coleta textos diretos do evento (toasters, avisos, notificações)
        val eventTexts = mutableListOf<String>()
        event.text?.forEach { charSeq ->
            val str = charSeq.toString().trim()
            if (str.isNotEmpty()) eventTexts.add(str)
        }
        val eventTextCombined = eventTexts.joinToString(" ")

        val containsRideKeywords = eventTextCombined.contains("99", ignoreCase = true) ||
                eventTextCombined.contains("didi", ignoreCase = true) ||
                eventTextCombined.contains("uber", ignoreCase = true) ||
                eventTextCombined.contains("corrida", ignoreCase = true) ||
                eventTextCombined.contains("passageiro", ignoreCase = true)

        // Se não for nem o pacote da 99/Uber nem evento do sistema com palavras de corrida, ignora
        if (!isTarget && !containsRideKeywords) {
            return
        }

        // 2. Incrementa contador de eventos de apps de corrida
        totalEventsReceived++
        lastEventPackage = pkgName
        lastEventTimestamp = System.currentTimeMillis()

        val eventTypeName = formatEventType(event.eventType)

        // 3. Extrai TODOS os nós e textos disponíveis
        val allTexts = LinkedHashSet<String>()
        allTexts.addAll(eventTexts)

        // Fonte 1: da árvore do evento (event.source)
        try {
            event.source?.let { sourceNode ->
                extractAllTexts(sourceNode, allTexts)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro ao extrair event.source: ${e.message}")
        }

        // Fonte 2: da janela ativa (rootInActiveWindow)
        try {
            rootInActiveWindow?.let { rootNode ->
                extractAllTexts(rootNode, allTexts)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro ao extrair rootInActiveWindow: ${e.message}")
        }

        // Fonte 3: de todas as janelas interativas (popups, dialogs, overlays)
        try {
            windows?.forEach { window ->
                window.root?.let { windowRoot ->
                    extractAllTexts(windowRoot, allTexts)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro ao iterar windows: ${e.message}")
        }

        val fullText = allTexts.joinToString(" ")
        lastEventSnippet = if (fullText.length > 120) fullText.take(120) + "..." else fullText

        // 4. Diagnóstico com o parser de OCR
        val parsedRide = if (allTexts.isNotEmpty()) OcrParser.parseRideText(fullText) else null

        val parserStatus = when {
            parsedRide != null -> "✅ OFERTA DETECTADA (R$${parsedRide.price} | ${parsedRide.totalDistanceKm}km | ${parsedRide.totalDurationMin}min)"
            allTexts.isEmpty() -> "⚠️ Árvore de acessibilidade sem textos (possível tela protegida ou SurfaceView)"
            fullText.contains("R$") || fullText.contains("aceitar", ignoreCase = true) -> "⚠️ Contém R$/Aceitar, mas campos incompletos (km ou tempo não fecharam)"
            else -> "ℹ️ Tela informativa/mapa (sem oferta completa no momento)"
        }

        // 5. Adiciona entrada no log de diagnóstico para a UI em tempo real
        val logEntry = AccessibilityEventLog(
            timestamp = System.currentTimeMillis(),
            packageName = pkgName,
            eventType = eventTypeName,
            eventTextSnippet = if (eventTexts.isNotEmpty()) eventTexts.joinToString(" | ").take(80) else null,
            nodeCount = allTexts.size,
            treeTextSnippet = if (fullText.length > 160) fullText.take(160) + "..." else fullText,
            parserVerdict = parserStatus,
            isTargetApp = true
        )
        addLog(logEntry)

        Log.i(
            TAG,
            "[DIAGNOSTICO-99] Pacote: $pkgName | Tipo: $eventTypeName | Textos(${allTexts.size}): $fullText | Status: $parserStatus"
        )

        // 6. Verifica se o Radar está ativado pelo usuário antes de disparar o pop-up
        val isEnabled = prefs.getBoolean("is_enabled", false)
        if (!isEnabled) {
            Log.d(TAG, "Radar DESATIVADO nas configurações. Evento diagnosticado mas popup não exibido.")
            return
        }

        if (parsedRide == null) return

        // 7. Evita notificações repetidas para a mesma oferta
        val currentHash = "${parsedRide.price}_${parsedRide.totalDistanceKm}_${parsedRide.totalDurationMin}".hashCode()
        val now = System.currentTimeMillis()
        if (currentHash == lastScannedHash && now - lastScanTimestamp < 8000) {
            return
        }
        lastScannedHash = currentHash
        lastScanTimestamp = now

        Log.i(
            TAG,
            "[SUCESSO] Disparando popup para oferta da ${parsedRide.app}: R$${parsedRide.price}, ${parsedRide.totalDistanceKm}km, ${parsedRide.totalDurationMin}min"
        )

        // 8. Lê metas configuradas pelo motorista
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

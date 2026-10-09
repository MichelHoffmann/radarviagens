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
            "com.sdu.didi.gui",
            "com.sdu.didi.gsui",
            "com.didi.passenger",
            "com.didiglobal.customer",
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

        @Volatile
        var detectedPopupsCount: Long = 0L
            private set

        @Volatile
        var lastDetectedOfferFingerprint: String? = null
            private set

        @Volatile
        var lastDetectedOfferTimestamp: Long = 0L
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
            detectedPopupsCount = 0L
            lastDetectedOfferFingerprint = null
            lastDetectedOfferTimestamp = 0L
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
            val pkgName = (event.packageName ?: event.source?.packageName)?.toString() ?: return

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

            // 2. Descrição de conteúdo do próprio evento
            event.contentDescription?.toString()?.trim()?.let {
                if (it.isNotEmpty()) allTexts.add(it)
            }

            // 3. Registros adicionais do evento (AccessibilityRecord)
            for (i in 0 until event.recordCount) {
                try {
                    val record = event.getRecord(i) ?: continue
                    record.text?.forEach { cs ->
                        val s = cs?.toString()?.trim()
                        if (!s.isNullOrEmpty()) allTexts.add(s)
                    }
                    record.contentDescription?.toString()?.trim()?.let {
                        if (it.isNotEmpty()) allTexts.add(it)
                    }
                } catch (e: Exception) {
                    // Ignora falha de record específico
                }
            }

            // 4. Nós da janela ativa ou fonte do evento
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

            // 5. Se a janela ativa não tinha textos, tenta event.source diretamente
            if (allTexts.isEmpty() && event.source != null && event.source != rootNode) {
                try {
                    extractAllTexts(event.source, allTexts, depth = 0, maxNodes = 100)
                } catch (e: Exception) {
                    Log.d(TAG, "Erro ao extrair nós de event.source: ${e.message}")
                }
            }

            val fullText = allTexts.joinToString(" ")
            lastEventSnippet = if (fullText.length > 120) fullText.take(120) + "..." else fullText

            // Reconhecimento de Pop-up ou Janela de Corrida da 99
            val detectionResult = detect99Popup(pkgName, event.eventType, fullText, allTexts)

            if (detectionResult.isNewOffer) {
                detectedPopupsCount++
                lastDetectedOfferFingerprint = detectionResult.offerFingerprint
                lastDetectedOfferTimestamp = System.currentTimeMillis()
                Log.i(TAG, ">>> POP-UP 99 RECONHECIDO (#$detectedPopupsCount): ${detectionResult.summary} | Textos: ${fullText.take(120)}")
            } else if (detectionResult.isPopup) {
                Log.d(TAG, "Pop-up 99 já contabilizado (mesma oferta em tela): ${detectionResult.summary}")
            }

            // Adiciona no histórico de diagnósticos para a interface
            val logEntry = AccessibilityEventLog(
                timestamp = System.currentTimeMillis(),
                packageName = pkgName,
                eventType = eventTypeName,
                eventTextSnippet = if (allTexts.isNotEmpty()) allTexts.first().take(80) else null,
                nodeCount = allTexts.size,
                treeTextSnippet = if (fullText.length > 160) fullText.take(160) + "..." else fullText,
                parserVerdict = detectionResult.summary,
                isTargetApp = true
            )
            addLog(logEntry)

            // Se o Radar estiver desativado pelo usuário na tela Home, não exibe pop-up flutuante
            val isEnabled = if (::prefs.isInitialized) prefs.getBoolean("is_enabled", false) else false
            if (!isEnabled) {
                return
            }

            // Tentativa opcional de OCR caso o overlay flutuante esteja configurado
            val parsedRide = if (allTexts.isNotEmpty()) OcrParser.parseRideText(fullText) else null
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

    private data class PopupDetectionResult(
        val isPopup: Boolean,
        val isNewOffer: Boolean,
        val summary: String,
        val offerFingerprint: String?
    )

    private fun detect99Popup(
        pkgName: String,
        eventType: Int,
        fullText: String,
        texts: Set<String>
    ): PopupDetectionResult {
        val lower = fullText.lowercase()

        // 1. Ações explícitas de aceitar ou rejeitar corrida
        val hasAcceptButton = lower.contains("aceitar") || lower.contains("aceite")
        val hasDeclineButton = lower.contains("recusar") || lower.contains("rejeitar") || lower.contains("dispensar")

        // 2. Títulos e expressões de chamada de corrida
        val hasNewRideTitle = lower.contains("nova corrida") || lower.contains("nova viagem") ||
                lower.contains("nova solicitação") || lower.contains("solicitação de corrida") ||
                lower.contains("chamada de corrida") || lower.contains("oferta de corrida") ||
                lower.contains("corrida recebida") || lower.contains("corrida disponível")

        // 3. Categorias típicas da 99
        val has99Category = lower.contains("99pop") || lower.contains("99 pop") ||
                lower.contains("99moto") || lower.contains("99 moto") ||
                lower.contains("99plus") || lower.contains("99 plus") ||
                lower.contains("99táxi") || lower.contains("99taxi") || lower.contains("99 táxi") ||
                lower.contains("99negocia") || lower.contains("99 negocia") ||
                lower.contains("99entrega") || lower.contains("99 entrega") ||
                lower.contains("99compartilhado") || lower.contains("99 compartilhado")

        // 4. Indicadores de valor e rota
        val hasPrice = lower.contains("r$") || lower.contains("reais")
        val hasDistance = lower.contains("km")
        val hasDuration = lower.contains("min")
        val hasTripKeywords = lower.contains("passageiro") || lower.contains("embarque") ||
                lower.contains("destino") || lower.contains("desembarque") || lower.contains("viagem")

        // Avaliação do pop-up
        val isPopup = when {
            // Regra 1: Botão explícito de Aceitar presente no app da 99
            hasAcceptButton -> true

            // Regra 2: Menção explícita a Nova Corrida / Solicitação
            hasNewRideTitle -> true

            // Regra 3: Categoria 99 (99Pop, 99Moto, etc.) combinada com R$ ou rota (km/min)
            has99Category && (hasPrice || (hasDistance && hasDuration)) -> true

            // Regra 4: Preço R$ + Distância km + ação ou rota
            hasPrice && hasDistance && (hasDuration || hasTripKeywords || hasDeclineButton) -> true

            // Regra 5: Notificação de corrida da 99
            eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && (hasPrice || hasNewRideTitle || has99Category) -> true

            else -> false
        }

        if (!isPopup) {
            val fallback = when {
                texts.isEmpty() -> "⚠️ Árvore de acessibilidade sem textos legíveis"
                hasPrice -> "ℹ️ Contém R$, mas sem ação de Aceitar ou Nova Corrida"
                else -> "📱 App 99 ativo (tela do mapa / sem pop-up de oferta)"
            }
            return PopupDetectionResult(
                isPopup = false,
                isNewOffer = false,
                summary = fallback,
                offerFingerprint = null
            )
        }

        // Impressão digital para evitar contar várias vezes o mesmo pop-up
        val priceMatch = Regex("r\\$\\s*\\d+(?:[.,]\\d+)?", RegexOption.IGNORE_CASE).find(fullText)?.value ?: ""
        val distMatch = Regex("\\d+(?:[.,]\\d+)?\\s*km", RegexOption.IGNORE_CASE).find(fullText)?.value ?: ""
        val cleanText = fullText.replace(Regex("\\b\\d{1,2}\\s*s\\b", RegexOption.IGNORE_CASE), "").trim()

        val fingerprint = if (priceMatch.isNotEmpty() || distMatch.isNotEmpty()) {
            "${priceMatch}_${distMatch}_${has99Category}"
        } else {
            cleanText.take(60)
        }

        val now = System.currentTimeMillis()
        val isSameRecentOffer = (fingerprint == lastDetectedOfferFingerprint) && (now - lastDetectedOfferTimestamp < 15000L)

        return if (isSameRecentOffer) {
            PopupDetectionResult(
                isPopup = true,
                isNewOffer = false,
                summary = "🔄 Pop-up 99 ativo (mesma oferta em contagem regressiva)",
                offerFingerprint = fingerprint
            )
        } else {
            val desc = buildString {
                append("🎉 NOVO POP-UP 99 RECONHECIDO!")
                if (priceMatch.isNotEmpty()) append(" ($priceMatch")
                if (distMatch.isNotEmpty()) {
                    if (priceMatch.isNotEmpty()) append(" • ") else append(" (")
                    append(distMatch)
                }
                if (priceMatch.isNotEmpty() || distMatch.isNotEmpty()) append(")")
            }
            PopupDetectionResult(
                isPopup = true,
                isNewOffer = true,
                summary = desc,
                offerFingerprint = fingerprint
            )
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

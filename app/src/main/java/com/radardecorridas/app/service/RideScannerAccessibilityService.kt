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
            "com.app99.driver",
            "com.taxis99.driver",
            "com.didiglobal.driver",
            "com.didiglobal.motorista",
            "com.didi.global.driver",
            "com.didi.brazil.driver",
            "com.xiaojukeji.didi.brazil.driver",
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
        var total99EventsReceived: Long = 0L
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
            total99EventsReceived = 0L
            totalRawEvents = 0L
            totalEventsReceived = 0L
            lastDetectedOfferFingerprint = null
            lastDetectedOfferTimestamp = 0L
        }

        fun addLog(log: AccessibilityEventLog) {
            synchronized(_eventLogs) {
                if (_eventLogs.size >= 50) {
                    _eventLogs.removeFirst()
                }
                _eventLogs.add(log)
            }
        }

        fun is99Package(pkg: String): Boolean {
            val lower = pkg.lowercase()
            if (lower == "com.taxis99") return false // App de passageiro
            return lower == "com.app99.driver" ||
                    lower == "com.taxis99.driver" ||
                    lower.contains("driver") && (lower.contains("99") || lower.contains("didi") || lower.contains("xiaojukeji")) ||
                    lower.contains("motorista") && (lower.contains("99") || lower.contains("didi"))
        }

        fun isTargetPackage(pkg: String): Boolean {
            val lower = pkg.lowercase()
            return is99Package(lower) ||
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
            val pkgName = (event.packageName ?: event.source?.packageName)?.toString() ?: "desconhecido"
            val className = (event.className ?: event.source?.className)?.toString()
            val eventTypeName = formatEventType(event.eventType)

            // Log de diagnóstico temporário conforme solicitado pelo usuário
            Log.d("GIGU_DEBUG", "Evento recebido: eventType=$eventTypeName (${event.eventType}), packageName=$pkgName, className=$className")

            // Se for com.app99.driver, despeja a árvore completa de nós no Logcat
            if (pkgName == "com.app99.driver") {
                dumpApp99WindowsAndNodes(event)
            }

            // 1. Registra todo e qualquer evento do Android para diagnóstico em tempo real
            totalRawEvents++
            lastSeenPackage = pkgName

            val is99 = is99Package(pkgName)
            val isTarget = isTargetPackage(pkgName)
            if (is99) {
                total99EventsReceived++
            }
            if (isTarget) {
                totalEventsReceived++
                lastEventPackage = pkgName
                lastEventTimestamp = System.currentTimeMillis()
            }

            // 2. Coleta textos e nós da janela
            val allTexts = LinkedHashSet<String>()
            val nodeDetails = mutableListOf<String>()
            var isTreeAvailable = false

            // Textos diretos da notificação ou evento
            event.text?.forEach { charSeq ->
                val str = charSeq?.toString()?.trim()
                if (!str.isNullOrEmpty()) allTexts.add(str)
            }

            event.contentDescription?.toString()?.trim()?.let {
                if (it.isNotEmpty()) allTexts.add(it)
            }

            // AccessibilityRecords
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
                } catch (_: Exception) {
                }
            }

            // Tenta obter a árvore da janela ativa ou event.source
            val rootNode = try {
                rootInActiveWindow ?: event.source
            } catch (_: Exception) {
                event.source
            }

            if (rootNode != null) {
                isTreeAvailable = true
                try {
                    extractTreeDetails(rootNode, allTexts, nodeDetails, depth = 0, maxNodes = 60)
                } catch (e: Exception) {
                    Log.d(TAG, "Erro ao extrair nós de rootNode: ${e.message}")
                }
            } else if (event.source != null) {
                isTreeAvailable = true
                try {
                    extractTreeDetails(event.source, allTexts, nodeDetails, depth = 0, maxNodes = 60)
                } catch (e: Exception) {
                    Log.d(TAG, "Erro ao extrair nós de event.source: ${e.message}")
                }
            }

            val fullText = allTexts.joinToString(" ")
            if (isTarget) {
                lastEventSnippet = if (fullText.length > 120) fullText.take(120) + "..." else fullText
            }

            // 3. Monta veredito puramente diagnóstico sem alterar contadores nem simular
            val verdict = when {
                is99 && !isTreeAvailable -> "⚠️ Evento 99 recebido, mas árvore da janela está INDISPONÍVEL"
                is99 && allTexts.isEmpty() -> "⚠️ Evento 99 recebido, porém árvore está VAZIA (sem nós de texto)"
                is99 -> "✅ Evento 99 recebido com ${allTexts.size} textos (${nodeDetails.size} elementos mapeados)"
                isTarget -> "ℹ️ Evento de app monitorado ($pkgName) com ${allTexts.size} textos"
                else -> "📱 Evento do sistema ($pkgName)"
            }

            // 4. Cria entrada diagnóstica completa
            val logEntry = AccessibilityEventLog(
                timestamp = System.currentTimeMillis(),
                packageName = pkgName,
                className = className,
                eventType = eventTypeName,
                eventTextSnippet = if (allTexts.isNotEmpty()) allTexts.first().take(80) else event.text?.firstOrNull()?.toString()?.take(80),
                nodeCount = allTexts.size,
                treeTextSnippet = if (fullText.length > 160) fullText.take(160) + "..." else if (fullText.isNotBlank()) fullText else if (!isTreeAvailable) "[Árvore indisponível]" else "[Árvore vazia]",
                nodeDetails = nodeDetails,
                isTreeAvailable = isTreeAvailable,
                is99App = is99,
                parserVerdict = verdict,
                isTargetApp = isTarget
            )
            addLog(logEntry)

            // 5. Detecção de corrida na 99 e exibição do pop-up de sobreposição
            if (is99 && fullText.isNotBlank()) {
                val isRadarEnabled = try {
                    if (!::prefs.isInitialized) {
                        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
                    }
                    prefs.getBoolean("is_enabled", false)
                } catch (_: Exception) {
                    false
                }

                if (isRadarEnabled) {
                    val parsedRide = OcrParser.parseRideText(fullText)
                    if (parsedRide != null && parsedRide.price > 0) {
                        val now = System.currentTimeMillis()
                        // Evita acionar repetidas vezes para o mesmo pop-up dentro de 5 segundos
                        val offerFingerprint = "${parsedRide.price}_${parsedRide.totalDistanceKm}_${parsedRide.totalDurationMin}"
                        if (offerFingerprint != lastDetectedOfferFingerprint || (now - lastDetectedOfferTimestamp > 5000)) {
                            lastDetectedOfferFingerprint = offerFingerprint
                            lastDetectedOfferTimestamp = now
                            detectedPopupsCount++

                            // Carrega configurações de metas do motorista
                            val minKm = prefs.getFloat("min_price_per_km", 2.0f).toDouble()
                            val minHour = prefs.getFloat("min_price_per_hour", 35.0f).toDouble()
                            val fuelPrice = prefs.getFloat("fuel_price", 5.85f).toDouble()
                            val consumption = prefs.getFloat("vehicle_consumption", 11.5f).toDouble()

                            val driverSettings = DriverSettings(
                                isEnabled = true,
                                minPricePerKm = minKm,
                                minPricePerHour = minHour,
                                fuelPricePerLiter = fuelPrice,
                                vehicleConsumptionKmPerLiter = consumption
                            )

                            // Calcula R$/km, R$/h e classificação de rentabilidade
                            val evaluation = RideCalculator.evaluateRide(parsedRide, driverSettings)

                            // Envia intenção para o FloatingOverlayService exibir o pop-up no topo da tela
                            val popupIntent = Intent(this, FloatingOverlayService::class.java).apply {
                                action = FloatingOverlayService.ACTION_SHOW_POPUP
                                putExtra("APP_NAME", "99")
                                putExtra("PRICE", parsedRide.price)
                                putExtra("TOTAL_KM", parsedRide.totalDistanceKm)
                                putExtra("TOTAL_MIN", parsedRide.totalDurationMin)
                                putExtra("PRICE_PER_KM", evaluation.pricePerKm)
                                putExtra("PRICE_PER_HOUR", evaluation.pricePerHour)
                                putExtra("VERDICT", evaluation.verdict.name)
                            }

                            try {
                                startService(popupIntent)
                                Log.i(TAG, "Pop-up do Radar acionado para corrida da 99: R$/km=${evaluation.pricePerKm}, R$/h=${evaluation.pricePerHour}, veredito=${evaluation.verdict}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao iniciar serviço de pop-up flutuante", e)
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Diagnóstico: [$eventTypeName] pkg=$pkgName class=$className textos=${allTexts.size} tree=$isTreeAvailable")
        } catch (t: Throwable) {
            Log.e(TAG, "Falha segura ao registrar diagnóstico de acessibilidade", t)
        }
    }

    private fun extractTreeDetails(
        node: AccessibilityNodeInfo?,
        texts: MutableSet<String>,
        details: MutableList<String>,
        depth: Int = 0,
        maxNodes: Int = 60
    ) {
        if (node == null || depth > 12 || texts.size >= maxNodes) return

        try {
            val nodeText = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val viewId = node.viewIdResourceName?.substringAfterLast('/')

            if (!nodeText.isNullOrEmpty()) {
                texts.add(nodeText)
                if (details.size < 15) {
                    val idPart = if (!viewId.isNullOrEmpty()) " [id: $viewId]" else ""
                    details.add("\"$nodeText\"$idPart")
                }
            } else if (!desc.isNullOrEmpty()) {
                texts.add(desc)
                if (details.size < 15) {
                    val idPart = if (!viewId.isNullOrEmpty()) " [id: $viewId]" else ""
                    details.add("\"$desc\"$idPart (desc)")
                }
            } else if (!viewId.isNullOrEmpty() && details.size < 15) {
                details.add("[id: $viewId] (sem texto)")
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (texts.size >= maxNodes) break
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }
                if (child != null) {
                    extractTreeDetails(child, texts, details, depth + 1, maxNodes)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro em extractTreeDetails: ${e.message}")
        }
    }

    /**
     * Despeja no Logcat (tag "GIGU_DEBUG") a árvore completa de nós da tela da 99:
     * text, contentDescription, className e viewIdResourceName,
     * percorrendo rootInActiveWindow e cada janela retornada por windows (getWindows).
     */
    private fun dumpApp99WindowsAndNodes(event: AccessibilityEvent) {
        Log.d("GIGU_DEBUG", "========== INÍCIO DUMP ÁRVORE COMPLETA 99 (com.app99.driver) ==========")

        // 1. Despeja nó raiz da janela ativa (rootInActiveWindow)
        val activeRoot = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.w("GIGU_DEBUG", "Erro ao obter rootInActiveWindow: ${e.message}")
            null
        }

        if (activeRoot != null) {
            Log.d("GIGU_DEBUG", "--- [Janela Ativa: rootInActiveWindow] ---")
            dumpNodeRecursive(activeRoot, depth = 0, origin = "rootInActiveWindow")
        } else {
            Log.d("GIGU_DEBUG", "--- rootInActiveWindow retornou nulo ---")
        }

        // 2. Despeja o nó fonte direto do evento (event.source), caso exista
        val eventSource = try {
            event.source
        } catch (_: Exception) {
            null
        }
        if (eventSource != null && eventSource != activeRoot) {
            Log.d("GIGU_DEBUG", "--- [Nó Fonte: event.source] ---")
            dumpNodeRecursive(eventSource, depth = 0, origin = "event.source")
        }

        // 3. Percorre cada janela disponível via windows (API getWindows())
        try {
            val windowList = windows
            Log.d("GIGU_DEBUG", "--- Total de Janelas Interativas Detectadas (getWindows): ${windowList.size} ---")
            windowList.forEachIndexed { index, window ->
                val windowRoot = try {
                    window.root
                } catch (e: Exception) {
                    Log.w("GIGU_DEBUG", "Erro ao ler window[$index].root: ${e.message}")
                    null
                }
                val winType = window.type
                val winLayer = window.layer
                val isFocused = window.isFocused
                Log.d("GIGU_DEBUG", "--- Janela #$index [tipo=$winType, layer=$winLayer, focused=$isFocused, id=${window.id}] ---")
                if (windowRoot != null) {
                    dumpNodeRecursive(windowRoot, depth = 0, origin = "window[$index]")
                } else {
                    Log.d("GIGU_DEBUG", "    (root nulo para janela #$index)")
                }
            }
        } catch (e: Exception) {
            Log.w("GIGU_DEBUG", "Erro ao iterar getWindows(): ${e.message}")
        }

        Log.d("GIGU_DEBUG", "========== FIM DUMP ÁRVORE COMPLETA 99 (com.app99.driver) ==========")
    }

    private fun dumpNodeRecursive(node: AccessibilityNodeInfo?, depth: Int, origin: String, maxDepth: Int = 18) {
        if (node == null || depth > maxDepth) return

        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.replace("\n", "\\n")
        val desc = node.contentDescription?.toString()?.replace("\n", "\\n")
        val className = node.className?.toString()
        val viewId = node.viewIdResourceName

        val infoBuilder = StringBuilder()
        infoBuilder.append(indent)
        infoBuilder.append("[$depth] Class: $className")

        if (!viewId.isNullOrEmpty()) {
            infoBuilder.append(" | id: $viewId")
        }
        if (!text.isNullOrEmpty()) {
            infoBuilder.append(" | text: \"$text\"")
        }
        if (!desc.isNullOrEmpty()) {
            infoBuilder.append(" | desc: \"$desc\"")
        }
        if (text.isNullOrEmpty() && desc.isNullOrEmpty() && viewId.isNullOrEmpty()) {
            infoBuilder.append(" | (vazio/layout)")
        }

        Log.d("GIGU_DEBUG", infoBuilder.toString())

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                dumpNodeRecursive(child, depth + 1, origin, maxDepth)
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

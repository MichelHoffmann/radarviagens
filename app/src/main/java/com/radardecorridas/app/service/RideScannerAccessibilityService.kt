package com.radardecorridas.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.util.AccessibilityEventLog
import com.radardecorridas.app.util.OcrParser
import com.radardecorridas.app.util.RideCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Serviço de Acessibilidade responsável por:
 * 1. Captura de tela contínua via takeScreenshot() (API 30+) sem exigir permissão de gravação/transmissão.
 * 2. Reconhecimento de texto offline via ML Kit Text Recognition a cada ~1000 ms.
 * 3. Filtragem de frames repetidos (amostragem de pixels) e redução de 50% para alta performance.
 * 4. Extração de ofertas de corridas pelo OcrParser (preço + distância + tempo).
 * 5. Deduplicação com fingerprint (10 segundos) e exibição do pop-up flutuante no topo da tela.
 * 6. Registro diagnóstico da árvore de acessibilidade da 99 e outros apps monitorados.
 */
class RideScannerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences

    // Gerenciamento do loop de screenshot e OCR
    private var isRadarRunning = false
    private val isScreenshotPending = AtomicBoolean(false)
    private val isOcrProcessing = AtomicBoolean(false)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var loopJob: Job? = null

    // Amostra de pixels para comparar frames consecutivos e descartar quadros idênticos
    private var lastSamplePixels: IntArray? = null

    companion object {
        private const val TAG = "RideScannerService"
        private const val OCR_TAG = "GIGU_OCR"

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

        @Volatile
        var instance: RideScannerAccessibilityService? = null
            private set

        fun startScreenshotLoop() {
            instance?.startLoop()
        }

        fun stopScreenshotLoop() {
            instance?.stopLoop()
        }

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
        instance = this
        try {
            prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_enabled", false)) {
                startLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar SharedPreferences em onServiceConnected", e)
        }
        Log.i(TAG, "RideScannerAccessibilityService conectado com sucesso.")
    }

    // =========================================================================
    // LOOP DE CAPTURA COM takeScreenshot() E OCR (Requisito 3)
    // =========================================================================

    fun startLoop() {
        if (isRadarRunning) return
        isRadarRunning = true
        Log.i(TAG, "Iniciando loop de takeScreenshot() a cada ~1000ms...")

        loopJob?.cancel()
        loopJob = serviceScope.launch {
            while (isActive && isRadarRunning) {
                delay(1000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (isScreenshotPending.compareAndSet(false, true)) {
                        try {
                            val executor = ContextCompat.getMainExecutor(this@RideScannerAccessibilityService)
                            takeScreenshot(
                                Display.DEFAULT_DISPLAY,
                                executor,
                                object : TakeScreenshotCallback {
                                    override fun onSuccess(result: ScreenshotResult) {
                                        isScreenshotPending.set(false)
                                        val hardwareBuffer = result.hardwareBuffer
                                        val colorSpace = result.colorSpace
                                        try {
                                            val rawBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                            val argbBitmap = rawBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                            rawBitmap?.recycle()
                                            hardwareBuffer.close()

                                            if (argbBitmap != null) {
                                                processScreenshotBitmap(argbBitmap)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(OCR_TAG, "Erro ao processar hardwareBuffer: ${e.message}", e)
                                            try { hardwareBuffer.close() } catch (_: Exception) {}
                                        }
                                    }

                                    override fun onFailure(errorCode: Int) {
                                        isScreenshotPending.set(false)
                                        // Requisito 7: Log.e com a tag "GIGU_OCR" para qualquer erro do takeScreenshot
                                        Log.e(OCR_TAG, "takeScreenshot falhou com código de erro: $errorCode")
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            isScreenshotPending.set(false)
                            Log.e(OCR_TAG, "Exceção ao invocar takeScreenshot: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    fun stopLoop() {
        isRadarRunning = false
        isScreenshotPending.set(false)
        loopJob?.cancel()
        loopJob = null
        Log.i(TAG, "Loop de takeScreenshot() encerrado.")
    }

    private fun processScreenshotBitmap(bitmap: Bitmap) {
        // Requisito 3: Descartar o frame se for praticamente igual ao anterior (amostragem de pixels)
        if (isFrameSimilar(bitmap)) {
            bitmap.recycle()
            return
        }

        // Evita sobreposição se uma inferência OCR anterior ainda estiver executando
        if (!isOcrProcessing.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }

        // Requisito 3: Reduzir a imagem para ~50% antes do OCR para ganhar velocidade
        val scaledWidth = max(1, (bitmap.width * 0.5f).toInt())
        val scaledHeight = max(1, (bitmap.height * 0.5f).toInt())
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }

        // Requisito 4: Rodar o OCR (ML Kit Text Recognition, offline) em thread de background
        val inputImage = InputImage.fromBitmap(scaledBitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                // Requisito 7: Log.d com a tag "GIGU_OCR" com o texto bruto de cada leitura
                Log.d(OCR_TAG, "--- OCR TEXTO RECONHECIDO (${rawText.length} chars) ---\n$rawText\n-----------------------------------")
                processOcrText(rawText)
            }
            .addOnFailureListener { e ->
                Log.e(OCR_TAG, "Erro no processamento OCR pelo ML Kit: ${e.message}", e)
            }
            .addOnCompleteListener {
                scaledBitmap.recycle()
                isOcrProcessing.set(false)
            }
    }

    /**
     * Amostra uma grade uniforme de 256 pontos pela tela para comparar variações entre frames
     * em menos de 0.2ms sem sobrecarregar a CPU.
     */
    private fun isFrameSimilar(bitmap: Bitmap): Boolean {
        val samples = extractSamplePixels(bitmap)
        val prev = lastSamplePixels
        if (prev == null || prev.size != samples.size) {
            lastSamplePixels = samples
            return false
        }

        var totalDiff = 0L
        for (i in samples.indices) {
            val c1 = samples[i]
            val c2 = prev[i]
            val r = abs(Color.red(c1) - Color.red(c2))
            val g = abs(Color.green(c1) - Color.green(c2))
            val b = abs(Color.blue(c1) - Color.blue(c2))
            totalDiff += (r + g + b)
        }

        lastSamplePixels = samples
        val avgDiffPerChannel = totalDiff.toDouble() / (samples.size * 3)

        // Se a alteração média for menor que 4 níveis de cor, consideramos a tela idêntica
        return avgDiffPerChannel < 4.0
    }

    private fun extractSamplePixels(bitmap: Bitmap): IntArray {
        val gridX = 16
        val gridY = 16
        val samples = IntArray(gridX * gridY)
        val stepX = max(1, bitmap.width / (gridX + 1))
        val stepY = max(1, bitmap.height / (gridY + 1))
        var idx = 0
        for (y in 1..gridY) {
            val py = (y * stepY).coerceAtMost(bitmap.height - 1)
            for (x in 1..gridX) {
                val px = (x * stepX).coerceAtMost(bitmap.width - 1)
                samples[idx++] = bitmap.getPixel(px, py)
            }
        }
        return samples
    }

    private fun processOcrText(rawText: String) {
        if (rawText.isBlank()) return

        // Requisito 4: Passar o texto ao OcrParser existente, que exige valor + km + tempo
        val rideData = OcrParser.parseRideText(rawText) ?: return

        // Requisito 5: Fingerprint anti-duplicação (valor + km + tempo, expira em 10s)
        val fingerprint = String.format(
            Locale.US,
            "%.2f_%.1f_%d",
            rideData.price,
            rideData.totalDistanceKm,
            rideData.totalDurationMin
        )
        val now = System.currentTimeMillis()

        if (fingerprint == lastDetectedOfferFingerprint && (now - lastDetectedOfferTimestamp) < 10_000L) {
            Log.d(TAG, "Corrida duplicada ($fingerprint) detectada nos últimos 10s. Ignorando exibição repetida.")
            return
        }

        lastDetectedOfferFingerprint = fingerprint
        lastDetectedOfferTimestamp = now
        detectedPopupsCount++

        val isEnabled = try {
            if (!::prefs.isInitialized) {
                prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
            }
            prefs.getBoolean("is_enabled", false)
        } catch (_: Exception) {
            false
        }
        if (!isEnabled) {
            Log.d(TAG, "Radar desativado nas preferências; ignorando pop-up.")
            return
        }

        // Requisito 6: Manter o cálculo de R$/km, R$/hora, as cores e o FloatingOverlayService
        val driverSettings = DriverSettings(
            isEnabled = true,
            minPricePerKm = prefs.getFloat("min_price_per_km", 2.0f).toDouble(),
            minPricePerHour = prefs.getFloat("min_price_per_hour", 35.0f).toDouble(),
            fuelPricePerLiter = prefs.getFloat("fuel_price", 5.85f).toDouble(),
            vehicleConsumptionKmPerLiter = prefs.getFloat("vehicle_consumption", 11.5f).toDouble()
        )

        val evaluation = RideCalculator.evaluateRide(rideData, driverSettings)

        val popupIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_SHOW_POPUP
            putExtra("APP_NAME", rideData.app)
            putExtra("PRICE", rideData.price)
            putExtra("TOTAL_KM", rideData.totalDistanceKm)
            putExtra("TOTAL_MIN", rideData.totalDurationMin)
            putExtra("PRICE_PER_KM", evaluation.pricePerKm)
            putExtra("PRICE_PER_HOUR", evaluation.pricePerHour)
            putExtra("VERDICT", evaluation.verdict.name)
        }

        try {
            startService(popupIntent)
            Log.i(TAG, "Pop-up do Radar acionado para corrida via Screenshot OCR: R$ ${rideData.price}, R$/km=${evaluation.pricePerKm}, R$/h=${evaluation.pricePerHour}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar serviço de pop-up flutuante", e)
        }
    }

    // =========================================================================
    // EVENTOS DE ACESSIBILIDADE E DIAGNÓSTICO
    // =========================================================================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val pkgName = (event.packageName ?: event.source?.packageName)?.toString() ?: "desconhecido"
            val className = (event.className ?: event.source?.className)?.toString()
            val eventTypeName = formatEventType(event.eventType)

            Log.d("GIGU_DEBUG", "Evento recebido: eventType=$eventTypeName (${event.eventType}), packageName=$pkgName, className=$className")

            if (pkgName == "com.app99.driver") {
                dumpApp99WindowsAndNodes(event)
            }

            totalRawEvents++
            lastSeenPackage = pkgName

            val is99 = is99Package(pkgName)
            val isTarget = isTargetPackage(pkgName)

            if (isTarget) {
                lastEventPackage = pkgName
                lastEventTimestamp = System.currentTimeMillis()
                totalEventsReceived++
                if (is99) {
                    total99EventsReceived++
                }
            }

            val allTexts = LinkedHashSet<String>()
            val nodeDetails = mutableListOf<String>()
            var isTreeAvailable = false

            event.text?.forEach { charSeq ->
                val str = charSeq?.toString()?.trim()
                if (!str.isNullOrEmpty()) allTexts.add(str)
            }

            event.contentDescription?.toString()?.trim()?.let {
                if (it.isNotEmpty()) allTexts.add(it)
            }

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
                } catch (_: Exception) {}
            }

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

            val verdict = when {
                is99 && !isTreeAvailable -> "⚠️ Evento 99 recebido, mas árvore da janela está INDISPONÍVEL"
                is99 && allTexts.isEmpty() -> "⚠️ Evento 99 recebido, porém árvore está VAZIA (sem nós de texto)"
                is99 -> "✅ Evento 99 recebido com ${allTexts.size} textos (${nodeDetails.size} elementos mapeados)"
                isTarget -> "ℹ️ Evento de app monitorado ($pkgName) com ${allTexts.size} textos"
                else -> "📱 Evento do sistema ($pkgName)"
            }

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

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar evento de acessibilidade", e)
        }
    }

    private fun extractTreeDetails(
        node: AccessibilityNodeInfo,
        texts: MutableSet<String>,
        nodeDetails: MutableList<String>,
        depth: Int,
        maxNodes: Int
    ) {
        if (nodeDetails.size >= maxNodes || depth > 12) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName
        val className = node.className?.toString()?.substringAfterLast('.')

        if (!text.isNullOrEmpty()) texts.add(text)
        if (!desc.isNullOrEmpty()) texts.add(desc)

        if (!text.isNullOrEmpty() || !desc.isNullOrEmpty() || !viewId.isNullOrEmpty()) {
            val label = buildString {
                append("[$depth]")
                if (!className.isNullOrEmpty()) append(" $className")
                if (!viewId.isNullOrEmpty()) append(" id=${viewId.substringAfterLast('/')}")
                if (!text.isNullOrEmpty()) append(" text=\"${text.take(30)}\"")
                if (!desc.isNullOrEmpty() && desc != text) append(" desc=\"${desc.take(30)}\"")
            }
            nodeDetails.add(label)
        }

        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                extractTreeDetails(child, texts, nodeDetails, depth + 1, maxNodes)
            }
        }
    }

    private fun dumpApp99WindowsAndNodes(event: AccessibilityEvent) {
        Log.d("GIGU_DEBUG", "========== INÍCIO DUMP ÁRVORE COMPLETA 99 (com.app99.driver) ==========")
        Log.d("GIGU_DEBUG", "Disparado por evento: ${formatEventType(event.eventType)} | Class: ${event.className}")

        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.d("GIGU_DEBUG", "Erro ao obter rootInActiveWindow: ${e.message}")
            null
        }

        if (root != null) {
            Log.d("GIGU_DEBUG", "--- Árvore a partir de rootInActiveWindow ---")
            dumpNodeRecursive(root, depth = 0, origin = "rootInActiveWindow")
        } else {
            Log.d("GIGU_DEBUG", "rootInActiveWindow é NULO")
            event.source?.let { src ->
                Log.d("GIGU_DEBUG", "--- Árvore a partir de event.source ---")
                dumpNodeRecursive(src, depth = 0, origin = "event.source")
            }
        }

        try {
            val windows = windows
            Log.d("GIGU_DEBUG", "Total de janelas interativas (getWindows): ${windows.size}")
            for ((wIdx, window) in windows.withIndex()) {
                val winTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) window.title else "N/A"
                Log.d("GIGU_DEBUG", "Janela [$wIdx]: id=${window.id}, type=${window.type}, title=$winTitle, isFocused=${window.isFocused}, isActive=${window.isActive}")
                val winRoot = window.root
                if (winRoot != null) {
                    dumpNodeRecursive(winRoot, depth = 1, origin = "window_$wIdx")
                } else {
                    Log.d("GIGU_DEBUG", "  Janela [$wIdx] tem root NULO")
                }
            }
        } catch (e: Exception) {
            Log.d("GIGU_DEBUG", "Erro ao percorrer windows: ${e.message}")
        }

        Log.d("GIGU_DEBUG", "========== FIM DUMP ÁRVORE COMPLETA 99 ==========")
    }

    private fun dumpNodeRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        origin: String,
        maxDepth: Int = 15
    ) {
        if (depth > maxDepth) return

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
        stopLoop()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "RideScannerAccessibilityService desconectado (onUnbind).")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        stopLoop()
        serviceScope.cancel()
        try {
            textRecognizer.close()
        } catch (_: Exception) {}
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "RideScannerAccessibilityService destruído.")
        super.onDestroy()
    }
}

package com.radardecorridas.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.radardecorridas.app.R
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.util.OcrParser
import com.radardecorridas.app.util.RideCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Foreground service responsável pela captura contínua de frames da tela via MediaProjection
 * e reconhecimento OCR offline com ML Kit para detecção de ofertas de corridas em qualquer app
 * (99 Motorista, Uber, galeria de imagens/prints, etc.).
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var captureJob: Job? = null

    private val isOcrProcessing = AtomicBoolean(false)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Amostra de pixels do frame anterior para detecção rápida de similaridade (Economia de Bateria - Requisito 2)
    private var lastSamplePixels: IntArray? = null

    // Fingerprint anti-duplicação: valor + distância + tempo com expiração de 10s (Requisito 7)
    private var lastOfferFingerprint: String? = null
    private var lastOfferTimestamp: Long = 0L

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val OCR_TAG = "GIGU_OCR"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 4099

        const val ACTION_START = "com.radardecorridas.app.START_SCREEN_CAPTURE"
        const val ACTION_STOP = "com.radardecorridas.app.STOP_SCREEN_CAPTURE"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        // Intervalo de captura configurável: 700 ms conforme Requisito 2
        const val CAPTURE_INTERVAL_MS = 700L

        @Volatile
        var isServiceRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != 0 && resultData != null) {
                    startForegroundServiceNotification()
                    initMediaProjection(resultCode, resultData)
                    isServiceRunning = true
                } else {
                    Log.e(TAG, "Tentativa de iniciar captura sem credenciais válidas de MediaProjection.")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "Recebida ação ACTION_STOP. Encerrando ScreenCaptureService.")
                stopCaptureService()
            }
            else -> {
                Log.w(TAG, "Ação desconhecida recebida: ${intent.action}")
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radar de Corridas - Captura de Tela",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoramento visual de corridas em segundo plano"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Radar de Corridas Ativo")
            .setContentText("Monitorando ofertas da tela...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection falhou ao inicializar.")
            stopCaptureService()
            return
        }

        // Obrigatório no Android 14+ (API 34) registrar o callback antes de criar o VirtualDisplay
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d(TAG, "MediaProjection interrompido pelo sistema operacional.")
                stopCaptureService()
            }
        }, handler)

        setupVirtualDisplay()
        startCaptureLoop()
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Cria o ImageReader para receber os buffers de tela
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RadarScreenCaptureDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive && isServiceRunning) {
                delay(CAPTURE_INTERVAL_MS)
                try {
                    captureAndProcessFrame()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no ciclo de captura de frame: ${e.message}")
                }
            }
        }
    }

    private fun captureAndProcessFrame() {
        val reader = imageReader ?: return
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao adquirir latest image: ${e.message}")
            null
        } ?: return

        var originalBitmap: Bitmap? = null
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val tempBitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            tempBitmap.copyPixelsFromBuffer(buffer)

            originalBitmap = if (rowPadding == 0) {
                tempBitmap
            } else {
                val cropped = Bitmap.createBitmap(tempBitmap, 0, 0, image.width, image.height)
                tempBitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao converter buffer para Bitmap: ${e.message}")
        } finally {
            image.close()
        }

        if (originalBitmap == null) return

        // Requisito 2: Descartar o frame se for praticamente igual ao anterior para economizar bateria
        if (isFrameSimilar(originalBitmap)) {
            originalBitmap.recycle()
            return
        }

        // Se uma análise OCR anterior ainda estiver rodando, descarta o frame para evitar acúmulo de memória
        if (!isOcrProcessing.compareAndSet(false, true)) {
            originalBitmap.recycle()
            return
        }

        // Requisito 3: Reduzir a imagem para ~50% antes do OCR para ganhar velocidade
        val scaledWidth = max(1, (originalBitmap.width * 0.5f).toInt())
        val scaledHeight = max(1, (originalBitmap.height * 0.5f).toInt())
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)

        // Libera bitmap original imediatamente após escalar
        if (originalBitmap != scaledBitmap) {
            originalBitmap.recycle()
        }

        // Execução do ML Kit Text Recognition offline em background
        val inputImage = InputImage.fromBitmap(scaledBitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                // Requisito 10: Log.d com a tag "GIGU_OCR" mostrando o texto bruto reconhecido a cada leitura
                Log.d(OCR_TAG, "--- OCR CAPTURADO (${rawText.length} caracteres) ---\n$rawText\n-----------------------------------")
                processRecognizedText(rawText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha no reconhecimento de texto pelo ML Kit: ${e.message}")
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

        // Se a alteração média for menor que 4 níveis de cor, consideramos a tela estática
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

    private fun processRecognizedText(rawText: String) {
        if (rawText.isBlank()) return

        // Requisitos 4 e 5: Extração e validação estrita (Preço + Km + Tempo) via OcrParser
        val rideData = OcrParser.parseRideText(rawText) ?: return

        // Requisito 7: Fingerprint anti-duplicação (valor + distância + tempo) com expiração de 10s
        val fingerprint = String.format(
            Locale.US,
            "%.2f_%.1f_%d",
            rideData.price,
            rideData.totalDistanceKm,
            rideData.totalDurationMin
        )
        val now = System.currentTimeMillis()

        if (fingerprint == lastOfferFingerprint && (now - lastOfferTimestamp) < 10_000L) {
            Log.d(TAG, "Corrida já processada recentemente (fingerprint: $fingerprint). Descartando duplicata dentro de 10s.")
            return
        }

        lastOfferFingerprint = fingerprint
        lastOfferTimestamp = now

        Log.i(TAG, "CORRIDA DETECTADA POR OCR: R$ ${rideData.price} | ${rideData.totalDistanceKm} km | ${rideData.totalDurationMin} min")

        // Requisito 6: Manter o cálculo de R$/km e R$/hora, a classificação verde/amarelo/vermelho e FloatingOverlayService como estão
        val prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("is_enabled", false)
        if (!isEnabled) {
            Log.d(TAG, "Radar desativado nas preferências; não disparando pop-up.")
            return
        }

        val settings = DriverSettings(
            isEnabled = true,
            minPricePerKm = prefs.getFloat("min_price_per_km", 2.00f).toDouble(),
            minPricePerHour = prefs.getFloat("min_price_per_hour", 35.00f).toDouble(),
            fuelPricePerLiter = prefs.getFloat("fuel_price", 5.85f).toDouble(),
            vehicleConsumptionKmPerLiter = prefs.getFloat("vehicle_consumption", 11.5f).toDouble()
        )

        val evaluation = RideCalculator.evaluateRide(rideData, settings)

        // Dispara o pop-up informativo no FloatingOverlayService
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
        startService(popupIntent)
    }

    private fun stopCaptureService() {
        isServiceRunning = false
        captureJob?.cancel()
        serviceScope.cancel()

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar VirtualDisplay: ${e.message}")
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar ImageReader: ${e.message}")
        }

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar MediaProjection: ${e.message}")
        }

        try {
            textRecognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar ML Kit TextRecognizer: ${e.message}")
        }

        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopCaptureService()
        super.onDestroy()
    }
}

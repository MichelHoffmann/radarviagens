package com.radardecorridas.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.radardecorridas.app.MainActivity
import com.radardecorridas.app.R
import java.util.Locale
import kotlin.math.abs

/**
 * Serviço responsável EXCLUSIVAMENTE pela exibição visual do botão flutuante e do pop-up
 * informativo do Radar de Corridas.
 *
 * Em total conformidade com as regras de segurança e operação:
 * - O Radar é estritamente um visualizador e calculador informativo de ofertas.
 * - NÃO aceita corridas, NÃO recusa corridas, NÃO interage com a interface da 99/Uber.
 * - O pop-up exibe apenas a Classificação (VERDE, AMARELO, VERMELHO), Valor por KM e Valor por Hora.
 */
class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var popupView: View? = null
    private var popupParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    companion object {
        private const val TAG = "FloatingOverlayService"
        const val ACTION_START_RADAR = "com.radardecorridas.app.START_RADAR"
        const val ACTION_STOP_RADAR = "com.radardecorridas.app.STOP_RADAR"
        const val ACTION_SHOW_POPUP = "com.radardecorridas.app.SHOW_POPUP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        // Verifica permissão antes de qualquer manipulação de overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Permissão de sobreposição de tela não concedida.")
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("is_enabled", false)

        when (intent.action) {
            ACTION_STOP_RADAR -> {
                Log.d(TAG, "Ação STOP_RADAR recebida: removendo todos os overlays.")
                stopRadar()
                return START_NOT_STICKY
            }
            ACTION_SHOW_POPUP -> {
                if (!isEnabled) {
                    Log.d(TAG, "Radar desativado nas preferências; ignorando exibição de pop-up.")
                    stopRadar()
                    return START_NOT_STICKY
                }
                val appName = intent.getStringExtra("APP_NAME") ?: "99"
                val price = intent.getDoubleExtra("PRICE", 0.0)
                val totalKm = intent.getDoubleExtra("TOTAL_KM", 0.0)
                val totalMin = intent.getIntExtra("TOTAL_MIN", 0)
                val pricePerKm = intent.getDoubleExtra("PRICE_PER_KM", 0.0)
                val pricePerHour = intent.getDoubleExtra("PRICE_PER_HOUR", 0.0)
                val verdict = intent.getStringExtra("VERDICT") ?: "RED"

                showPopup(appName, price, totalKm, totalMin, pricePerKm, pricePerHour, verdict)
            }
            ACTION_START_RADAR -> {
                if (isEnabled) {
                    showBubble()
                } else {
                    stopRadar()
                    return START_NOT_STICKY
                }
            }
            else -> {
                if (intent.hasExtra("PRICE")) {
                    if (!isEnabled) {
                        stopRadar()
                        return START_NOT_STICKY
                    }
                    val appName = intent.getStringExtra("APP_NAME") ?: "99"
                    val price = intent.getDoubleExtra("PRICE", 0.0)
                    val totalKm = intent.getDoubleExtra("TOTAL_KM", 0.0)
                    val totalMin = intent.getIntExtra("TOTAL_MIN", 0)
                    val pricePerKm = intent.getDoubleExtra("PRICE_PER_KM", 0.0)
                    val pricePerHour = intent.getDoubleExtra("PRICE_PER_HOUR", 0.0)
                    val verdict = intent.getStringExtra("VERDICT") ?: "RED"
                    showPopup(appName, price, totalKm, totalMin, pricePerKm, pricePerHour, verdict)
                } else if (isEnabled) {
                    showBubble()
                } else {
                    stopRadar()
                    return START_NOT_STICKY
                }
            }
        }

        return START_STICKY
    }

    private fun getOverlayLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun showBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        if (bubbleView == null) {
            val inflater = LayoutInflater.from(this)
            bubbleView = inflater.inflate(R.layout.overlay_floating_bubble, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 320
            }
            bubbleParams = params

            setupBubbleTouchListener(bubbleView!!, params)

            try {
                windowManager.addView(bubbleView, params)
                Log.d(TAG, "Botão flutuante do Radar adicionado à tela.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao adicionar botão flutuante", e)
            }
        } else {
            bubbleView?.visibility = View.VISIBLE
        }
    }

    private fun setupBubbleTouchListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var startClickTime = 0L
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    startClickTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao atualizar posição do botão flutuante", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - startClickTime
                    if (!isDragging && clickDuration < 300) {
                        // Ao tocar no botão flutuante, abre o app principal do Radar
                        try {
                            val openIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(openIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao abrir MainActivity", e)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Exibe o pop-up informativo com os 3 dados solicitados:
     * 1. Classificação: VERDE, AMARELO, VERMELHO
     * 2. Valor por quilômetro (R$/km)
     * 3. Valor por hora (R$/h)
     * Utiliza as respectivas cores e funciona estritamente como visualizador passivo.
     */
    private fun showPopup(
        appName: String,
        price: Double,
        totalKm: Double,
        totalMin: Int,
        pricePerKm: Double,
        pricePerHour: Double,
        verdict: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        if (popupView == null) {
            val inflater = LayoutInflater.from(this)
            popupView = inflater.inflate(R.layout.overlay_ride_popup, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 120
            }
            popupParams = params

            setupPopupDragListener(popupView!!, params)

            try {
                windowManager.addView(popupView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao adicionar pop-up de corrida", e)
                return
            }
        }

        val cardContainer = popupView!!.findViewById<CardView>(R.id.cardContainer)
        val tvVerdictBadge = popupView!!.findViewById<TextView>(R.id.tvVerdictBadge)
        val tvAppName = popupView!!.findViewById<TextView>(R.id.tvAppName)
        val tvPrice = popupView!!.findViewById<TextView>(R.id.tvPrice)
        val tvPricePerKm = popupView!!.findViewById<TextView>(R.id.tvPricePerKm)
        val tvPricePerHour = popupView!!.findViewById<TextView>(R.id.tvPricePerHour)
        val tvTripDetails = popupView!!.findViewById<TextView>(R.id.tvTripDetails)
        val btnClose = popupView!!.findViewById<ImageView>(R.id.btnClose)

        tvAppName.text = appName.uppercase(Locale.getDefault())
        tvPrice.text = String.format(Locale.getDefault(), "R$ %.2f", price)
        tvPricePerKm.text = String.format(Locale.getDefault(), "R$ %.2f / km", pricePerKm)
        tvPricePerHour.text = String.format(Locale.getDefault(), "R$ %.2f / h", pricePerHour)
        tvTripDetails.text = String.format(Locale.getDefault(), "%.1f km · %d min", totalKm, totalMin)

        // Classificação com respectivas cores (Verde, Amarelo, Vermelho)
        when (verdict.uppercase(Locale.getDefault())) {
            "GREEN" -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#064E3B")) // Emerald escuro
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#10B981"))
                tvVerdictBadge.setTextColor(Color.WHITE)
                tvVerdictBadge.text = "🟢 VERDE — VALE A PENA"
                tvPricePerKm.setTextColor(Color.parseColor("#34D399"))
                tvPricePerHour.setTextColor(Color.parseColor("#34D399"))
            }
            "YELLOW" -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#78350F")) // Amber escuro
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
                tvVerdictBadge.setTextColor(Color.BLACK)
                tvVerdictBadge.text = "🟡 AMARELO — ATENÇÃO (1 CRITÉRIO)"
                tvPricePerKm.setTextColor(Color.parseColor("#FBBF24"))
                tvPricePerHour.setTextColor(Color.parseColor("#FBBF24"))
            }
            else -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#7F1D1D")) // Rose escuro
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvVerdictBadge.setTextColor(Color.WHITE)
                tvVerdictBadge.text = "🔴 VERMELHO — RECUSAR"
                tvPricePerKm.setTextColor(Color.parseColor("#F87171"))
                tvPricePerHour.setTextColor(Color.parseColor("#F87171"))
            }
        }

        // Fechamento manual apenas do pop-up
        btnClose.setOnClickListener {
            dismissPopup()
        }

        // Auto-fechamento do pop-up após 12 segundos
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = Runnable { dismissPopup() }
        handler.postDelayed(autoDismissRunnable!!, 12000)
    }

    private fun setupPopupDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao arrastar pop-up", e)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dismissPopup() {
        removePopup()
        val prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_enabled", false)) {
            showBubble()
        } else {
            stopRadar()
        }
    }

    private fun removePopup() {
        autoDismissRunnable?.let {
            handler.removeCallbacks(it)
            autoDismissRunnable = null
        }
        if (popupView != null) {
            try {
                windowManager.removeView(popupView)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao remover pop-up", e)
            }
            popupView = null
        }
    }

    private fun removeBubble() {
        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao remover botão flutuante", e)
            }
            bubbleView = null
        }
    }

    private fun stopRadar() {
        removePopup()
        removeBubble()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRadar()
    }
}

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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.radardecorridas.app.R
import java.util.Locale

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val appName = intent.getStringExtra("APP_NAME") ?: "App"
        val price = intent.getDoubleExtra("PRICE", 0.0)
        val totalKm = intent.getDoubleExtra("TOTAL_KM", 0.0)
        val totalMin = intent.getIntExtra("TOTAL_MIN", 0)
        val pricePerKm = intent.getDoubleExtra("PRICE_PER_KM", 0.0)
        val pricePerHour = intent.getDoubleExtra("PRICE_PER_HOUR", 0.0)
        val verdict = intent.getStringExtra("VERDICT") ?: "RED"

        showOverlay(appName, price, totalKm, totalMin, pricePerKm, pricePerHour, verdict)
        return START_STICKY
    }

    private fun showOverlay(
        appName: String,
        price: Double,
        totalKm: Double,
        totalMin: Int,
        pricePerKm: Double,
        pricePerHour: Double,
        verdict: String
    ) {
        if (overlayView == null) {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_ride_popup, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 30
                y = 140
            }

            setupDragListener(overlayView!!, params)
            windowManager.addView(overlayView, params)
        }

        val cardContainer = overlayView!!.findViewById<CardView>(R.id.cardContainer)
        val tvVerdictBadge = overlayView!!.findViewById<TextView>(R.id.tvVerdictBadge)
        val tvAppName = overlayView!!.findViewById<TextView>(R.id.tvAppName)
        val tvPrice = overlayView!!.findViewById<TextView>(R.id.tvPrice)
        val tvPricePerKm = overlayView!!.findViewById<TextView>(R.id.tvPricePerKm)
        val tvPricePerHour = overlayView!!.findViewById<TextView>(R.id.tvPricePerHour)
        val tvTripDetails = overlayView!!.findViewById<TextView>(R.id.tvTripDetails)
        val btnClose = overlayView!!.findViewById<ImageView>(R.id.btnClose)

        tvAppName.text = appName.uppercase(Locale.getDefault())
        tvPrice.text = String.format(Locale.getDefault(), "R$ %.2f", price)
        tvPricePerKm.text = String.format(Locale.getDefault(), "R$ %.2f / km", pricePerKm)
        tvPricePerHour.text = String.format(Locale.getDefault(), "R$ %.2f / h", pricePerHour)
        tvTripDetails.text = String.format(Locale.getDefault(), "%.1f km · %d min", totalKm, totalMin)

        // Níveis solicitados:
        // Verde: atende todos os requisitos
        // Amarelo: atende apenas um
        // Vermelho: não atende nenhum
        when (verdict) {
            "GREEN" -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#064E3B"))
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#10B981"))
                tvVerdictBadge.setTextColor(Color.WHITE)
                tvVerdictBadge.text = "🟢 CORRIDA EXCELENTE (VALE A PENA)"
            }
            "YELLOW" -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#78350F"))
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
                tvVerdictBadge.setTextColor(Color.BLACK)
                tvVerdictBadge.text = "🟡 ATENÇÃO: ATENDE APENAS 1 CRITÉRIO"
            }
            else -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#7F1D1D"))
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvVerdictBadge.setTextColor(Color.WHITE)
                tvVerdictBadge.text = "🔴 RECUSAR: NÃO ATENDE NENHUM CRITÉRIO"
            }
        }

        btnClose.setOnClickListener { removeOverlay() }

        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = Runnable { removeOverlay() }
        handler.postDelayed(autoDismissRunnable!!, 12000)
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
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
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}

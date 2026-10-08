export interface AndroidCodeFile {
  filename: string;
  language: 'kotlin' | 'xml' | 'groovy';
  path: string;
  description: string;
  content: string;
}

export const ANDROID_PROJECT_FILES: AndroidCodeFile[] = [
  {
    filename: 'RideScannerAccessibilityService.kt',
    language: 'kotlin',
    path: 'app/src/main/java/com/radardecorridas/service/RideScannerAccessibilityService.kt',
    description: 'Serviço de Acessibilidade que escaneia a tela da Uber e 99 em tempo real sem consumir bateria, extrai preço, km e minutos e aciona o pop-up.',
    content: `package com.radardecorridas.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class RideScannerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastScannedHash = 0
    private var lastScanTimestamp = 0L

    companion object {
        const val UBER_PACKAGE = "com.ubercab.driver"
        const val NINETEEN_NINE_PACKAGE = "com.taxis99"
        const val ACTION_SHOW_OVERLAY = "com.radardecorridas.SHOW_OVERLAY"
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

        // Evita processamento redundante em menos de 300ms
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

        // Processa os dados da corrida
        parseAndEvaluateRide(pkgName, fullText)
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

    private fun parseAndEvaluateRide(packageName: String, text: String) {
        // 1. Extração do Preço (Ex: R$ 34,50 ou R$34.50)
        val pricePattern = Pattern.compile("(?:R\\\\$|R\\\\$\\\\s*)(\\\\d{1,3}(?:[.,]\\\\d{2})?)", Pattern.CASE_INSENSITIVE)
        val priceMatcher = pricePattern.matcher(text)
        var price = 0.0
        if (priceMatcher.find()) {
            price = priceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        }

        // 2. Extração da Distância Total em km (Ex: 12,4 km ou 8 km)
        val distancePattern = Pattern.compile("(\\\\d+(?:[.,]\\\\d+)?)\\\\s*(?:km|quilômetros)", Pattern.CASE_INSENSITIVE)
        val distanceMatcher = distancePattern.matcher(text)
        val distances = mutableListOf<Double>()
        while (distanceMatcher.find()) {
            distanceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()?.let {
                distances.add(it)
            }
        }

        // 3. Extração do Tempo Total em minutos (Ex: 25 min)
        val durationPattern = Pattern.compile("(\\\\d+)\\\\s*(?:min|minutos)", Pattern.CASE_INSENSITIVE)
        val durationMatcher = durationPattern.matcher(text)
        var totalMinutes = 0
        while (durationMatcher.find()) {
            durationMatcher.group(1)?.toIntOrNull()?.let {
                totalMinutes += it
            }
        }

        if (price <= 0.0 || distances.isEmpty() || totalMinutes <= 0) return

        // Distância total
        val totalDistanceKm = if (distances.size >= 2) distances.sum() else distances[0]

        // Metas configuradas pelo motorista
        val minPricePerKm = prefs.getFloat("min_price_per_km", 2.00f).toDouble()
        val minPricePerHour = prefs.getFloat("min_price_per_hour", 35.00f).toDouble()

        // Cálculos principais
        val pricePerKm = price / Math.max(0.1, totalDistanceKm)
        val pricePerHour = (price / Math.max(1, totalMinutes)) * 60.0

        val meetsKm = pricePerKm >= minPricePerKm
        val meetsHour = pricePerHour >= minPricePerHour

        // Níveis:
        // VERDE = atende todos os requisitos
        // AMARELO = atende apenas um
        // VERMELHO = não atende nenhum
        val verdictColor = when {
            meetsKm && meetsHour -> "GREEN"
            meetsKm || meetsHour -> "YELLOW"
            else -> "RED"
        }

        // Lança o Pop-up Flutuante
        val appName = if (packageName == UBER_PACKAGE) "Uber" else "99"
        val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("APP_NAME", appName)
            putExtra("PRICE", price)
            putExtra("TOTAL_KM", totalDistanceKm)
            putExtra("TOTAL_MIN", totalMinutes)
            putExtra("PRICE_PER_KM", pricePerKm)
            putExtra("PRICE_PER_HOUR", pricePerHour)
            putExtra("MEETS_KM", meetsKm)
            putExtra("MEETS_HOUR", meetsHour)
            putExtra("VERDICT", verdictColor)
        }
        startService(overlayIntent)
    }

    override fun onInterrupt() {}
}
`,
  },
  {
    filename: 'FloatingOverlayService.kt',
    language: 'kotlin',
    path: 'app/src/main/java/com/radardecorridas/service/FloatingOverlayService.kt',
    description: 'Serviço que gerencia o Pop-up Flutuante na tela (WindowManager TYPE_APPLICATION_OVERLAY) com suporte a arraste e cores verde/amarelo/vermelho.',
    content: `package com.radardecorridas.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.radardecorridas.R
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
                y = 120
            }

            // Implementação de gesto para arrastar o balão livremente na tela
            setupDragListener(overlayView!!, params)
            windowManager.addView(overlayView, params)
        }

        // Atualiza elementos do layout
        val cardContainer = overlayView!!.findViewById<CardView>(R.id.cardContainer)
        val tvVerdictBadge = overlayView!!.findViewById<TextView>(R.id.tvVerdictBadge)
        val tvAppName = overlayView!!.findViewById<TextView>(R.id.tvAppName)
        val tvPrice = overlayView!!.findViewById<TextView>(R.id.tvPrice)
        val tvPricePerKm = overlayView!!.findViewById<TextView>(R.id.tvPricePerKm)
        val tvPricePerHour = overlayView!!.findViewById<TextView>(R.id.tvPricePerHour)
        val tvTripDetails = overlayView!!.findViewById<TextView>(R.id.tvTripDetails)
        val btnClose = overlayView!!.findViewById<ImageView>(R.id.btnClose)

        tvAppName.text = appName.uppercase()
        tvPrice.text = String.format(Locale.getDefault(), "R$ %.2f", price)
        tvPricePerKm.text = String.format(Locale.getDefault(), "R$ %.2f / km", pricePerKm)
        tvPricePerHour.text = String.format(Locale.getDefault(), "R$ %.2f / h", pricePerHour)
        tvTripDetails.text = String.format(Locale.getDefault(), "%.1f km · %d min", totalKm, totalMin)

        // Aplicação do Nível de Cor:
        // VERDE: Atende todos os requisitos
        // AMARELO: Atende apenas um requisito
        // VERMELHO: Não atende nenhum critério
        when (verdict) {
            "GREEN" -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#064E3B")) // Fundo esmeralda escuro
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#10B981"))
                tvVerdictBadge.setTextColor(Color.WHITE)
                tvVerdictBadge.text = "🟢 CORRIDA EXCELENTE (VALE A PENA)"
            }
            "YELLOW" -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#78350F")) // Fundo âmbar escuro
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
                tvVerdictBadge.setTextColor(Color.BLACK)
                tvVerdictBadge.text = "🟡 ATENÇÃO: ATENDE APENAS 1 CRITÉRIO"
            }
            else -> {
                cardContainer.setCardBackgroundColor(Color.parseColor("#7F1D1D")) // Fundo vermelho escuro
                tvVerdictBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvVerdictBadge.setTextColor(Color.WHITE)
                tvVerdictBadge.text = "🔴 RECUSAR: NÃO ATENDE NENHUM CRITÉRIO"
            }
        }

        btnClose.setOnClickListener { removeOverlay() }

        // Auto-remover após 12 segundos
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
`,
  },
  {
    filename: 'MainActivity.kt',
    language: 'kotlin',
    path: 'app/src/main/java/com/radardecorridas/ui/MainActivity.kt',
    description: 'Tela de controle principal onde o motorista ativa/desativa o app, define R$/km e R$/hora mínimos e gerencia permissões.',
    content: `package com.radardecorridas.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.radardecorridas.R
import com.radardecorridas.service.FloatingOverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnableRadar: Switch
    private lateinit var etMinPriceKm: EditText
    private lateinit var etMinPriceHour: EditText
    private lateinit var btnSaveSettings: Button
    private lateinit var btnPermissionOverlay: Button
    private lateinit var btnPermissionAccessibility: Button
    private lateinit var btnTestOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        switchEnableRadar = findViewById(R.id.switchEnableRadar)
        etMinPriceKm = findViewById(R.id.etMinPriceKm)
        etMinPriceHour = findViewById(R.id.etMinPriceHour)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnPermissionOverlay = findViewById(R.id.btnPermissionOverlay)
        btnPermissionAccessibility = findViewById(R.id.btnPermissionAccessibility)
        btnTestOverlay = findViewById(R.id.btnTestOverlay)

        // Carrega configurações salvas
        switchEnableRadar.isChecked = prefs.getBoolean("is_enabled", true)
        etMinPriceKm.setText(prefs.getFloat("min_price_per_km", 2.00f).toString())
        etMinPriceHour.setText(prefs.getFloat("min_price_per_hour", 35.00f).toString())

        // Salvar configurações
        btnSaveSettings.setOnClickListener {
            val minKm = etMinPriceKm.text.toString().toFloatOrNull() ?: 2.00f
            val minHour = etMinPriceHour.text.toString().toFloatOrNull() ?: 35.00f

            prefs.edit().apply {
                putBoolean("is_enabled", switchEnableRadar.isChecked)
                putFloat("min_price_per_km", minKm)
                putFloat("min_price_per_hour", minHour)
                apply()
            }
            Toast.makeText(this, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()
        }

        // Permissão de Sobreposição de Tela (SYSTEM_ALERT_WINDOW)
        btnPermissionOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Permissão de sobreposição já concedida!", Toast.LENGTH_SHORT).show()
            }
        }

        // Abrir tela de Acessibilidade do Android
        btnPermissionAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Testar Pop-up na tela com corrida demonstrativa
        btnTestOverlay.setOnClickListener {
            val intent = Intent(this, FloatingOverlayService::class.java).apply {
                putExtra("APP_NAME", "Uber")
                putExtra("PRICE", 38.50)
                putExtra("TOTAL_KM", 12.0)
                putExtra("TOTAL_MIN", 22)
                putExtra("PRICE_PER_KM", 3.20)
                putExtra("PRICE_PER_HOUR", 52.50)
                putExtra("VERDICT", "GREEN")
            }
            startService(intent)
        }
    }
}
`,
  },
  {
    filename: 'overlay_ride_popup.xml',
    language: 'xml',
    path: 'app/src/main/res/layout/overlay_ride_popup.xml',
    description: 'Layout visual XML do Pop-up Flutuante com cantos arredondados, badge colorida, valores em destaque e botão fechar.',
    content: `<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/cardContainer"
    android:layout_width="320dp"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="16dp"
    app:cardElevation="12dp"
    app:cardBackgroundColor="#0F172A">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="14dp">

        <!-- Banner com status colorido (Verde / Amarelo / Vermelho) -->
        <TextView
            android:id="@+id/tvVerdictBadge"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#10B981"
            android:padding="6dp"
            android:text="🟢 CORRIDA VALE A PENA"
            android:textAlignment="center"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:textStyle="bold" />

        <!-- Cabeçalho com App e Botão Fechar -->
        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp">

            <TextView
                android:id="@+id/tvAppName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentStart="true"
                android:text="UBER"
                android:textColor="#94A3B8"
                android:textSize="12sp"
                android:textStyle="bold" />

            <ImageView
                android:id="@+id/btnClose"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:layout_alignParentEnd="true"
                android:src="@android:drawable/ic_menu_close_clear_cancel"
                android:contentDescription="Fechar" />
        </RelativeLayout>

        <!-- Valor Total da Corrida -->
        <TextView
            android:id="@+id/tvPrice"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="R$ 38,50"
            android:textColor="#FFFFFF"
            android:textSize="26sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvTripDetails"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="12.0 km · 22 min"
            android:textColor="#CBD5E1"
            android:textSize="13sp" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginVertical="10dp"
            android:background="#334155" />

        <!-- Grid com R$/km e R$/hora -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:weightSum="2">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="VALOR POR KM"
                    android:textColor="#94A3B8"
                    android:textSize="10sp" />

                <TextView
                    android:id="@+id/tvPricePerKm"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="R$ 3,20 / km"
                    android:textColor="#38BDF8"
                    android:textSize="16sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="VALOR POR HORA"
                    android:textColor="#94A3B8"
                    android:textSize="10sp" />

                <TextView
                    android:id="@+id/tvPricePerHour"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="R$ 52,50 / h"
                    android:textColor="#A855F7"
                    android:textSize="16sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>

    </LinearLayout>
</androidx.cardview.widget.CardView>
`,
  },
  {
    filename: 'AndroidManifest.xml',
    language: 'xml',
    path: 'app/src/main/AndroidManifest.xml',
    description: 'Manifesto do Android com declaração das permissões de sobreposição, serviço de acessibilidade e inicialização automática.',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.radardecorridas">

    <!-- Permissão para desenhar pop-ups sobre outros apps (Uber/99) -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <!-- Permissão para manter serviço em execução em segundo plano -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Radar de Corridas"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">

        <!-- Tela Principal de Configurações -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Serviço de Acessibilidade que escaneia a tela da Uber e 99 -->
        <service
            android:name=".service.RideScannerAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <!-- Serviço do Pop-up Flutuante -->
        <service
            android:name=".service.FloatingOverlayService"
            android:exported="false" />

    </application>
</manifest>
`,
  },
  {
    filename: 'accessibility_service_config.xml',
    language: 'xml',
    path: 'app/src/main/res/xml/accessibility_service_config.xml',
    description: 'Configuração XML do AccessibilityService apontando para os pacotes da Uber e 99.',
    content: `<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_description"
    android:packageNames="com.ubercab.driver,com.taxis99"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true" />
`,
  },
  {
    filename: 'build.gradle.kts',
    language: 'groovy',
    path: 'app/build.gradle.kts',
    description: 'Script de build do Gradle com dependências necessárias (CardView, AppCompat, Material).',
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.radardecorridas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radardecorridas"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
`,
  },
];

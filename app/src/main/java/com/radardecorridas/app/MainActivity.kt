package com.radardecorridas.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.data.SampleRides
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideData
import com.radardecorridas.app.model.ScanHistoryItem
import com.radardecorridas.app.service.FloatingOverlayService
import com.radardecorridas.app.service.RideScannerAccessibilityService
import com.radardecorridas.app.ui.screens.*
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.DiagnosticHelper
import com.radardecorridas.app.util.RideCalculator

enum class AppTab(val title: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    HISTORY("Histórico", Icons.Default.History),
    SYSTEM("Sistema", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var settingsState by mutableStateOf(DriverSettings(isEnabled = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        val isEnabled = prefs.getBoolean("is_enabled", false)

        settingsState = DriverSettings(
            isEnabled = isEnabled,
            minPricePerKm = prefs.getFloat("min_price_per_km", 2.00f).toDouble(),
            minPricePerHour = prefs.getFloat("min_price_per_hour", 35.00f).toDouble(),
            fuelPricePerLiter = prefs.getFloat("fuel_price", 5.85f).toDouble(),
            vehicleConsumptionKmPerLiter = prefs.getFloat("vehicle_consumption", 11.5f).toDouble()
        )

        // Se o Radar estiver configurado como ativo e as permissões existirem, garante início
        if (isEnabled && hasOverlayPermission() && DiagnosticHelper.isAccessibilityServiceEnabled(this)) {
            RideScannerAccessibilityService.startScreenshotLoop()
            val serviceIntent = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_START_RADAR
            }
            startService(serviceIntent)
        }

        setContent {
            RadarDeCorridasTheme {
                var selectedTab by remember { mutableStateOf(AppTab.HOME) }

                var history by remember {
                    mutableStateOf(
                        listOf(
                            ScanHistoryItem(
                                evaluation = RideCalculator.evaluateRide(SampleRides.list[0], settingsState),
                                actionTaken = "accepted",
                                evaluatedAt = "Há 5 min"
                            ),
                            ScanHistoryItem(
                                evaluation = RideCalculator.evaluateRide(SampleRides.list[1], settingsState),
                                actionTaken = "ignored",
                                evaluatedAt = "Há 18 min"
                            ),
                            ScanHistoryItem(
                                evaluation = RideCalculator.evaluateRide(SampleRides.list[2], settingsState),
                                actionTaken = "declined",
                                evaluatedAt = "Há 32 min"
                            )
                        )
                    )
                }

                Scaffold(
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate950)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Radar de Corridas",
                                    color = White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "Calculadora Uber e 99",
                                    color = Slate400,
                                    fontSize = 10.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if (settingsState.isEnabled) "ATIVO" else "OFF",
                                    color = if (settingsState.isEnabled) Emerald400 else Rose400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Switch(
                                    checked = settingsState.isEnabled,
                                    onCheckedChange = { toggleRadar(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = White,
                                        checkedTrackColor = Emerald500,
                                        uncheckedThumbColor = Slate400,
                                        uncheckedTrackColor = Slate800
                                    )
                                )
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Slate950,
                            tonalElevation = 8.dp
                        ) {
                            AppTab.values().forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Emerald400,
                                        selectedTextColor = Emerald400,
                                        unselectedIconColor = Slate400,
                                        unselectedTextColor = Slate400,
                                        indicatorColor = Slate900
                                    )
                                )
                            }
                        }
                    },
                    containerColor = Slate950
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            AppTab.HOME -> HomeScreen(
                                settings = settingsState,
                                onToggleRadar = { toggleRadar(it) },
                                onNavigateToSystem = { selectedTab = AppTab.SYSTEM }
                            )
                            AppTab.HISTORY -> HistoryScreen(
                                history = history,
                                onClearHistory = { history = emptyList() }
                            )
                            AppTab.SYSTEM -> ServiceStatusScreen(
                                settings = settingsState,
                                onUpdateSettings = { updateSettings(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    fun toggleRadar(enabled: Boolean) {
        if (enabled) {
            // Requisito 8: Exige apenas sobreposição de tela e acessibilidade ativas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Conceda a permissão de sobreposição para exibir o pop-up.",
                    Toast.LENGTH_LONG
                ).show()
                DiagnosticHelper.openOverlaySettings(this)
                return
            }

            if (!DiagnosticHelper.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(
                    this,
                    "Ative o serviço de acessibilidade do Radar de Corridas.",
                    Toast.LENGTH_LONG
                ).show()
                DiagnosticHelper.openAccessibilitySettings(this)
                return
            }

            prefs.edit().putBoolean("is_enabled", true).apply()
            settingsState = settingsState.copy(isEnabled = true)

            // Requisito 3 e 8: Inicia o loop de takeScreenshot() no AccessibilityService
            RideScannerAccessibilityService.startScreenshotLoop()

            val serviceIntent = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_START_RADAR
            }
            startService(serviceIntent)
            Toast.makeText(this, "Radar ATIVADO.", Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean("is_enabled", false).apply()
            settingsState = settingsState.copy(isEnabled = false)

            // Requisito 3 e 8: Encerra o loop de takeScreenshot()
            RideScannerAccessibilityService.stopScreenshotLoop()

            val serviceIntent = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_STOP_RADAR
            }
            startService(serviceIntent)
            stopService(serviceIntent)
            Toast.makeText(this, "Radar DESATIVADO.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSettings(newSettings: DriverSettings) {
        val wasEnabled = settingsState.isEnabled
        settingsState = newSettings
        prefs.edit().apply {
            putBoolean("is_enabled", newSettings.isEnabled)
            putFloat("min_price_per_km", newSettings.minPricePerKm.toFloat())
            putFloat("min_price_per_hour", newSettings.minPricePerHour.toFloat())
            putFloat("fuel_price", newSettings.fuelPricePerLiter.toFloat())
            putFloat("vehicle_consumption", newSettings.vehicleConsumptionKmPerLiter.toFloat())
            apply()
        }

        if (wasEnabled != newSettings.isEnabled) {
            toggleRadar(newSettings.isEnabled)
        }
    }

    override fun onResume() {
        super.onResume()
        val isEnabled = prefs.getBoolean("is_enabled", false)
        val hasOverlay = hasOverlayPermission()
        val hasAcc = DiagnosticHelper.isAccessibilityServiceEnabled(this)

        if (isEnabled && (!hasOverlay || !hasAcc)) {
            prefs.edit().putBoolean("is_enabled", false).apply()
            settingsState = settingsState.copy(isEnabled = false)
            RideScannerAccessibilityService.stopScreenshotLoop()
        } else if (isEnabled && hasOverlay && hasAcc) {
            settingsState = settingsState.copy(isEnabled = true)
            RideScannerAccessibilityService.startScreenshotLoop()
        }
    }
}

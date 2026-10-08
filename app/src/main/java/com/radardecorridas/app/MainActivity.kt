package com.radardecorridas.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
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
import com.radardecorridas.app.ui.screens.*
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.RideCalculator

enum class AppTab(val title: String, val icon: ImageVector) {
    SIMULATOR("Simulador", Icons.Default.PhoneAndroid),
    SETTINGS("Ajustes", Icons.Default.Tune),
    SCANNER("Scanner", Icons.Default.CropFree),
    HISTORY("Histórico", Icons.Default.History),
    SYSTEM("Sistema", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RadarPrefs", Context.MODE_PRIVATE)

        setContent {
            RadarDeCorridasTheme {
                var selectedTab by remember { mutableStateOf(AppTab.SIMULATOR) }

                // Carrega configurações persistidas
                var settings by remember {
                    mutableStateOf(
                        DriverSettings(
                            isEnabled = prefs.getBoolean("is_enabled", true),
                            minPricePerKm = prefs.getFloat("min_price_per_km", 2.00f).toDouble(),
                            minPricePerHour = prefs.getFloat("min_price_per_hour", 35.00f).toDouble(),
                            fuelPricePerLiter = prefs.getFloat("fuel_price", 5.85f).toDouble(),
                            vehicleConsumptionKmPerLiter = prefs.getFloat("vehicle_consumption", 11.5f).toDouble()
                        )
                    )
                }

                var currentRide by remember { mutableStateOf(SampleRides.list[0]) }

                var history by remember {
                    mutableStateOf(
                        listOf(
                            ScanHistoryItem(
                                evaluation = RideCalculator.evaluateRide(SampleRides.list[0], settings),
                                actionTaken = "accepted",
                                evaluatedAt = "Há 5 min"
                            ),
                            ScanHistoryItem(
                                evaluation = RideCalculator.evaluateRide(SampleRides.list[1], settings),
                                actionTaken = "ignored",
                                evaluatedAt = "Há 18 min"
                            ),
                            ScanHistoryItem(
                                evaluation = RideCalculator.evaluateRide(SampleRides.list[2], settings),
                                actionTaken = "declined",
                                evaluatedAt = "Há 32 min"
                            )
                        )
                    )
                }

                fun saveSettings(newSettings: DriverSettings) {
                    settings = newSettings
                    prefs.edit().apply {
                        putBoolean("is_enabled", newSettings.isEnabled)
                        putFloat("min_price_per_km", newSettings.minPricePerKm.toFloat())
                        putFloat("min_price_per_hour", newSettings.minPricePerHour.toFloat())
                        putFloat("fuel_price", newSettings.fuelPricePerLiter.toFloat())
                        putFloat("vehicle_consumption", newSettings.vehicleConsumptionKmPerLiter.toFloat())
                        apply()
                    }
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
                                    if (settings.isEnabled) "ATIVO" else "OFF",
                                    color = if (settings.isEnabled) Emerald400 else Rose400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Switch(
                                    checked = settings.isEnabled,
                                    onCheckedChange = { saveSettings(settings.copy(isEnabled = it)) },
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
                            AppTab.SIMULATOR -> SimulatorScreen(
                                settings = settings,
                                currentRide = currentRide,
                                onSelectRide = {
                                    currentRide = it
                                    val eval = RideCalculator.evaluateRide(it, settings)
                                    history = listOf(ScanHistoryItem(eval, null, "Agora mesmo")) + history
                                },
                                onRideAction = { eval, action ->
                                    history = listOf(ScanHistoryItem(eval, action, "Agora mesmo")) + history
                                    val nextIndex = (SampleRides.list.indexOfFirst { r -> r.id == currentRide.id } + 1) % SampleRides.list.size
                                    currentRide = SampleRides.list[nextIndex]
                                }
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                onUpdateSettings = { saveSettings(it) },
                                onResetDefaults = { saveSettings(DriverSettings()) }
                            )
                            AppTab.SCANNER -> ScannerScreen(
                                settings = settings,
                                onSendToPhone = {
                                    currentRide = it
                                    selectedTab = AppTab.SIMULATOR
                                }
                            )
                            AppTab.HISTORY -> HistoryScreen(
                                history = history,
                                onClearHistory = { history = emptyList() }
                            )
                            AppTab.SYSTEM -> ServiceStatusScreen()
                        }
                    }
                }
            }
        }
    }
}

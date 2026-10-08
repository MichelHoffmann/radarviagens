package com.radardecorridas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: DriverSettings,
    onUpdateSettings: (DriverSettings) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status Mestre do Radar (Ativar / Desativar App)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Radar de Corridas", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (settings.isEnabled) "ATIVO" else "DESATIVADO",
                                color = if (settings.isEnabled) Emerald400 else Rose400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            "Escaneia automaticamente chamadas da Uber e 99 e gera o pop-up.",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.isEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = White,
                            checkedTrackColor = Emerald500,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate800
                        )
                    )
                }
            }
        }

        // 2. Metas de R$/km e R$/hora
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Critérios Mínimos do Motorista", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                // Meta R$/km
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Meta Mínima R$ por KM", color = Slate200, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.getDefault(), "R$ %.2f / km", settings.minPricePerKm),
                            color = Emerald400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Slider(
                        value = settings.minPricePerKm.toFloat(),
                        onValueChange = {
                            val rounded = (it * 20).roundToInt() / 20.0
                            onUpdateSettings(settings.copy(minPricePerKm = rounded))
                        },
                        valueRange = 1.0f..4.5f,
                        colors = SliderDefaults.colors(thumbColor = Emerald500, activeTrackColor = Emerald500)
                    )
                }

                // Meta R$/hora
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Meta Mínima R$ por HORA", color = Slate200, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.getDefault(), "R$ %.2f / h", settings.minPricePerHour),
                            color = Cyan400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Slider(
                        value = settings.minPricePerHour.toFloat(),
                        onValueChange = {
                            val rounded = (it * 2).roundToInt() / 2.0
                            onUpdateSettings(settings.copy(minPricePerHour = rounded))
                        },
                        valueRange = 20.0f..90.0f,
                        colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400)
                    )
                }
            }
        }

        // 3. Custos do Veículo (para cálculo de lucro líquido)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Custos do Veículo (Lucro Líquido Real)", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Preço do Combustível:", color = Slate400, fontSize = 11.sp)
                        Text(
                            String.format(Locale.getDefault(), "R$ %.2f / L", settings.fuelPricePerLiter),
                            color = Amber400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.fuelPricePerLiter.toFloat(),
                        onValueChange = {
                            val rounded = (it * 20).roundToInt() / 20.0
                            onUpdateSettings(settings.copy(fuelPricePerLiter = rounded))
                        },
                        valueRange = 3.5f..8.5f,
                        colors = SliderDefaults.colors(thumbColor = Amber500, activeTrackColor = Amber500)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Consumo Médio:", color = Slate400, fontSize = 11.sp)
                        Text(
                            String.format(Locale.getDefault(), "%.1f km / L", settings.vehicleConsumptionKmPerLiter),
                            color = White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.vehicleConsumptionKmPerLiter.toFloat(),
                        onValueChange = {
                            val rounded = (it * 2).roundToInt() / 2.0
                            onUpdateSettings(settings.copy(vehicleConsumptionKmPerLiter = rounded))
                        },
                        valueRange = 6.0f..20.0f,
                        colors = SliderDefaults.colors(thumbColor = Emerald500, activeTrackColor = Emerald500)
                    )
                }
            }
        }

        // 4. Aplicativos Monitorados
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Aplicativos Monitorados", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Uber Driver (com.ubercab.driver)", color = Slate200, fontSize = 12.sp)
                    Checkbox(
                        checked = settings.targetUber,
                        onCheckedChange = { onUpdateSettings(settings.copy(targetUber = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("99 Motorista (com.taxis99)", color = Slate200, fontSize = 12.sp)
                    Checkbox(
                        checked = settings.target99,
                        onCheckedChange = { onUpdateSettings(settings.copy(target99 = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("inDrive", color = Slate200, fontSize = 12.sp)
                    Checkbox(
                        checked = settings.targetInDrive,
                        onCheckedChange = { onUpdateSettings(settings.copy(targetInDrive = it)) }
                    )
                }
            }
        }

        // Botão Restaurar Padrões
        item {
            Button(
                onClick = onResetDefaults,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Slate200),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Restaurar Configurações Padrão", fontSize = 12.sp)
            }
        }
    }
}

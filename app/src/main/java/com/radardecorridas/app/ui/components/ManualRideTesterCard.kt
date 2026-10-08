package com.radardecorridas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.radardecorridas.app.model.RideData
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.RideCalculator
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ManualRideTesterCard(
    settings: DriverSettings,
    onTestRide: (RideData) -> Unit,
    modifier: Modifier = Modifier
) {
    var price by remember { mutableFloatStateOf(32.0f) }
    var totalDistanceKm by remember { mutableFloatStateOf(11.5f) }
    var totalDurationMin by remember { mutableFloatStateOf(24f) }

    val testRide = remember(price, totalDistanceKm, totalDurationMin) {
        val totalKmD = (totalDistanceKm * 10).roundToInt() / 10.0
        val pickupKmD = ((totalKmD * 0.2) * 10).roundToInt() / 10.0
        val tripKmD = ((totalKmD * 0.8) * 10).roundToInt() / 10.0
        val totalMinI = totalDurationMin.toInt()

        RideData(
            id = "custom-${System.currentTimeMillis()}",
            app = "uber",
            category = "UberX",
            price = (price * 10).roundToInt() / 10.0,
            totalDistanceKm = totalKmD,
            pickupDistanceKm = pickupKmD,
            tripDistanceKm = tripKmD,
            totalDurationMin = totalMinI,
            pickupDurationMin = (totalMinI * 0.2).roundToInt(),
            tripDurationMin = (totalMinI * 0.8).roundToInt(),
            pickupAddress = "Ponto de Partida Personalizado",
            destinationAddress = "Destino da Viagem Personalizado",
            timestamp = "Teste Manual"
        )
    }

    val evaluation = remember(testRide, settings) {
        RideCalculator.evaluateRide(testRide, settings)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Slate900)
            .border(1.dp, Slate800, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Calculadora Rápida & Simulador Livre", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = when (evaluation.verdict) {
                        RideVerdict.GREEN -> "🟢 VERDE"
                        RideVerdict.YELLOW -> "🟡 AMARELO"
                        RideVerdict.RED -> "🔴 VERMELHO"
                    },
                    color = when (evaluation.verdict) {
                        RideVerdict.GREEN -> Emerald400
                        RideVerdict.YELLOW -> Amber400
                        RideVerdict.RED -> Rose400
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Slider Preço
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Valor Total:", color = Slate400, fontSize = 11.sp)
                    Text(RideCalculator.formatBRL(price.toDouble()), color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = price,
                    onValueChange = { price = it },
                    valueRange = 8f..120f,
                    colors = SliderDefaults.colors(thumbColor = Emerald500, activeTrackColor = Emerald500)
                )
            }

            // Slider Distância
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Distância Total:", color = Slate400, fontSize = 11.sp)
                    Text(String.format(Locale.getDefault(), "%.1f km", totalDistanceKm), color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = totalDistanceKm,
                    onValueChange = { totalDistanceKm = it },
                    valueRange = 1f..45f,
                    colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400)
                )
            }

            // Slider Tempo
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duração Estimada:", color = Slate400, fontSize = 11.sp)
                    Text("${totalDurationMin.toInt()} min", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = totalDurationMin,
                    onValueChange = { totalDurationMin = it },
                    valueRange = 4f..90f,
                    colors = SliderDefaults.colors(thumbColor = Amber500, activeTrackColor = Amber500)
                )
            }

            // Ação de Testar no Simulador
            Button(
                onClick = { onTestRide(testRide) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simular esta Corrida no Celular", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

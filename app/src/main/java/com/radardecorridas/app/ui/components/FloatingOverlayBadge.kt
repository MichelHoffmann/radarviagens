package com.radardecorridas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideEvaluation
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.RideCalculator
import java.util.Locale

/**
 * Visualizador informativo da oferta de corrida simulada no app.
 * Em estrita conformidade com as regras do Radar de Corridas:
 * - Não possui botões de Aceitar/Recusar que interajam com apps terceiros.
 * - Exibe estritamente a Classificação (VERDE, AMARELO, VERMELHO), R$/km e R$/hora.
 */
@Composable
fun FloatingOverlayBadge(
    evaluation: RideEvaluation,
    settings: DriverSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeText, cardBg, borderColor) = when (evaluation.verdict) {
        RideVerdict.GREEN -> Quadruple(
            Emerald500,
            "🟢 VERDE — VALE A PENA",
            Emerald950.copy(alpha = 0.95f),
            Emerald500.copy(alpha = 0.8f)
        )
        RideVerdict.YELLOW -> Quadruple(
            Amber500,
            "🟡 AMARELO — ATENÇÃO (1 CRITÉRIO)",
            Amber950.copy(alpha = 0.95f),
            Amber500.copy(alpha = 0.8f)
        )
        RideVerdict.RED -> Quadruple(
            Rose500,
            "🔴 VERMELHO — RECUSAR",
            Rose950.copy(alpha = 0.95f),
            Rose500.copy(alpha = 0.8f)
        )
    }

    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
    ) {
        Column {
            // 1. Classificação da corrida: VERDE, AMARELO, VERMELHO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(badgeBg)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badgeText,
                    color = if (evaluation.verdict == RideVerdict.YELLOW) Black else White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    tint = if (evaluation.verdict == RideVerdict.YELLOW) Black else White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClose() }
                )
            }

            // Conteúdo Interno: R$/km e R$/h com as respectivas cores
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cabeçalho da Chamada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = evaluation.ride.app.uppercase(Locale.getDefault()),
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${evaluation.ride.totalDistanceKm} km · ${evaluation.ride.totalDurationMin} min",
                        color = Slate400,
                        fontSize = 11.sp
                    )
                }

                // Grid dos 2 Indicadores Essenciais: R$/km e R$/hora
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 2. Valor por quilômetro
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (evaluation.meetsKmRequirement) Emerald950.copy(alpha = 0.6f) else Rose950.copy(alpha = 0.5f))
                            .border(1.dp, if (evaluation.meetsKmRequirement) Emerald500.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("VALOR / KM", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                String.format(Locale.getDefault(), "R$ %.2f", evaluation.pricePerKm),
                                color = if (evaluation.meetsKmRequirement) Emerald400 else Rose400,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                String.format(Locale.getDefault(), "Meta: R$ %.2f", settings.minPricePerKm),
                                color = Slate400,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // 3. Valor por hora
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (evaluation.meetsHourRequirement) Emerald950.copy(alpha = 0.6f) else Rose950.copy(alpha = 0.5f))
                            .border(1.dp, if (evaluation.meetsHourRequirement) Emerald500.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("VALOR / HORA", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                String.format(Locale.getDefault(), "R$ %.2f", evaluation.pricePerHour),
                                color = if (evaluation.meetsHourRequirement) Emerald400 else Rose400,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                String.format(Locale.getDefault(), "Meta: R$ %.2f", settings.minPricePerHour),
                                color = Slate400,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                // Valor Bruto da Corrida
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Valor Ofertado:", color = Slate400, fontSize = 11.sp)
                    Text(
                        RideCalculator.formatBRL(evaluation.ride.price),
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

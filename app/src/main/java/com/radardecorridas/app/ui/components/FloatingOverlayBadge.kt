package com.radardecorridas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideEvaluation
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.RideCalculator
import java.util.Locale

@Composable
fun FloatingOverlayBadge(
    evaluation: RideEvaluation,
    settings: DriverSettings,
    onClose: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    val (badgeBg, badgeText, cardBg, borderColor) = when (evaluation.verdict) {
        RideVerdict.GREEN -> Quadruple(
            Emerald500,
            "🟢 VALE A PENA (2/2)",
            Emerald950.copy(alpha = 0.95f),
            Emerald500.copy(alpha = 0.8f)
        )
        RideVerdict.YELLOW -> Quadruple(
            Amber500,
            "🟡 ATENÇÃO (1/2)",
            Amber950.copy(alpha = 0.95f),
            Amber500.copy(alpha = 0.8f)
        )
        RideVerdict.RED -> Quadruple(
            Rose500,
            "🔴 RECUSAR (0/2)",
            Rose950.copy(alpha = 0.95f),
            Rose500.copy(alpha = 0.8f)
        )
    }

    Box(
        modifier = modifier
            .widthIn(max = 340.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Column {
            // Barra de Status Superior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(badgeBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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

            // Conteúdo Interno
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preço e Categoria
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = evaluation.ride.category.uppercase(Locale.getDefault()),
                            color = Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = RideCalculator.formatBRL(evaluation.ride.price),
                            color = White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${evaluation.ride.totalDistanceKm} km",
                            color = Slate200,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${evaluation.ride.totalDurationMin} min",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                // Grid dos 2 Indicadores Principais: R$/km e R$/hora
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card R$/km
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (evaluation.meetsKmRequirement) Emerald950.copy(alpha = 0.6f) else Rose950.copy(alpha = 0.5f))
                            .border(1.dp, if (evaluation.meetsKmRequirement) Emerald500.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("R$/KM", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (evaluation.meetsKmRequirement) "✓" else "✗",
                                    color = if (evaluation.meetsKmRequirement) Emerald400 else Rose400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                String.format(Locale.getDefault(), "R$ %.2f", evaluation.pricePerKm),
                                color = White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                String.format(Locale.getDefault(), "Meta: R$ %.2f", settings.minPricePerKm),
                                color = Slate400,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Card R$/hora
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (evaluation.meetsHourRequirement) Emerald950.copy(alpha = 0.6f) else Rose950.copy(alpha = 0.5f))
                            .border(1.dp, if (evaluation.meetsHourRequirement) Emerald500.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("R$/HORA", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (evaluation.meetsHourRequirement) "✓" else "✗",
                                    color = if (evaluation.meetsHourRequirement) Emerald400 else Rose400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                String.format(Locale.getDefault(), "R$ %.2f", evaluation.pricePerHour),
                                color = White,
                                fontSize = 15.sp,
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

                // Motivo do Veredito
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Black.copy(alpha = 0.35f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = evaluation.verdictReason,
                        color = Slate200,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                // Custos & Lucro Líquido
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Combustível est.:", color = Slate400, fontSize = 11.sp)
                            Text(
                                "- ${RideCalculator.formatBRL(evaluation.fuelCost)}",
                                color = Rose400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lucro Líquido:", color = Slate400, fontSize = 11.sp)
                            Text(
                                RideCalculator.formatBRL(evaluation.netProfit),
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Ações
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable { isExpanded = !isExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isExpanded) "Menos" else "Detalhes",
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onDecline,
                            colors = ButtonDefaults.buttonColors(containerColor = Rose950, contentColor = Rose400),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Recusar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Aceitar", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

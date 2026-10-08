package com.radardecorridas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.model.ScanHistoryItem
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.RideCalculator

@Composable
fun HistoryScreen(
    history: List<ScanHistoryItem>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<RideVerdict?>(null) }

    val filteredList = remember(history, selectedFilter) {
        if (selectedFilter == null) history
        else history.filter { it.evaluation.verdict == selectedFilter }
    }

    val greenCount = history.count { it.evaluation.verdict == RideVerdict.GREEN }
    val yellowCount = history.count { it.evaluation.verdict == RideVerdict.YELLOW }
    val redCount = history.count { it.evaluation.verdict == RideVerdict.RED }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Histórico de Corridas", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Limpar", color = Rose400, fontSize = 12.sp)
                    }
                }
            }
        }

        // Métricas rápidas
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Slate900)
                        .border(1.dp, Slate800, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("TOTAL", color = Slate400, fontSize = 9.sp)
                        Text("${history.size}", color = White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Emerald950.copy(alpha = 0.5f))
                        .border(1.dp, Emerald500.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("VERDES", color = Emerald400, fontSize = 9.sp)
                        Text("$greenCount", color = Emerald400, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Amber950.copy(alpha = 0.5f))
                        .border(1.dp, Amber500.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("AMARELAS", color = Amber400, fontSize = 9.sp)
                        Text("$yellowCount", color = Amber400, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Rose950.copy(alpha = 0.5f))
                        .border(1.dp, Rose500.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("VERMELHAS", color = Rose400, fontSize = 9.sp)
                        Text("$redCount", color = Rose400, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // Filtros
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("Todas", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == RideVerdict.GREEN,
                    onClick = { selectedFilter = RideVerdict.GREEN },
                    label = { Text("🟢 Verdes", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == RideVerdict.YELLOW,
                    onClick = { selectedFilter = RideVerdict.YELLOW },
                    label = { Text("🟡 Amarelas", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == RideVerdict.RED,
                    onClick = { selectedFilter = RideVerdict.RED },
                    label = { Text("🔴 Vermelhas", fontSize = 11.sp) }
                )
            }
        }

        // Lista de Itens
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma corrida encontrada neste filtro.", color = Slate400, fontSize = 12.sp)
                }
            }
        } else {
            items(filteredList) { item ->
                val eval = item.evaluation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate900)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(eval.ride.category, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (eval.verdict) {
                                        RideVerdict.GREEN -> "🟢 Vale a Pena"
                                        RideVerdict.YELLOW -> "🟡 Atenção"
                                        RideVerdict.RED -> "🔴 Recusar"
                                    },
                                    color = when (eval.verdict) {
                                        RideVerdict.GREEN -> Emerald400
                                        RideVerdict.YELLOW -> Amber400
                                        RideVerdict.RED -> Rose400
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(RideCalculator.formatBRL(eval.ride.price), color = White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("R$ %.2f / km".format(eval.pricePerKm), color = if (eval.meetsKmRequirement) Emerald400 else Rose400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("R$ %.2f / h".format(eval.pricePerHour), color = if (eval.meetsHourRequirement) Emerald400 else Rose400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${eval.ride.totalDistanceKm}km · ${eval.ride.totalDurationMin}m", color = Slate400, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

package com.radardecorridas.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.data.SampleRides
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideData
import com.radardecorridas.app.model.RideEvaluation
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.ui.components.FloatingOverlayBadge
import com.radardecorridas.app.ui.components.ManualRideTesterCard
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.RideCalculator
import kotlin.math.roundToInt

@Composable
fun SimulatorScreen(
    settings: DriverSettings,
    currentRide: RideData,
    onSelectRide: (RideData) -> Unit,
    onRideAction: (RideEvaluation, String) -> Unit,
    onToggleRadar: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var overlayOffsetX by remember { mutableFloatStateOf(20f) }
    var overlayOffsetY by remember { mutableFloatStateOf(80f) }
    var isOverlayVisible by remember { mutableStateOf(true) }

    val evaluation = remember(currentRide, settings) {
        RideCalculator.evaluateRide(currentRide, settings)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Painel Mestre de Ativação do Radar (Controle Principal na Tela Inicial)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(
                        1.5.dp,
                        if (settings.isEnabled) Emerald500 else Slate800,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Radar de Corridas (Uber e 99)",
                            color = White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (settings.isEnabled)
                                "🟢 ATIVO: Botão flutuante na tela e escaneamento ligado"
                            else
                                "⚪ DESATIVADO: Botão flutuante e escaneamento desligados",
                            color = if (settings.isEnabled) Emerald400 else Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                // Botão de Ação Principal: Ativar Radar / Desativar Radar
                Button(
                    onClick = { onToggleRadar(!settings.isEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.isEnabled) Rose500 else Emerald500,
                        contentColor = if (settings.isEnabled) White else Slate950
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        if (settings.isEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (settings.isEnabled) "DESATIVAR RADAR" else "ATIVAR RADAR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Aviso de Permissão de Sobreposição se não estiver concedida
                val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true

                if (!hasOverlayPermission) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Amber950.copy(alpha = 0.5f))
                            .border(1.dp, Amber500, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sobreposição de Tela necessária para o botão flutuante",
                            color = Amber400,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                DiagnosticHelper.openOverlaySettings(context)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Conceder", color = Emerald400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Mockup do Smartphone com a Corrida e o Pop-up Flutuante
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Slate900)
                    .border(2.dp, Slate700, RoundedCornerShape(32.dp))
                    .padding(12.dp)
            ) {
                // Tela interna do celular
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Slate950)
                ) {
                    // Barra de Status Superior do Android
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("18:24", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("5G · 88%", color = Slate400, fontSize = 10.sp)
                    }

                    // Card da chamada Uber / 99 no celular
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (currentRide.app == "uber") Black else Amber500),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (currentRide.app == "uber") "UB" else "99",
                                        color = White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(currentRide.category, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Chamada recebida", color = Slate400, fontSize = 9.sp)
                                }
                            }
                            Text(
                                RideCalculator.formatBRL(currentRide.price),
                                color = White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${currentRide.totalDistanceKm} km no total", color = Slate200, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${currentRide.totalDurationMin} min estimad.", color = Slate400, fontSize = 11.sp)
                        }

                        Text(
                            "${currentRide.pickupAddress} ➔ ${currentRide.destinationAddress}",
                            color = Slate400,
                            fontSize = 10.sp,
                            maxLines = 1
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRideAction(evaluation, "declined") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Slate200),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Recusar", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { onRideAction(evaluation, "accepted") },
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Aceitar Corrida", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Pop-up Flutuante Arrastável (se o radar estiver ativo)
                    if (settings.isEnabled && isOverlayVisible) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(overlayOffsetX.roundToInt(), overlayOffsetY.roundToInt()) }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        overlayOffsetX = (overlayOffsetX + dragAmount.x).coerceIn(10f, 60f)
                                        overlayOffsetY = (overlayOffsetY + dragAmount.y).coerceIn(40f, 160f)
                                    }
                                }
                        ) {
                            FloatingOverlayBadge(
                                evaluation = evaluation,
                                settings = settings,
                                onClose = { isOverlayVisible = false }
                            )
                        }
                    } else if (!settings.isEnabled) {
                        // Aviso de radar desativado
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Rose950.copy(alpha = 0.9f))
                                .border(1.dp, Rose500, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Radar Desativado — Toque em Ativar Radar acima",
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (!isOverlayVisible) {
                        Button(
                            onClick = { isOverlayVisible = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950)
                        ) {
                            Text("Reabrir Pop-up", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tabela explicativa dos 3 níveis solicitados pelo usuário
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Critérios de Avaliação do Pop-up", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Emerald950.copy(alpha = 0.6f))
                            .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("🟢 VERDE", color = Emerald400, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("Atende todos os critérios", color = White, fontSize = 10.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Amber950.copy(alpha = 0.6f))
                            .border(1.dp, Amber500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("🟡 AMARELO", color = Amber400, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("Atende apenas um", color = White, fontSize = 10.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose950.copy(alpha = 0.6f))
                            .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("🔴 VERMELHO", color = Rose400, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("Não atende nenhum", color = White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Cenários de Corridas Prontas para Teste
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Chamadas Reais para Testar", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                SampleRides.list.forEach { ride ->
                    val isSelected = ride.id == currentRide.id
                    val rEval = RideCalculator.evaluateRide(ride, settings)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Slate800 else Slate950)
                            .border(1.dp, if (isSelected) Emerald500 else Slate800, RoundedCornerShape(12.dp))
                            .clickable {
                                isOverlayVisible = true
                                onSelectRide(ride)
                            }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ride.category, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (rEval.verdict) {
                                        RideVerdict.GREEN -> "🟢 Verde"
                                        RideVerdict.YELLOW -> "🟡 Amarelo"
                                        RideVerdict.RED -> "🔴 Vermelho"
                                    },
                                    color = when (rEval.verdict) {
                                        RideVerdict.GREEN -> Emerald400
                                        RideVerdict.YELLOW -> Amber400
                                        RideVerdict.RED -> Rose400
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "${ride.pickupAddress} ➔ ${ride.destinationAddress}",
                                color = Slate400,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(RideCalculator.formatBRL(ride.price), color = White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Text("R$ %.2f/km".format(rEval.pricePerKm), color = Slate400, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Calculadora Livre com Sliders
        item {
            ManualRideTesterCard(
                settings = settings,
                onTestRide = {
                    isOverlayVisible = true
                    onSelectRide(it)
                }
            )
        }
    }
}

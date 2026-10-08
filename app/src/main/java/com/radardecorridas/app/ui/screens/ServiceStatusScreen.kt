package com.radardecorridas.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.service.FloatingOverlayService
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.DiagnosticHelper
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ServiceStatusScreen(
    settings: DriverSettings? = null,
    onUpdateSettings: ((DriverSettings) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var diagStatus by remember { mutableStateOf(DiagnosticHelper.checkStatus(context)) }

    LaunchedEffect(Unit) {
        diagStatus = DiagnosticHelper.checkStatus(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Diagnóstico e Permissões do Sistema
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Diagnóstico do Sistema Android",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { diagStatus = DiagnosticHelper.checkStatus(context) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Verifique o estado real dos componentes para assegurar o funcionamento do Radar:",
                    color = Slate400,
                    fontSize = 11.sp
                )

                // Item 1: Sobreposição de tela
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1. Sobreposição de Tela", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (diagStatus.hasOverlayPermission) "✅ Sobreposição permitida" else "❌ Sobreposição não permitida",
                                color = if (diagStatus.hasOverlayPermission) Emerald400 else Rose400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { DiagnosticHelper.openOverlaySettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (diagStatus.hasOverlayPermission) Slate800 else Amber500,
                                contentColor = if (diagStatus.hasOverlayPermission) Slate200 else Slate950
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (diagStatus.hasOverlayPermission) "Configurar" else "Conceder",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Item 2: Acessibilidade no Android
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. Serviço de Acessibilidade", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (diagStatus.isAccessibilityEnabledInSystem) "✅ Serviço de acessibilidade ativo no Android" else "❌ Serviço de acessibilidade inativo",
                                color = if (diagStatus.isAccessibilityEnabledInSystem) Emerald400 else Rose400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { DiagnosticHelper.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (diagStatus.isAccessibilityEnabledInSystem) Slate800 else Cyan400,
                                contentColor = if (diagStatus.isAccessibilityEnabledInSystem) Slate200 else Slate950
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (diagStatus.isAccessibilityEnabledInSystem) "Configurar" else "Ativar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Item 3: Conexão do Serviço
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("3. Conexão do Serviço em Tempo Real", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (diagStatus.isServiceConnected) "✅ Serviço conectado e respondendo" else "❌ Serviço não conectado ao app",
                        color = if (diagStatus.isServiceConnected) Emerald400 else Rose400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (diagStatus.isAccessibilityEnabledInSystem && !diagStatus.isServiceConnected) {
                        Text(
                            text = "Aviso: O serviço está ativado nas configurações do Android, porém o processo ainda não está conectado. Desative e ative a chave de acessibilidade para reiniciar.",
                            color = Amber400,
                            fontSize = 10.sp
                        )
                    }
                }

                // Item 4: Pacotes Monitorados
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("4. Pacotes de Aplicativos Monitorados", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = diagStatus.monitoredPackages.joinToString(", "),
                        color = Slate400,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Item 5: Diagnóstico de Eventos
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate950)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5. Diagnóstico de Recepção de Eventos", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${diagStatus.totalEventsReceived} recebidos",
                            color = if (diagStatus.totalEventsReceived > 0) Emerald400 else Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (diagStatus.lastEventPackage != null && diagStatus.lastEventTimestamp > 0) {
                        val formattedTime = DateFormat.format("HH:mm:ss", Date(diagStatus.lastEventTimestamp)).toString()
                        Text(
                            text = "Último evento: ${diagStatus.lastEventPackage} às $formattedTime",
                            color = Emerald400,
                            fontSize = 11.sp
                        )
                        if (!diagStatus.lastEventTextSnippet.isNullOrEmpty()) {
                            Text(
                                text = "Texto: \"${diagStatus.lastEventTextSnippet}\"",
                                color = Slate400,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2
                            )
                        }
                    } else {
                        Text(
                            text = "Aguardando primeiro evento da 99 ou Uber...",
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }
                }

                // Testar Pop-up Nativo na tela real
                Button(
                    onClick = {
                        val intent = Intent(context, FloatingOverlayService::class.java).apply {
                            action = FloatingOverlayService.ACTION_SHOW_POPUP
                            putExtra("APP_NAME", "99")
                            putExtra("PRICE", 10.32)
                            putExtra("TOTAL_KM", 7.8)
                            putExtra("TOTAL_MIN", 15)
                            putExtra("PRICE_PER_KM", 1.32)
                            putExtra("PRICE_PER_HOUR", 41.28)
                            putExtra("VERDICT", "YELLOW")
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Testar Pop-up Flutuante Informativo na Tela",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // 2. Critérios de Avaliação (Metas do Motorista)
        if (settings != null && onUpdateSettings != null) {
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
                    Text(
                        text = "Critérios de Classificação",
                        color = White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Meta R$/km
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Meta Mínima R$ por KM", color = Slate200, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "R$ ${String.format(Locale.US, "%.2f", settings.minPricePerKm)}",
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.minPricePerKm.toFloat(),
                            onValueChange = {
                                val rounded = (it * 10).roundToInt() / 10.0
                                onUpdateSettings(settings.copy(minPricePerKm = rounded))
                            },
                            valueRange = 1.0f..5.0f,
                            steps = 39,
                            colors = SliderDefaults.colors(
                                thumbColor = Emerald400,
                                activeTrackColor = Emerald500,
                                inactiveTrackColor = Slate800
                            )
                        )
                    }

                    // Meta R$/hora
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Meta Mínima R$ por Hora", color = Slate200, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "R$ ${String.format(Locale.US, "%.0f", settings.minPricePerHour)}/h",
                                color = Cyan400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.minPricePerHour.toFloat(),
                            onValueChange = {
                                val rounded = (it / 5.0).roundToInt() * 5.0
                                onUpdateSettings(settings.copy(minPricePerHour = rounded))
                            },
                            valueRange = 20.0f..100.0f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = Cyan400,
                                activeTrackColor = Cyan400,
                                inactiveTrackColor = Slate800
                            )
                        )
                    }
                }
            }
        }

        // 3. Regra de Operação Passiva
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900.copy(alpha = 0.5f))
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Garantia de Não Interferência",
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "O Radar de Corridas nunca toca na tela, nunca aceita ou recusa chamadas automaticamente e não interfere nos aplicativos da 99, Uber ou inDrive.",
                    color = Slate400,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

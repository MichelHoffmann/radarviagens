package com.radardecorridas.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.service.FloatingOverlayService
import com.radardecorridas.app.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ServiceStatusScreen(
    settings: DriverSettings? = null,
    onUpdateSettings: ((DriverSettings) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val hasOverlay = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Permissões do Sistema
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
                Text(
                    text = "Permissões do Sistema Android",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "O Radar necessita de duas permissões do Android para monitorar ofertas e exibir o pop-up informativo:",
                    color = Slate400,
                    fontSize = 11.sp
                )

                // Permissão 1: Sobreposição de tela
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Slate800,
                        contentColor = if (hasOverlay) Emerald400 else Amber400
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (hasOverlay) "✓ 1. Sobreposição de Tela (Concedida)" else "1. Ativar Sobreposição de Tela",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Permissão 2: Acessibilidade
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Slate800,
                        contentColor = Cyan400
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "2. Ativar Serviço de Acessibilidade",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                        text = "3. Testar Pop-up Flutuante na Tela",
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

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.service.FloatingOverlayService
import com.radardecorridas.app.ui.theme.*

@Composable
fun ServiceStatusScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                Text("Permissões do Sistema Android", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Para o Radar de Corridas desenhar o pop-up sobre a Uber e 99 e ler os valores de chamadas na tela:",
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
                    colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Emerald400),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("1. Ativar Permissão de Sobreposição de Tela", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Permissão 2: Acessibilidade
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Cyan400),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("2. Ativar Serviço de Acessibilidade", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Testar Pop-up Nativo na tela real
                Button(
                    onClick = {
                        val intent = Intent(context, FloatingOverlayService::class.java).apply {
                            action = FloatingOverlayService.ACTION_SHOW_POPUP
                            putExtra("APP_NAME", "Uber")
                            putExtra("PRICE", 38.50)
                            putExtra("TOTAL_KM", 12.0)
                            putExtra("TOTAL_MIN", 22)
                            putExtra("PRICE_PER_KM", 3.20)
                            putExtra("PRICE_PER_HOUR", 52.50)
                            putExtra("VERDICT", "GREEN")
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("3. Testar Pop-up Flutuante na Tela Real", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

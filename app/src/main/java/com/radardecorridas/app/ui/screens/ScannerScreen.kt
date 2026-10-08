package com.radardecorridas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.model.RideData
import com.radardecorridas.app.model.RideVerdict
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.OcrParser
import com.radardecorridas.app.util.RideCalculator

@Composable
fun ScannerScreen(
    settings: DriverSettings,
    onSendToPhone: (RideData) -> Unit,
    modifier: Modifier = Modifier
) {
    var rawText by remember {
        mutableStateOf(
            """UberX
R$ 44,80
14,2 km no total
A 2,1 km (5 min) do embarque
Viagem: 12,1 km (22 min)
Av. Brigadeiro Luis Antonio -> Aeroporto de Congonhas"""
        )
    }

    val parsedRide = remember(rawText) {
        OcrParser.parseRideText(rawText)
    }

    val evaluation = remember(parsedRide, settings) {
        parsedRide?.let { RideCalculator.evaluateRide(it, settings) }
    }

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
                Text("Scanner de Texto da Tela (OCR)", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Cole o texto da tela da Uber ou 99 para simular o leitor de tela nativo:",
                    color = Slate400,
                    fontSize = 11.sp
                )

                // Presets Rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            rawText = """UberX
R$ 44,80
14,2 km no total (24 min)
Embarque a 2 km"""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Emerald400),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Exemplo 1 (Ótima)", fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            rawText = """99Pop
R$ 19,00
11,0 km (35 min)
Trânsito intenso"""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Amber400),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Exemplo 2 (Atenção)", fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            rawText = """UberX
R$ 15,20
14,0 km (48 min)
Prejuízo"""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Rose400),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Exemplo 3 (Ruim)", fontSize = 10.sp)
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = Slate200,
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Slate700
                    )
                )
            }
        }

        // Resultado da Análise
        if (evaluation != null && parsedRide != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Slate900)
                        .border(
                            1.dp,
                            when (evaluation.verdict) {
                                RideVerdict.GREEN -> Emerald500
                                RideVerdict.YELLOW -> Amber500
                                RideVerdict.RED -> Rose500
                            },
                            RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when (evaluation.verdict) {
                            RideVerdict.GREEN -> "🟢 NÍVEL VERDE: CORRIDA VALE A PENA"
                            RideVerdict.YELLOW -> "🟡 NÍVEL AMARELO: ATENÇÃO (1 CRITÉRIO)"
                            RideVerdict.RED -> "🔴 NÍVEL VERMELHO: RECUSAR (PREJUÍZO)"
                        },
                        color = when (evaluation.verdict) {
                            RideVerdict.GREEN -> Emerald400
                            RideVerdict.YELLOW -> Amber400
                            RideVerdict.RED -> Rose400
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(evaluation.verdictReason, color = Slate200, fontSize = 11.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate950)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("R$/KM", color = Slate400, fontSize = 9.sp)
                                Text("R$ %.2f/km".format(evaluation.pricePerKm), color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate950)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("R$/HORA", color = Slate400, fontSize = 9.sp)
                                Text("R$ %.2f/h".format(evaluation.pricePerHour), color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = { onSendToPhone(parsedRide) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Slate950),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ver Pop-up no Simulador", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

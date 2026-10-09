package com.radardecorridas.app.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.radardecorridas.app.model.DriverSettings
import com.radardecorridas.app.ui.theme.*
import com.radardecorridas.app.util.DiagnosticHelper
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun HomeScreen(
    settings: DriverSettings,
    onToggleRadar: (Boolean) -> Unit,
    onNavigateToSystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEnabled = settings.isEnabled
    val scrollState = rememberScrollState()

    // Estado do diagnóstico em tempo real
    var diagStatus by remember { mutableStateOf(DiagnosticHelper.checkStatus(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var showLogsExpanded by remember { mutableStateOf(false) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }

    // Reavalia o estado dos requisitos no exato momento do retorno das configurações (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                diagStatus = DiagnosticHelper.checkStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Atualiza diagnóstico periodicamente (1s) para refletir eventos da 99 assim que ocorrerem
    LaunchedEffect(Unit) {
        while (true) {
            diagStatus = DiagnosticHelper.checkStatus(context)
            delay(1000)
        }
    }

    // Função de verificação acionada no toque de ativar/desativar
    val handleActivateClick = {
        val currentStatus = DiagnosticHelper.checkStatus(context)
        diagStatus = currentStatus

        if (isEnabled) {
            // Desativar sempre é permitido imediatamente
            onToggleRadar(false)
        } else {
            // Se ativando: verifica se todos os pré-requisitos essenciais estão cumpridos
            val canActivate = currentStatus.hasOverlayPermission &&
                    currentStatus.isAccessibilityEnabledInSystem &&
                    currentStatus.isServiceConnected

            if (canActivate) {
                onToggleRadar(true)
            } else {
                // Abre o diálogo explicativo com diagnóstico e ações corretivas
                showDialog = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Nome / Identidade do Radar de Corridas
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text = "Radar de Corridas",
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Analisador Visual de Ofertas",
                color = Slate400,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 2. Estado Atual do Radar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Slate900)
                .border(
                    width = 1.5.dp,
                    color = if (isEnabled) Emerald500.copy(alpha = 0.6f) else Slate800,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Indicador visual circular
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) Emerald500.copy(alpha = 0.15f) else Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isEnabled) Emerald400 else Slate400,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Texto do estado
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isEnabled) "RADAR ATIVO" else "RADAR DESATIVADO",
                        color = if (isEnabled) Emerald400 else Slate200,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isEnabled)
                            "Pronto e monitorando ofertas de corrida em segundo plano"
                        else
                            "Toque no botão abaixo para testar requisitos e ativar",
                        color = Slate400,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. Botão Principal Grande (ATIVAR RADAR / DESATIVAR RADAR)
        Button(
            onClick = handleActivateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isEnabled) Rose500 else Emerald500,
                contentColor = White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isEnabled) "DESATIVAR RADAR" else "ATIVAR RADAR",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }

        // 4. Cartão de Diagnóstico Rápido em Tempo Real
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = Cyan400,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Status dos Pré-Requisitos",
                            color = White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

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

                Divider(color = Slate800, thickness = 1.dp)

                // Item 1: Sobreposição
                DiagnosticRow(
                    isOk = diagStatus.hasOverlayPermission,
                    title = "Sobreposição de Tela",
                    statusOkText = "Sobreposição permitida",
                    statusFailText = "Sobreposição não permitida",
                    actionLabel = if (!diagStatus.hasOverlayPermission) "Conceder" else null,
                    onAction = { DiagnosticHelper.openOverlaySettings(context) }
                )

                // Item 2: Acessibilidade Habilitada no Android
                DiagnosticRow(
                    isOk = diagStatus.isAccessibilityEnabledInSystem,
                    title = "Acessibilidade no Android",
                    statusOkText = "Serviço de acessibilidade ativo",
                    statusFailText = "Serviço de acessibilidade inativo",
                    actionLabel = if (!diagStatus.isAccessibilityEnabledInSystem) "Ativar" else null,
                    onAction = { DiagnosticHelper.openAccessibilitySettings(context) }
                )

                // Item 3: Serviço Realmente Conectado
                DiagnosticRow(
                    isOk = diagStatus.isServiceConnected,
                    title = "Comunicação com o App",
                    statusOkText = "Serviço conectado ao app",
                    statusFailText = "Serviço não responde (desconectado)",
                    actionLabel = if (!diagStatus.isServiceConnected) "Reiniciar" else null,
                    onAction = { DiagnosticHelper.openAccessibilitySettings(context) }
                )

                // Item 4: Diagnóstico de Acessibilidade em Tempo Real (99 / Todos os Apps)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Diagnóstico de Acessibilidade",
                                color = White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Eventos em tempo real da 99 e do sistema",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = if (diagStatus.isServiceConnected) "● CONECTADO" else "○ DESCONECTADO",
                            color = if (diagStatus.isServiceConnected) Emerald400 else Rose400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Status dos contadores globais e da 99
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Eventos do OS", color = Slate400, fontSize = 9.sp)
                            Text(
                                text = "${diagStatus.totalRawEvents}",
                                color = White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Eventos da 99", color = Slate400, fontSize = 9.sp)
                            Text(
                                text = "${diagStatus.total99Events}",
                                color = if (diagStatus.total99Events > 0) Emerald400 else Amber400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Último App", color = Slate400, fontSize = 9.sp)
                            Text(
                                text = diagStatus.lastSeenPackage?.substringAfterLast('.') ?: "nenhum",
                                color = Cyan400,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (diagStatus.lastSeenPackage != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate900.copy(alpha = 0.5f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Último pacote detectado: ${diagStatus.lastSeenPackage}",
                                color = Cyan400,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            if (diagStatus.lastEventPackage != null && diagStatus.lastEventTimestamp > 0) {
                                val formattedTime = DateFormat.format("HH:mm:ss", Date(diagStatus.lastEventTimestamp)).toString()
                                Text(
                                    text = "Último app de transporte monitorado: ${diagStatus.lastEventPackage} às $formattedTime",
                                    color = Emerald400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (diagStatus.isServiceConnected)
                                "Acessibilidade conectada aguardando eventos. Alterne para a 99 ou outro app."
                            else
                                "Serviço não conectado. Verifique o item 2 e 3 acima.",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }

                    // Botões de Ação de Diagnóstico e Visualização de Logs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showLogsExpanded = !showLogsExpanded },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showLogsExpanded) Emerald500.copy(alpha = 0.2f) else Slate800,
                                contentColor = if (showLogsExpanded) Emerald400 else White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (showLogsExpanded) "Ocultar Registros (${diagStatus.recentLogs.size})" else "Ver Registros Detalhados (${diagStatus.recentLogs.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val ok = DiagnosticHelper.copyDiagnosticsToClipboard(context, diagStatus)
                                copiedMessage = if (ok) "Copiado!" else "Erro"
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Slate800,
                                contentColor = Slate200
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = copiedMessage ?: "Copiar",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                DiagnosticHelper.clearLogs()
                                diagStatus = DiagnosticHelper.checkStatus(context)
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Slate800,
                                contentColor = Rose400
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Limpar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Lista de Logs Detalhados em Tempo Real
                    if (showLogsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate900)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LOGS DETALHADOS EM TEMPO REAL",
                                    color = Cyan400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Limite: 50",
                                    color = Slate400,
                                    fontSize = 9.sp
                                )
                            }

                            if (diagStatus.recentLogs.isEmpty()) {
                                Text(
                                    text = "Nenhum evento registrado ainda. Abra qualquer app ou a 99.",
                                    color = Slate400,
                                    fontSize = 10.sp
                                )
                            } else {
                                diagStatus.recentLogs.takeLast(12).reversed().forEachIndexed { index, log ->
                                    val timeStr = DateFormat.format("HH:mm:ss", Date(log.timestamp)).toString()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (log.is99App) Emerald950.copy(alpha = 0.4f) else Slate950)
                                            .border(
                                                width = 0.5.dp,
                                                color = if (log.is99App) Emerald500.copy(alpha = 0.6f) else Slate800,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "#${diagStatus.recentLogs.size - index} $timeStr [${log.eventType}]",
                                                color = if (log.is99App) Emerald400 else White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (log.is99App) "🎯 APP 99" else if (log.isTreeAvailable) "${log.nodeCount} textos" else "Árvore n/d",
                                                color = if (log.is99App) Emerald400 else if (log.isTreeAvailable) Cyan400 else Rose400,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = "Pacote: ${log.packageName}",
                                            color = Slate200,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        if (!log.className.isNullOrBlank()) {
                                            Text(
                                                text = "Classe: ${log.className}",
                                                color = Slate400,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        if (!log.eventTextSnippet.isNullOrEmpty()) {
                                            Text(
                                                text = "Texto do evento: ${log.eventTextSnippet}",
                                                color = Slate200,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Text(
                                            text = if (log.isTreeAvailable) "Árvore: \"${log.treeTextSnippet}\"" else "Árvore: ❌ Não disponível no momento do evento",
                                            color = if (log.isTreeAvailable) Slate400 else Rose400,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 3
                                        )

                                        if (log.nodeDetails.isNotEmpty()) {
                                            Text(
                                                text = "Elementos/IDs: " + log.nodeDetails.take(4).joinToString(" • "),
                                                color = Cyan400,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 2
                                            )
                                        }

                                        Text(
                                            text = log.parserVerdict,
                                            color = if (log.is99App) Emerald400 else Slate400,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Explicação Curta e Objetiva
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Slate900.copy(alpha = 0.6f))
                .border(1.dp, Slate800, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Como funciona",
                        color = White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O Radar analisa as ofertas de corrida e mostra classificação, R$/km e R$/h. Ele não aceita nem recusa corridas.",
                        color = Slate400,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 6. Atalho para permissões e configurações no Sistema
        OutlinedButton(
            onClick = onNavigateToSystem,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Slate900,
                contentColor = Slate200
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Slate800)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Cyan400,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Verificar Permissões no Sistema",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Diálogo de Pré-Requisitos Pendentes
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Slate900,
            titleContentColor = White,
            textContentColor = Slate200,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber400
                    )
                    Text("Configuração Necessária", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "O Radar não pôde ser ativado porque um ou mais pré-requisitos essenciais ainda não estão prontos:",
                        fontSize = 13.sp,
                        color = Slate400
                    )

                    // Diagnóstico 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (diagStatus.hasOverlayPermission) "✅ Sobreposição permitida" else "❌ Sobreposição não permitida",
                                color = if (diagStatus.hasOverlayPermission) Emerald400 else Rose400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!diagStatus.hasOverlayPermission) {
                            TextButton(onClick = {
                                DiagnosticHelper.openOverlaySettings(context)
                                showDialog = false
                            }) {
                                Text("Permitir", color = Cyan400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Diagnóstico 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (diagStatus.isAccessibilityEnabledInSystem) "✅ Serviço de acessibilidade ativo" else "❌ Serviço de acessibilidade inativo",
                                color = if (diagStatus.isAccessibilityEnabledInSystem) Emerald400 else Rose400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!diagStatus.isAccessibilityEnabledInSystem) {
                            TextButton(onClick = {
                                DiagnosticHelper.openAccessibilitySettings(context)
                                showDialog = false
                            }) {
                                Text("Ativar", color = Cyan400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Diagnóstico 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (diagStatus.isServiceConnected) "✅ Serviço conectado" else "❌ Serviço não conectado",
                                color = if (diagStatus.isServiceConnected) Emerald400 else Rose400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (diagStatus.isAccessibilityEnabledInSystem && !diagStatus.isServiceConnected) {
                                Text(
                                    text = "O Android pode ter congelado o processo. Desative e reative a acessibilidade.",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (!diagStatus.isServiceConnected) {
                            TextButton(onClick = {
                                DiagnosticHelper.openAccessibilitySettings(context)
                                showDialog = false
                            }) {
                                Text("Reabrir", color = Cyan400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Explicação de segurança
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate950)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Sem essas permissões o Radar não consegue observar as ofertas na tela da 99 nem exibir o pop-up informativo.",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        diagStatus = DiagnosticHelper.checkStatus(context)
                        if (diagStatus.hasOverlayPermission && diagStatus.isAccessibilityEnabledInSystem && diagStatus.isServiceConnected) {
                            showDialog = false
                            onToggleRadar(true)
                        } else {
                            showDialog = false
                        }
                    }
                ) {
                    Text("OK, Entendi", color = Emerald400, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun DiagnosticRow(
    isOk: Boolean,
    title: String,
    statusOkText: String,
    statusFailText: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Slate400,
                fontSize = 11.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isOk) "✅" else "❌",
                    fontSize = 12.sp
                )
                Text(
                    text = if (isOk) statusOkText else statusFailText,
                    color = if (isOk) Emerald400 else Rose400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (actionLabel != null) {
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Slate800,
                    contentColor = Cyan400
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

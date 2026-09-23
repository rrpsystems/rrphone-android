package com.rrpsystems.rrphone.ui.screens.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.ui.components.Dialpad
import com.rrpsystems.rrphone.ui.components.PillButton
import com.rrpsystems.rrphone.ui.components.rememberElapsed
import com.rrpsystems.rrphone.ui.screens.contacts.ContactPickerDialog
import com.rrpsystems.rrphone.ui.screens.dialer.dtmfTone
import com.rrpsystems.rrphone.ui.screens.dialer.rememberToneGenerator
import com.rrpsystems.rrphone.ui.theme.Rrp

/**
 * Transferência numa tela própria, com o mesmo fluxo único do desktop:
 *
 * 1. escolhe o destino e toca "Chamar": quem está na linha vai para a espera
 *    e o destino é chamado;
 * 2. "Transferir" conclui. Se o destino já atendeu, é transferência com
 *    consulta; se ainda está chamando, vira transferência cega — então
 *    discar e transferir em seguida é o jeito de transferir sem anunciar;
 * 3. "Cancelar" desliga a consulta e retoma quem estava em espera.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransferScreen(onDone: () -> Unit) {
    val ui by CallManager.ui.collectAsState()
    val transfer = ui.transfer
    var number by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    val tone = rememberToneGenerator()

    // Consulta iniciada e depois encerrada (concluída, cancelada, destino
    // desligou): o fluxo acabou, volta para a chamada.
    var started by remember { mutableStateOf(transfer != null) }
    LaunchedEffect(transfer) {
        if (transfer != null) started = true
        else if (started) onDone()
    }

    BackHandler {
        if (transfer != null) CallManager.cancelTransfer() else onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Rrp.Background)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (transfer != null) CallManager.cancelTransfer() else onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Rrp.TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Transferir chamada", color = Rrp.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    "de ${ui.party?.label ?: ""} · em espera durante a consulta",
                    color = Rrp.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (transfer == null) {
            // Passo 1: escolher o destino.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    number.ifEmpty { "Destino" },
                    fontSize = 36.sp, fontWeight = FontWeight.Light,
                    color = if (number.isEmpty()) Rrp.TextSecondary.copy(alpha = 0.5f) else Rrp.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.StartEllipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { picker = true }) {
                    Icon(Icons.Default.Contacts, null, tint = Rrp.AccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Contatos", color = Rrp.AccentBlue)
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                        enabled = number.isNotEmpty(),
                        onClick = { number = number.dropLast(1) },
                        onLongClick = { number = "" }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (number.isNotEmpty()) Icon(Icons.AutoMirrored.Outlined.Backspace, "Apagar", tint = Rrp.TextSecondary)
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Dialpad(onDigit = { d -> number += d; tone?.startTone(dtmfTone(d), 120) })
                Spacer(Modifier.height(20.dp))
                PillButton(
                    text = "Chamar", icon = Icons.Default.Call, color = Rrp.AccentBlue,
                    enabled = number.isNotEmpty(),
                    onClick = { CallManager.beginTransfer(number) },
                    modifier = Modifier.widthIn(min = 160.dp)
                )
            }
        } else {
            // Passo 2: consultando.
            val elapsed = rememberElapsed(transfer.connectedAt)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Consultando", color = Rrp.TextSecondary, fontSize = 14.sp)
                Text(
                    transfer.target, color = Rrp.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    if (transfer.answered && elapsed != null) "Em conversa · $elapsed" else transfer.label,
                    color = if (transfer.answered) Rrp.Green else Rrp.TextSecondary, fontSize = 16.sp
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    if (transfer.answered) "Transferir entrega ${ui.party?.label ?: "a chamada"} a ${transfer.target}."
                    else "Se transferir agora, a chamada vai direto, sem anúncio.",
                    color = Rrp.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PillButton(
                    text = "Cancelar", icon = Icons.Default.Close, color = Rrp.Red,
                    onClick = CallManager::cancelTransfer, modifier = Modifier.weight(1f)
                )
                PillButton(
                    text = "Transferir", icon = Icons.AutoMirrored.Filled.PhoneForwarded, color = Rrp.AccentBlue,
                    onClick = CallManager::completeTransfer, modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (picker) {
        ContactPickerDialog(
            onPick = { number = it; picker = false },
            onDismiss = { picker = false }
        )
    }
}

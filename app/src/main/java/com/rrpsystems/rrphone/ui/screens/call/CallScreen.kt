package com.rrpsystems.rrphone.ui.screens.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
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
import com.rrpsystems.rrphone.core.sip.AudioRoute
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.CallPhase
import com.rrpsystems.rrphone.ui.components.BigCircleButton
import com.rrpsystems.rrphone.ui.components.Dialpad
import com.rrpsystems.rrphone.ui.components.RoundActionButton
import com.rrpsystems.rrphone.ui.components.rememberElapsed
import com.rrpsystems.rrphone.ui.theme.Rrp

/**
 * Tela de chamada. As ações moram numa grade 3x2 de botões redondos, fora da
 * tela principal (no desktop elas ficam na própria janela). Transferir abre
 * uma tela à parte.
 */
@Composable
fun CallScreen(
    onOpenTransfer: () -> Unit,
    onBackToTabs: () -> Unit,
) {
    val ui by CallManager.ui.collectAsState()
    var showKeypad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var routeMenu by remember { mutableStateOf(false) }

    // Voltar não desliga: leva às abas, com a faixa "voltar à chamada".
    BackHandler {
        if (showKeypad) showKeypad = false else onBackToTabs()
    }

    val elapsed = rememberElapsed(ui.connectedAt)
    val party = ui.party
    val inCall = ui.phase == CallPhase.Active
    val canTransfer = inCall && ui.waiting == null && ui.parked == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Rrp.Background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            party?.label ?: "",
            fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Rrp.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (party != null && party.name.isNotBlank()) {
            Text(party.number, fontSize = 16.sp, color = Rrp.TextSecondary)
        }
        if (ui.dialed.isNotEmpty()) {
            Text("discado: ${ui.dialed}", fontSize = 13.sp, color = Rrp.TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                ui.held -> "Em espera"
                ui.phase == CallPhase.Active && elapsed != null && ui.statusLabel == "Em chamada" -> elapsed
                else -> ui.statusLabel
            },
            fontSize = 16.sp,
            color = if (ui.held) Rrp.Amber else Rrp.TextSecondary
        )
        if (ui.muted) {
            Text("Microfone mudo", fontSize = 13.sp, color = Rrp.Red, modifier = Modifier.padding(top = 4.dp))
        }

        ui.parked?.let {
            Spacer(Modifier.height(16.dp))
            InfoChip("Em espera: ${it.label}")
        }
        ui.transfer?.let {
            Spacer(Modifier.height(16.dp))
            InfoChip("Transferência para ${it.target} · ${it.label}", onClick = onOpenTransfer)
        }

        ui.waiting?.let { waiting ->
            Spacer(Modifier.height(20.dp))
            WaitingCallCard(
                who = waiting.label,
                onAnswer = CallManager::answerWaitingCall,
                onDecline = CallManager::declineWaitingCall,
            )
        }

        Spacer(Modifier.weight(1f))

        when {
            ui.phase == CallPhase.Incoming -> Unit
            showKeypad -> {
                Text(
                    dtmfDigits.ifEmpty { " " }, fontSize = 28.sp, color = Rrp.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))
                Dialpad(
                    onDigit = { d -> dtmfDigits += d; CallManager.sendDtmf(d) },
                    keyHeight = 54.dp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                TextButton(onClick = { showKeypad = false }) { Text("Ocultar teclado", color = Rrp.TextSecondary) }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    RoundActionButton("Mudo", if (ui.muted) Icons.Default.MicOff else Icons.Default.Mic,
                        onClick = { CallManager.setMuted(!ui.muted) }, active = ui.muted)
                    RoundActionButton("Teclado", Icons.Default.Dialpad, onClick = { showKeypad = true })
                    RoundActionButton("Espera", Icons.Default.Pause,
                        onClick = { CallManager.setHeld(!ui.held) }, active = ui.held,
                        enabled = inCall && ui.waiting == null)
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        val choosable = ui.availableRoutes.any { it == AudioRoute.Bluetooth || it == AudioRoute.Headset }
                        RoundActionButton(
                            label = if (choosable) ui.audioRoute.label else "Alto-falante",
                            icon = when (ui.audioRoute) {
                                AudioRoute.Bluetooth -> Icons.Default.Bluetooth
                                AudioRoute.Headset -> Icons.Default.Headset
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            active = ui.audioRoute == AudioRoute.Speaker,
                            onClick = {
                                if (choosable) routeMenu = true
                                else CallManager.setAudioRoute(
                                    if (ui.audioRoute == AudioRoute.Speaker) AudioRoute.Earpiece else AudioRoute.Speaker
                                )
                            }
                        )
                        DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }, containerColor = Rrp.Panel) {
                            ui.availableRoutes.forEach { route ->
                                DropdownMenuItem(
                                    text = { Text(route.label + if (route == ui.audioRoute) "  ✓" else "") },
                                    onClick = { routeMenu = false; CallManager.setAudioRoute(route) }
                                )
                            }
                        }
                    }
                    RoundActionButton("Transferir", Icons.AutoMirrored.Filled.PhoneForwarded,
                        onClick = onOpenTransfer, enabled = canTransfer || ui.transfer != null)
                    RoundActionButton("Alternar", Icons.Default.SwapCalls,
                        onClick = CallManager::swapCalls, enabled = ui.parked != null)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        if (ui.phase == CallPhase.Incoming) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                LabeledCircle("Recusar") { BigCircleButton(Icons.Default.CallEnd, Rrp.Red, "Recusar", CallManager::decline) }
                LabeledCircle("Atender") { BigCircleButton(Icons.Default.Call, Rrp.Green, "Atender", CallManager::answer) }
            }
        } else {
            BigCircleButton(Icons.Default.CallEnd, Rrp.Red, "Desligar", CallManager::hangUp)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LabeledCircle(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(8.dp))
        Text(label, color = Rrp.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun InfoChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        fontSize = 13.sp,
        color = Rrp.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(Rrp.Button)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Segunda chamada tocando: a conversa atual segue intacta até o usuário decidir. */
@Composable
private fun WaitingCallCard(who: String, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Rrp.Panel)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Chamada em espera", fontSize = 13.sp, color = Rrp.TextSecondary)
        Text(who, fontSize = 20.sp, color = Rrp.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Atender coloca a conversa atual em espera",
            fontSize = 12.sp, color = Rrp.TextSecondary, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDecline, colors = ButtonDefaults.buttonColors(containerColor = Rrp.Red)) {
                Text("Recusar")
            }
            Button(onClick = onAnswer, colors = ButtonDefaults.buttonColors(containerColor = Rrp.Green)) {
                Text("Atender")
            }
        }
    }
}

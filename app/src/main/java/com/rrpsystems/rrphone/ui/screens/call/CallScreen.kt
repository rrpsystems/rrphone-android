package com.rrpsystems.rrphone.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.core.sip.CallManager
import org.linphone.core.Call

@Composable
fun CallScreen(onCallEnded: () -> Unit) {
    val callState by CallManager.callState.collectAsState()
    val currentCall by CallManager.currentCall.collectAsState()

    var callDuration by remember { mutableStateOf(0) }

    LaunchedEffect(callState) {
        if (callState == Call.State.End || callState == Call.State.Released || callState == Call.State.Error) {
            onCallEnded()
        }
    }

    LaunchedEffect(callState) {
        if (callState == Call.State.StreamsRunning) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callDuration++
            }
        } else if (callState == Call.State.End || callState == Call.State.Released) {
            callDuration = 0
        }
    }

    val phoneNumber = currentCall?.remoteAddress?.username ?: "Desconhecido"
    
    val formattedDuration = String.format("%02d:%02d", callDuration / 60, callDuration % 60)
    
    val stateText = when (callState) {
        Call.State.OutgoingInit, Call.State.OutgoingProgress -> "Chamando..."
        Call.State.OutgoingRinging -> "Chamando..."
        Call.State.IncomingReceived -> "Recebendo chamada..."
        Call.State.StreamsRunning -> formattedDuration
        Call.State.Paused -> "Em Espera"
        Call.State.End, Call.State.Released -> "Encerrada"
        else -> "Conectando..."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF202124)), // Fundo escuro nativo
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // Info do Contato
        Text(
            text = phoneNumber,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stateText,
            fontSize = 16.sp,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.weight(1f))

        // Painel de Botões Avançados (Fase C.1)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Linha 1: Mute, Teclado, Espera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallActionButton("Mudo", Icons.Default.MicOff) { CallManager.toggleMute() }
                CallActionButton("Teclado", Icons.Default.Dialpad) { /* Abrir modal de DTMF */ }
                CallActionButton("Espera", Icons.Default.Pause) { CallManager.toggleHold() }
            }

            // Linha 2: Add (Conferência), Transf. Cega, Transf. Assistida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallActionButton("Adicionar", Icons.Default.AddCircle) { /* Fase Futura */ }
                CallActionButton("Transf. Cega", Icons.Default.PhoneForwarded) { /* Transf Cega */ }
                CallActionButton("Transf. Assis.", Icons.Default.SwapCalls) { /* Transf Supervisionada */ }
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        // Botão de Desligar / Atender
        if (callState == Call.State.IncomingReceived) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Desligar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .clickable { CallManager.hangUp() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Recusar", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                
                // Atender
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .clickable { CallManager.acceptCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Atender", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        } else {
            // Apenas botão de desligar para chamadas em andamento
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
                    .clickable { CallManager.hangUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Desligar", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun CallActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF3C4043)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

package com.rrpsystems.rrphone.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.core.contacts.ContactsRepository
import com.rrpsystems.rrphone.core.history.CallHistoryStore
import com.rrpsystems.rrphone.core.history.CallRecord
import com.rrpsystems.rrphone.ui.components.Routes
import com.rrpsystems.rrphone.ui.components.TabScaffold
import com.rrpsystems.rrphone.ui.screens.dialer.rememberPlaceCall
import com.rrpsystems.rrphone.ui.theme.Rrp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Histórico local (D-08): recebidas, efetuadas e perdidas, a mais recente no topo. */
@Composable
fun HistoryScreen(onNavigate: (String) -> Unit, onReturnToCall: () -> Unit) {
    val records by CallHistoryStore.records.collectAsState()
    val remote by ContactsRepository.remote.collectAsState()
    val local by ContactsRepository.local.collectAsState()
    // O PBX nem sempre manda nome; a agenda completa o que faltar.
    val names = remember(remote, local) {
        (remote + local).filter { it.number.isNotBlank() }.associate { it.number to it.name }
    }
    val placeCall = rememberPlaceCall()
    var confirmClear by remember { mutableStateOf(false) }

    TabScaffold(currentRoute = Routes.HISTORY, onNavigate = onNavigate, onReturnToCall = onReturnToCall) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Histórico", color = Rrp.TextPrimary, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.DeleteSweep, "Limpar histórico", tint = Rrp.TextSecondary)
                }
            }
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma chamada registrada ainda.", color = Rrp.TextSecondary, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(records) { record ->
                    HistoryRow(record, names[record.peer], onCall = { placeCall(record.peer) })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Rrp.Panel,
            title = { Text("Limpar histórico") },
            text = { Text("Apagar todas as chamadas registradas?") },
            confirmButton = {
                TextButton(onClick = { CallHistoryStore.clear(); confirmClear = false }) { Text("Apagar", color = Rrp.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryRow(record: CallRecord, contactName: String?, onCall: () -> Unit) {
    val (icon, color) = when {
        record.incoming && record.answered -> Icons.AutoMirrored.Filled.CallReceived to Rrp.Green
        // Atendida/recusada em outro aparelho do mesmo ramal: não é perdida.
        record.incoming && record.note.contains("outro aparelho") ->
            Icons.AutoMirrored.Filled.CallReceived to Rrp.TextSecondary
        record.incoming -> Icons.AutoMirrored.Filled.CallMissed to Rrp.Red
        else -> Icons.AutoMirrored.Filled.CallMade to (if (record.answered) Rrp.AccentBlue else Rrp.TextSecondary)
    }
    val title = record.displayName.ifBlank { contactName ?: "" }.ifBlank { record.peer }
    val detail = buildList {
        add(formatWhen(record.startedAt))
        if (record.answered) add(formatDuration(record.durationSeconds))
        if (record.note.isNotBlank()) add(record.note)
    }.joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (title != record.peer) "$title (${record.peer})" else title,
                color = if (record.incoming && !record.answered && !record.note.contains("outro aparelho")) Rrp.Red
                else Rrp.TextPrimary,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(detail, color = Rrp.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (record.peer.isNotBlank()) {
            IconButton(onClick = onCall) { Icon(Icons.Default.Call, "Ligar para ${record.peer}", tint = Rrp.Green) }
        }
    }
}

private fun formatWhen(millis: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "dd/MM HH:mm"
    return SimpleDateFormat(pattern, Locale("pt", "BR")).format(Date(millis))
}

private fun formatDuration(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

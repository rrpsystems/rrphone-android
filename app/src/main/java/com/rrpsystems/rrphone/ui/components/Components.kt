package com.rrpsystems.rrphone.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.core.settings.SettingsStore
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.CallPhase
import com.rrpsystems.rrphone.core.sip.LinphoneManager
import com.rrpsystems.rrphone.core.sip.Registration
import com.rrpsystems.rrphone.ui.theme.Rrp
import kotlinx.coroutines.delay

object Routes {
    const val DIALER = "dialer"
    const val CONTACTS = "contacts"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * Esqueleto das quatro abas: linha de status no topo, faixa "voltar à
 * chamada" quando há uma em curso, navegação fixa embaixo.
 */
@Composable
fun TabScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onReturnToCall: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui by CallManager.ui.collectAsState()
    Scaffold(
        containerColor = Rrp.Background,
        topBar = { StatusTopBar() },
        bottomBar = { RrpBottomNav(currentRoute, onNavigate) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Rrp.Background)
        ) {
            if (ui.phase != CallPhase.Idle) {
                ReturnToCallBanner(ui.party?.label ?: "", ui.statusLabel, ui.connectedAt, onReturnToCall)
            }
            content()
        }
    }
}

/** Presença + ramal. Mostra não perturbe e siga-me aqui, só como estado. */
@Composable
fun StatusTopBar() {
    val registration by LinphoneManager.registration.collectAsState()
    val dnd by CallManager.doNotDisturb.collectAsState()
    val forward by CallManager.forwardTarget.collectAsState()

    val (color, text) = when {
        forward.isNotEmpty() -> Rrp.AccentBlue to "Siga-me → $forward"
        dnd -> Rrp.Red to "Não perturbe"
        registration is Registration.Ok -> Rrp.Green to "Disponível"
        registration is Registration.Progress -> Rrp.Amber to "Registrando..."
        registration is Registration.Failed -> Rrp.Red to "Falha no registro"
        else -> Rrp.TextSecondary to "Offline"
    }
    val profile = remember(registration) { SettingsStore.loadProfile() }
    val account = profile?.let { it.displayName.ifBlank { "Ramal ${it.username}" } } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Rrp.Background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(account, color = Rrp.TextSecondary, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun ReturnToCallBanner(who: String, status: String, connectedAt: Long?, onClick: () -> Unit) {
    val elapsed = rememberElapsed(connectedAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Rrp.GreenMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Call, null, tint = Rrp.TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "$who · ${elapsed ?: status}",
            color = Rrp.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text("Voltar à chamada", color = Rrp.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RrpBottomNav(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = Rrp.Panel, tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Routes.DIALER, Icons.Default.Dialpad, "Teclado"),
            Triple(Routes.CONTACTS, Icons.Default.Person, "Contatos"),
            Triple(Routes.HISTORY, Icons.Default.History, "Histórico"),
            Triple(Routes.SETTINGS, Icons.Default.Settings, "Ajustes"),
        )
        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { if (currentRoute != route) onNavigate(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Rrp.TextPrimary,
                    selectedTextColor = Rrp.TextPrimary,
                    unselectedIconColor = Rrp.TextSecondary,
                    unselectedTextColor = Rrp.TextSecondary,
                    indicatorColor = Rrp.ButtonPressed
                )
            )
        }
    }
}

// --- Teclado -----------------------------------------------------------------

private val keyRows = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

/**
 * Teclado com teclas arredondadas (pílula), letras sob os números.
 * Segurar o 0 digita "+".
 */
@Composable
fun Dialpad(
    onDigit: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 58.dp,
    onLongZero: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keyRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (digit, letters) ->
                    DialKey(
                        digit = digit,
                        letters = letters,
                        height = keyHeight,
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(digit[0]) },
                        onLongClick = if (digit == "0" && onLongZero != null) onLongZero else null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    letters: String,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Rrp.Button)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(digit, fontSize = 24.sp, color = Rrp.TextPrimary, lineHeight = 26.sp)
        if (letters.isNotEmpty()) {
            Text(letters, fontSize = 9.sp, color = Rrp.TextSecondary, letterSpacing = 1.sp, lineHeight = 10.sp)
        }
    }
}

/** Botão principal em pílula (Ligar, Transferir...). */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

/** Botão redondo com rótulo, da grade da tela de chamada. */
@Composable
fun RoundActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 64.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (active) Rrp.TextPrimary else Rrp.Button)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = label,
                tint = when {
                    !enabled -> Rrp.TextSecondary.copy(alpha = 0.4f)
                    active -> Rrp.Background
                    else -> Rrp.TextPrimary
                },
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label, fontSize = 12.sp,
            color = if (enabled) Rrp.TextPrimary else Rrp.TextSecondary.copy(alpha = 0.5f),
            maxLines = 1
        )
    }
}

/** Botão circular grande colorido (atender / desligar). */
@Composable
fun BigCircleButton(icon: ImageVector, color: Color, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

/** "mm:ss" desde [since] (elapsedRealtime), atualizado a cada segundo. */
@Composable
fun rememberElapsed(since: Long?): String? {
    if (since == null) return null
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun PassphraseDialog(
    title: String,
    message: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rrp.Panel,
        title = { Text(title) },
        text = {
            Column {
                Text(message, color = Rrp.TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("Senha do arquivo") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotEmpty()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

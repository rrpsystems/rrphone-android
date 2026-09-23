package com.rrpsystems.rrphone.ui.screens.dialer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import kotlinx.coroutines.awaitCancellation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rrpsystems.rrphone.R
import com.rrpsystems.rrphone.core.history.CallHistoryStore
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.CallPhase
import com.rrpsystems.rrphone.ui.components.Dialpad
import com.rrpsystems.rrphone.ui.components.PillButton
import com.rrpsystems.rrphone.ui.components.Routes
import com.rrpsystems.rrphone.ui.components.TabScaffold
import com.rrpsystems.rrphone.ui.theme.Rrp

/** Tom local de cada tecla, como no desktop: sem ele o teclado parece morto. */
fun dtmfTone(digit: Char): Int = when (digit) {
    '1' -> ToneGenerator.TONE_DTMF_1; '2' -> ToneGenerator.TONE_DTMF_2; '3' -> ToneGenerator.TONE_DTMF_3
    '4' -> ToneGenerator.TONE_DTMF_4; '5' -> ToneGenerator.TONE_DTMF_5; '6' -> ToneGenerator.TONE_DTMF_6
    '7' -> ToneGenerator.TONE_DTMF_7; '8' -> ToneGenerator.TONE_DTMF_8; '9' -> ToneGenerator.TONE_DTMF_9
    '*' -> ToneGenerator.TONE_DTMF_S; '#' -> ToneGenerator.TONE_DTMF_P
    else -> ToneGenerator.TONE_DTMF_0
}

/**
 * O que dá para discar num texto colado: "(13) 3219-1212", "+55 13 3219 1212"
 * ou "Ramal 2130" viram só dígitos, *, # e o + inicial. Texto sem nenhum
 * dígito não vira número.
 */
fun dialableFrom(text: String): String {
    val trimmed = text.trim()
    val kept = trimmed.filter { it.isDigit() || it == '*' || it == '#' }
    if (kept.none { it.isDigit() }) return ""
    return if (trimmed.startsWith("+")) "+$kept" else kept
}

@Composable
fun rememberToneGenerator(): ToneGenerator? {
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    return tone
}

/**
 * Discar pedindo a permissão do microfone na hora, se ainda não houver —
 * usado pelo teclado, contatos e histórico.
 */
@Composable
fun rememberPlaceCall(): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pending
        pending = null
        if (granted && target != null) CallManager.dial(context, target)
    }
    return { target ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            CallManager.dial(context, target)
        } else {
            pending = target
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DialerScreen(
    onNavigate: (String) -> Unit,
    onReturnToCall: () -> Unit,
    prefill: String = "",
    onPrefillConsumed: () -> Unit = {},
) {
    // O número com cursor e seleção. `number` é só o texto, para o resto da tela.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val number = field.text
    var clipboardMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val tone = rememberToneGenerator()
    val ui by CallManager.ui.collectAsState()
    val hookMessage by CallManager.hookMessage.collectAsState()
    val dnd by CallManager.doNotDisturb.collectAsState()
    val forward by CallManager.forwardTarget.collectAsState()
    val history by CallHistoryStore.records.collectAsState()
    val lastDialed = remember(history) { CallHistoryStore.lastDialedNumber() }

    // Número vindo de Contatos/Histórico ("ligar de novo" pelo teclado).
    LaunchedEffect(prefill) {
        if (prefill.isNotEmpty()) {
            field = DialField.of(prefill)
            onPrefillConsumed()
        }
    }

    val placeCall = rememberPlaceCall()

    fun dial() {
        val target = number.trim()
        if (target.isEmpty()) {
            // Rediscar: o botão com o visor vazio só traz o último número; a
            // ligação exige um segundo toque. Trazer por engano não faz mal,
            // discar por engano faz. Mesmo comportamento do desktop.
            if (lastDialed.isNotEmpty()) field = DialField.of(lastDialed)
            return
        }
        placeCall(target)
    }

    // Número usado vira histórico; limpa o visor quando a ligação sai.
    LaunchedEffect(ui.phase) {
        if (ui.phase == CallPhase.Outgoing) field = TextFieldValue()
    }

    val hookText = buildString {
        append(
            when {
                hookMessage.isNotEmpty() -> hookMessage
                ui.phase != CallPhase.Idle -> ui.statusLabel
                else -> "No gancho"
            }
        )
        if (forward.isNotEmpty()) append(" · Siga-me → $forward")
        else if (dnd) append(" · Não perturbe")
    }

    TabScaffold(currentRoute = Routes.DIALER, onNavigate = onNavigate, onReturnToCall = onReturnToCall) {
        // Visor: logo quando vazio; com número, um campo editável — tocar põe o
        // cursor, os dígitos entram nele e o apagar remove o que está antes,
        // como no campo do desktop. Segurar o número abre a barra do Android
        // (colar, copiar, selecionar); segurar a logo abre o nosso Colar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (number.isEmpty()) Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = { clipboardMenu = true }
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (number.isEmpty()) {
                Image(
                    painter = painterResource(id = R.drawable.logo_rrp_clean),
                    contentDescription = "RRP Systems",
                    modifier = Modifier.size(120.dp)
                )
            } else {
                // Sem teclado virtual: quem digita é o teclado do app. O campo
                // só serve para cursor, seleção e a barra de colar/copiar.
                InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = DialField.sanitize(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = if (number.length > 12) 30.sp else 38.sp,
                            fontWeight = FontWeight.Light,
                            color = Rrp.TextPrimary,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(Rrp.AccentBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = clipboardMenu,
                onDismissRequest = { clipboardMenu = false },
                containerColor = Rrp.Panel
            ) {
                val pasted = clipboard.getText()?.text?.let(::dialableFrom).orEmpty()
                DropdownMenuItem(
                    text = { Text(if (pasted.isEmpty()) "Colar" else "Colar  $pasted") },
                    enabled = pasted.isNotEmpty(),
                    onClick = {
                        clipboardMenu = false
                        field = DialField.of(pasted)
                        CallManager.clearHookMessage()
                    }
                )
                if (number.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Copiar") },
                        onClick = {
                            clipboardMenu = false
                            clipboard.setText(AnnotatedString(number))
                        }
                    )
                }
            }
        }

        // Linha de gancho: situação + modos ativos, e o apagar à direita.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                hookText,
                color = if (hookMessage.isNotEmpty()) Rrp.Amber else Rrp.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = number.isNotEmpty(),
                        onClick = { field = DialField.backspace(field) },
                        onLongClick = { field = TextFieldValue() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Apagar", tint = Rrp.TextSecondary)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Dialpad(
                onDigit = { d ->
                    field = DialField.insert(field, d.toString())
                    tone?.startTone(dtmfTone(d), 120)
                    CallManager.clearHookMessage()
                },
                onLongZero = { field = DialField.insert(field, "+") },
            )
            Spacer(Modifier.height(20.dp))
            PillButton(
                text = if (number.isEmpty() && lastDialed.isNotEmpty()) "Rediscar" else "Ligar",
                icon = Icons.Default.Call,
                color = Rrp.Green,
                enabled = ui.phase == CallPhase.Idle && (number.isNotEmpty() || lastDialed.isNotEmpty()),
                onClick = ::dial,
                modifier = Modifier.widthIn(min = 160.dp)
            )
        }
    }
}

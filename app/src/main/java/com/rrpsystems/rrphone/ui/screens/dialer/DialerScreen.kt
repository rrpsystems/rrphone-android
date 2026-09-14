package com.rrpsystems.rrphone.ui.screens.dialer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Backspace
import com.rrpsystems.rrphone.core.sip.CallManager

@Composable
fun DialerScreen(
    // No futuro, passaremos um ViewModel para observar o status do Linphone
) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    
    // Gerador de Tom para o Teclado
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }

    // Launcher para solicitar permissões (Microfone e Notificações)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (audioGranted) {
            CallManager.makeCall(context, phoneNumber)
        } else {
            // Em produção, exibir um aviso de que precisa do mic
        }
    }
    
    // Pedir permissão de notificação no boot da tela (Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
    
    // Mock do status de registro (Online/Offline)
    val isRegistered = true

    Scaffold(
        topBar = {
            DialerTopBar(isRegistered = isRegistered)
        },
        bottomBar = {
            DialerBottomNav()
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            // Área superior (Branca) - Onde o número digitado aparece
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = phoneNumber.ifEmpty { " " },
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // Área inferior (Cinza Claro) - Teclado Compacto
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF2F4F7)) // Cinza bem clarinho
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                // Botão de apagar (Alinhado à direita)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (phoneNumber.isNotEmpty()) {
                        IconButton(onClick = { phoneNumber = phoneNumber.dropLast(1) }) {
                            Icon(
                                imageVector = Icons.Outlined.Backspace,
                                contentDescription = "Apagar",
                                tint = Color.Gray
                            )
                        }
                    } else {
                        // Espaçador para manter a altura
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }

                // Teclado Numérico Compacto
                Dialpad(
                    onDigitClick = { digit -> 
                        phoneNumber += digit
                        // Tocar som do DTMF correspondente
                        val tone = when (digit) {
                            "1" -> ToneGenerator.TONE_DTMF_1
                            "2" -> ToneGenerator.TONE_DTMF_2
                            "3" -> ToneGenerator.TONE_DTMF_3
                            "4" -> ToneGenerator.TONE_DTMF_4
                            "5" -> ToneGenerator.TONE_DTMF_5
                            "6" -> ToneGenerator.TONE_DTMF_6
                            "7" -> ToneGenerator.TONE_DTMF_7
                            "8" -> ToneGenerator.TONE_DTMF_8
                            "9" -> ToneGenerator.TONE_DTMF_9
                            "0" -> ToneGenerator.TONE_DTMF_0
                            "*" -> ToneGenerator.TONE_DTMF_S
                            "#" -> ToneGenerator.TONE_DTMF_P
                            else -> ToneGenerator.TONE_DTMF_0
                        }
                        toneGenerator.startTone(tone, 150)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botão de Chamar em formato de pílula (Pill Shape)
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .width(140.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF2E7D32)) // Verde escuro estilo iOS/Google Voice
                        .clickable { 
                            if (phoneNumber.isNotEmpty()) {
                                // Verificar permissão de áudio antes de ligar
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    CallManager.makeCall(context, phoneNumber)
                                } else {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Chamar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ligar",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DialerTopBar(isRegistered: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F9FC))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isRegistered) Color(0xFF4CAF50) else Color.Red)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRegistered) "Ramal Online" else "Desconectado",
                fontWeight = FontWeight.Medium,
                color = if (isRegistered) Color(0xFF4CAF50) else Color.Red,
                fontSize = 14.sp
            )
        }

        Text(
            text = "RRP",
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50),
            fontSize = 18.sp
        )
    }
}

@Composable
fun Dialpad(onDigitClick: (String) -> Unit) {
    val rows = listOf(
        listOf("1" to "oo", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    DialpadButton(digit, letters) { onDigitClick(digit) }
                }
            }
        }
    }
}

@Composable
fun RowScope.DialpadButton(digit: String, letters: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(58.dp)
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(50)) // Formato oval/pílula
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                modifier = Modifier.offset(y = if (letters.isNotEmpty()) 2.dp else 0.dp)
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(y = (-2).dp)
                )
            }
        }
    }
}

@Composable
fun DialerBottomNav() {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Default.Person, contentDescription = "Contatos") },
            label = { Text("Contatos") }
        )
        NavigationBarItem(
            selected = true,
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Default.Call, contentDescription = "Teclado") },
            label = { Text("Teclado") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
            label = { Text("Ajustes") }
        )
    }
}

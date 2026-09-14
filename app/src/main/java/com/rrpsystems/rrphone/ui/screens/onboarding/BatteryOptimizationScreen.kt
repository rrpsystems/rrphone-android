package com.rrpsystems.rrphone.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun BatteryOptimizationScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName

    // Estado que monitora se a permissão já foi concedida
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(packageName))
    }

    // Polling contínuo (a cada 2 segundos) para checar se o usuário concedeu a permissão fora do app
    LaunchedEffect(Unit) {
        while (!isIgnoringBatteryOptimizations) {
            delay(2000)
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC)), // Fundo claro neutro
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isIgnoringBatteryOptimizations) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Sucesso",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tudo Certo!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "O aplicativo está pronto para receber chamadas a qualquer momento.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = "Aviso de Bateria",
                        tint = Color(0xFFE67E22),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Atenção Necessária",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Para receber chamadas com a tela do celular desligada, precisamos que você permita o app funcionar em segundo plano.",
                        textAlign = TextAlign.Center,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Na próxima tela, selecione 'Permitir' ou desligue a 'Otimização de Bateria'.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:$packageName")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C5364))
                    ) {
                        Text("Configurar Agora", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onContinue) {
                        Text("Pular por enquanto (Não Recomendado)", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

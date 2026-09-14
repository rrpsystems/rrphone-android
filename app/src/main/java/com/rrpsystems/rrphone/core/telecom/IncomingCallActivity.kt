package com.rrpsystems.rrphone.core.telecom

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.MainActivity
import com.rrpsystems.rrphone.core.sip.CallManager
import org.linphone.core.Call

/**
 * Activity dedicada 100% a acender a tela do celular quando está bloqueado.
 * Isolada do NavGraph principal para evitar reiniciar o app e cair no Login.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Força a tela a acender e ultrapassar a tela de bloqueio com senha
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Desconhecido"

        setContent {
            val callState by CallManager.callState.collectAsState()

            // Se o chamador desligar antes de atendermos, a tela fecha sozinha
            LaunchedEffect(callState) {
                if (callState == Call.State.End || callState == Call.State.Released || callState == Call.State.Error) {
                    finish()
                }
                // Se por algum motivo o estado já for streams running, significa que atendeu de outro lugar
                if (callState == Call.State.StreamsRunning) {
                    finish()
                }
            }

            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "RRPhone",
                            color = Color.LightGray,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = callerName,
                            color = Color.White,
                            fontSize = 32.sp
                        )
                        Text(
                            text = "Chamada de Voz",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(100.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Botão Recusar
                            FloatingActionButton(
                                onClick = {
                                    CallManager.hangUp()
                                    finish()
                                },
                                containerColor = Color(0xFFD32F2F)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Recusar", tint = Color.White)
                            }

                            // Botão Atender
                            FloatingActionButton(
                                onClick = {
                                    CallManager.acceptCall()
                                    
                                    // Fecha essa tela da tela de bloqueio
                                    finish()
                                    
                                    // Abre o App principal (MainActivity) para ver a chamada rolando
                                    val mainIntent = Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    startActivity(mainIntent)
                                },
                                containerColor = Color(0xFF388E3C)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Atender", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

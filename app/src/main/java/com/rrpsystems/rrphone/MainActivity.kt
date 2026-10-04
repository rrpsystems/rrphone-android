package com.rrpsystems.rrphone

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.CallPhase
import com.rrpsystems.rrphone.core.telecom.TelecomHelper
import com.rrpsystems.rrphone.ui.navigation.AppNavGraph
import com.rrpsystems.rrphone.ui.screens.login.LoginViewModel
import com.rrpsystems.rrphone.ui.theme.RRPhoneTheme

class MainActivity : ComponentActivity() {
    companion object {
        var isAppInForeground = false
        /** Abre o app na tela de chamada (chamada recebida em tela cheia). */
        const val ACTION_SHOW_CALL = "com.rrpsystems.rrphone.ACTION_SHOW_CALL"
    }

    // Microfone e notificações logo na primeira abertura: pedir só na hora de
    // atender deixaria a primeira chamada recebida sem áudio.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // Android 14+: sem a permissão de tela cheia, a ligação recebida com a tela
    // apagada vira só uma notificação — a tela não acende. Instalado pelo Play,
    // ela só vem concedida depois que a declaração do Console é aprovada; até
    // lá (ou se o usuário a tirar) o app pede para ligar nas configurações.
    private var fullScreenMissing by mutableStateOf(false)
    private var fullScreenAsked = false

    override fun onResume() {
        super.onResume()
        fullScreenMissing = Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun openFullScreenSettings() {
        fullScreenAsked = true
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyLockScreenMode(CallManager.ui.value.phase)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.action == "com.rrpsystems.rrphone.ACTION_ANSWER_CALL") {
            CallManager.answer()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(CallManager.INCOMING_NOTIFICATION_ID)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Já na criação (aberta pela tela cheia de uma chamada recebida): a tela
        // precisa acender agora, não um quadro depois.
        applyLockScreenMode(CallManager.ui.value.phase)
        handleCallIntent(intent)

        TelecomHelper.registerPhoneAccount(applicationContext)
        requestMissingPermissions()

        // Ícones claros nas barras do sistema: o app é sempre escuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            RRPhoneTheme {
                // Sobre o bloqueio só enquanto houver chamada, como o discador do
                // sistema: acende a tela ao tocar e, quando a chamada acaba, o
                // bloqueio volta — o resto do app nunca fica exposto sem senha.
                val ui by CallManager.ui.collectAsState()
                LaunchedEffect(ui.phase) { applyLockScreenMode(ui.phase) }
                val loginViewModel: LoginViewModel = viewModel()
                val navController = rememberNavController()
                AppNavGraph(navController = navController, loginViewModel = loginViewModel)
                if (fullScreenMissing && !fullScreenAsked && ui.phase == CallPhase.Idle) {
                    AlertDialog(
                        onDismissRequest = { fullScreenAsked = true; fullScreenMissing = false },
                        title = { Text("Ligações com a tela apagada") },
                        text = {
                            Text("Para a tela acender quando chegar uma ligação, permita que o RRP Softphone " +
                                "mostre notificações em tela cheia.")
                        },
                        confirmButton = { TextButton(onClick = ::openFullScreenSettings) { Text("Permitir") } },
                        dismissButton = {
                            TextButton(onClick = { fullScreenAsked = true; fullScreenMissing = false }) { Text("Agora não") }
                        }
                    )
                }
            }
        }
    }

    private fun applyLockScreenMode(phase: CallPhase) {
        val inCall = phase != CallPhase.Idle
        setShowWhenLocked(inCall)
        setTurnScreenOn(phase == CallPhase.Incoming)
        if (inCall) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestMissingPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            // "Dispositivos próximos": fone e carro Bluetooth nas chamadas.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }
}

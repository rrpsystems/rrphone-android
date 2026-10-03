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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.telecom.TelecomHelper
import com.rrpsystems.rrphone.ui.navigation.AppNavGraph
import com.rrpsystems.rrphone.ui.screens.login.LoginViewModel
import com.rrpsystems.rrphone.ui.theme.RRPhoneTheme

class MainActivity : ComponentActivity() {
    companion object {
        var isAppInForeground = false
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
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.action == "com.rrpsystems.rrphone.ACTION_ANSWER_CALL") {
            CallManager.answer()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleCallIntent(intent)

        // Acender a tela e aparecer sobre o bloqueio quando aberta por uma chamada.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        TelecomHelper.registerPhoneAccount(applicationContext)
        requestMissingPermissions()

        // Ícones claros nas barras do sistema: o app é sempre escuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            RRPhoneTheme {
                val loginViewModel: LoginViewModel = viewModel()
                val navController = rememberNavController()
                AppNavGraph(navController = navController, loginViewModel = loginViewModel)
                if (fullScreenMissing && !fullScreenAsked) {
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

    private fun requestMissingPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }
}

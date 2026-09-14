package com.rrpsystems.rrphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.rrpsystems.rrphone.ui.navigation.AppNavGraph
import com.rrpsystems.rrphone.core.security.KeystoreManager
import com.rrpsystems.rrphone.core.sip.SipAccountHelper
import com.rrpsystems.rrphone.ui.screens.login.LoginViewModel
import com.rrpsystems.rrphone.core.telecom.TelecomHelper
import com.rrpsystems.rrphone.ui.theme.RRPhoneTheme
import android.view.WindowManager
import android.os.Build
import android.content.Intent
import android.app.NotificationManager
import android.content.Context
import com.rrpsystems.rrphone.core.sip.CallManager

class MainActivity : ComponentActivity() {
    companion object {
        var isAppInForeground = false
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
            CallManager.acceptCall()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleCallIntent(intent)
        
        // Permitir que a tela acenda e apareça sobre a tela de bloqueio
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
        
        // Registrar o PhoneAccount para o ConnectionService
        TelecomHelper.registerPhoneAccount(applicationContext)
        
        // Passar contexto pro CallManager disparar o push passivo (Fase C.2)
        com.rrpsystems.rrphone.core.sip.CallManager.appContext = applicationContext

        enableEdgeToEdge()
        setContent {
            RRPhoneTheme {
                // Factory manual para evitar Dagger/Hilt no MVP
                val factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val keystore = KeystoreManager(applicationContext)
                        val sipHelper = SipAccountHelper(keystore)
                        return LoginViewModel(keystore, sipHelper) as T
                    }
                }
                
                val loginViewModel: LoginViewModel = viewModel(factory = factory)
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavGraph(
                            navController = navController,
                            loginViewModel = loginViewModel
                        )
                    }
                }
            }
        }
    }
}
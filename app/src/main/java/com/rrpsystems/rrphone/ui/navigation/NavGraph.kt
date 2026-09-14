package com.rrpsystems.rrphone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rrpsystems.rrphone.ui.screens.dialer.DialerScreen
import com.rrpsystems.rrphone.ui.screens.login.LoginScreen
import com.rrpsystems.rrphone.ui.screens.login.LoginViewModel
import com.rrpsystems.rrphone.ui.screens.onboarding.BatteryOptimizationScreen
import com.rrpsystems.rrphone.ui.screens.call.CallScreen
import com.rrpsystems.rrphone.core.sip.CallManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.linphone.core.Call

sealed class Route(val route: String) {
    object Login : Route("login")
    object BatteryOnboarding : Route("battery_onboarding")
    object Dialer : Route("dialer")
    object Call : Route("call")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    loginViewModel: LoginViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Route.Login.route
    ) {
        composable(Route.Login.route) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    // Após o login com sucesso, navega para o onboarding de bateria
                    navController.navigate(Route.BatteryOnboarding.route) {
                        popUpTo(Route.Login.route) { inclusive = true } // Remove o login da pilha
                    }
                }
            )
        }

        composable(Route.BatteryOnboarding.route) {
            BatteryOptimizationScreen(
                onContinue = {
                    navController.navigate(Route.Dialer.route) {
                        popUpTo(Route.BatteryOnboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Dialer.route) {
            val callState by CallManager.callState.collectAsState()

            LaunchedEffect(callState) {
                // Se a chamada iniciou, navega para a tela de chamada
                if (callState == Call.State.OutgoingInit || 
                    callState == Call.State.OutgoingProgress ||
                    callState == Call.State.OutgoingRinging ||
                    callState == Call.State.IncomingReceived) {
                    
                    // Prevenir múltiplas navegações
                    if (navController.currentDestination?.route != Route.Call.route) {
                        navController.navigate(Route.Call.route)
                    }
                }
            }

            DialerScreen()
        }

        composable(Route.Call.route) {
            CallScreen(
                onCallEnded = {
                    navController.popBackStack(Route.Dialer.route, inclusive = false)
                }
            )
        }
    }
}

package com.rrpsystems.rrphone.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.CallPhase
import com.rrpsystems.rrphone.ui.components.Routes
import com.rrpsystems.rrphone.ui.screens.call.CallScreen
import com.rrpsystems.rrphone.ui.screens.contacts.ContactsScreen
import com.rrpsystems.rrphone.ui.screens.dialer.DialerScreen
import com.rrpsystems.rrphone.ui.screens.history.HistoryScreen
import com.rrpsystems.rrphone.ui.screens.login.LoginScreen
import com.rrpsystems.rrphone.ui.screens.login.LoginViewModel
import com.rrpsystems.rrphone.ui.screens.settings.SettingsScreen
import com.rrpsystems.rrphone.ui.screens.transfer.TransferScreen

sealed class Route(val route: String) {
    object Login : Route("login")
    object Dialer : Route(Routes.DIALER)
    object Call : Route("call")
    object Transfer : Route("transfer")
    object Contacts : Route(Routes.CONTACTS)
    object History : Route(Routes.HISTORY)
    object Settings : Route(Routes.SETTINGS)
}

private val tabRoutes = setOf(Routes.DIALER, Routes.CONTACTS, Routes.HISTORY, Routes.SETTINGS)
private val callRoutes = setOf(Route.Call.route, Route.Transfer.route)

@Composable
fun AppNavGraph(
    navController: NavHostController,
    loginViewModel: LoginViewModel
) {
    val startRoute = if (loginViewModel.checkIsLoggedIn()) Route.Dialer.route else Route.Login.route
    val ui by CallManager.ui.collectAsState()

    fun openCall() {
        if (navController.currentDestination?.route != Route.Call.route) {
            navController.navigate(Route.Call.route) { launchSingleTop = true }
        }
    }

    // A tela de chamada segue o estado real: abre quando uma chamada nasce
    // (efetuada ou recebida) e fecha quando não sobra nenhuma.
    LaunchedEffect(ui.phase) {
        val route = navController.currentDestination?.route
        when {
            (ui.phase == CallPhase.Incoming || ui.phase == CallPhase.Outgoing) && route in tabRoutes -> openCall()
            ui.phase == CallPhase.Idle && route in callRoutes -> {
                if (!navController.popBackStack(Route.Dialer.route, inclusive = false)) {
                    navController.navigate(Route.Dialer.route)
                }
            }
        }
    }

    val navigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Route.Dialer.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable(Route.Login.route) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(Route.Dialer.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Dialer.route) {
            DialerScreen(onNavigate = navigateTab, onReturnToCall = ::openCall)
        }
        composable(Route.Contacts.route) {
            ContactsScreen(onNavigate = navigateTab, onReturnToCall = ::openCall)
        }
        composable(Route.History.route) {
            HistoryScreen(onNavigate = navigateTab, onReturnToCall = ::openCall)
        }
        composable(Route.Settings.route) {
            SettingsScreen(
                onNavigate = navigateTab,
                onReturnToCall = ::openCall,
                onLoggedOut = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Call.route) {
            CallScreen(
                onOpenTransfer = {
                    navController.navigate(Route.Transfer.route) { launchSingleTop = true }
                },
                onBackToTabs = { navController.popBackStack() }
            )
        }
        composable(Route.Transfer.route) {
            TransferScreen(onDone = { navController.popBackStack(Route.Call.route, inclusive = false) })
        }
    }
}

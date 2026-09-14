package com.rrpsystems.rrphone.ui.screens.login

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrpsystems.rrphone.core.api.LoginRequest
import com.rrpsystems.rrphone.core.api.RetrofitClient
import com.rrpsystems.rrphone.core.security.KeystoreManager
import com.rrpsystems.rrphone.core.sip.SipAccountHelper
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val message: String) : LoginState()
    data class Error(val error: String) : LoginState()
}

class LoginViewModel(
    private val keystoreManager: KeystoreManager,
    private val sipAccountHelper: SipAccountHelper
) : ViewModel() {

    private val apiService = RetrofitClient.getService(keystoreManager)

    private val _loginState = mutableStateOf<LoginState>(LoginState.Idle)
    val loginState: State<LoginState> = _loginState

    fun authenticateAndProvision(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _loginState.value = LoginState.Error("Preencha todos os campos.")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                // TODO: Bypassing the API for MVP testing
                /* 
                // Código original da API comentado
                val loginResponse = apiService.login(LoginRequest(email, pass))
                ...
                */

                // DADOS MOCKADOS FORNECIDOS PELO USUÁRIO PARA TESTE
                val sipServer = "escritorio.rrpsystems.com.br"
                val sipPort = "5090"
                val sipTransport = "tcp"
                val sipExtension = "2127"
                val sipPassword = "!U^cz1hUDv"

                // Simulando latência de rede
                kotlinx.coroutines.delay(1000)

                // Passo 3: Salvar senha do SIP no Keystore
                keystoreManager.saveSecureString(KeystoreManager.KEY_SIP_PASSWORD, sipPassword)

                // Passo 4: Registrar conta no Liblinphone
                sipAccountHelper.configureAndRegisterAccount(
                    username = sipExtension,
                    domain = sipServer,
                    proxyAddress = sipServer,
                    port = sipPort,
                    transport = sipTransport
                )

                _loginState.value = LoginState.Success("Login simulado com sucesso!")

            } catch (e: Exception) {
                e.printStackTrace()
                _loginState.value = LoginState.Error("Erro: ${e.localizedMessage}")
            }
        }
    }
}

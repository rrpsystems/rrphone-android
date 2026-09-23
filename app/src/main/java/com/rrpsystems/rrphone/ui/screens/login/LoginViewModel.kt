package com.rrpsystems.rrphone.ui.screens.login

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrpsystems.rrphone.core.contacts.ContactsRepository
import com.rrpsystems.rrphone.core.settings.AccountProfile
import com.rrpsystems.rrphone.core.settings.SettingsStore
import com.rrpsystems.rrphone.core.sip.LinphoneManager
import com.rrpsystems.rrphone.core.sip.Registration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val message: String) : LoginState()
    data class Error(val error: String) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val _loginState = mutableStateOf<LoginState>(LoginState.Idle)
    val loginState: State<LoginState> = _loginState

    fun configureSipAccount(
        domain: String, extension: String, pass: String, transport: String,
        pushEnabled: Boolean, outboundProxy: String,
    ) {
        if (domain.isBlank() || extension.isBlank() || pass.isBlank()) {
            _loginState.value = LoginState.Error("Preencha todos os campos.")
            return
        }
        applyProfile(
            AccountProfile(
                username = extension.trim(),
                password = pass.trim(),
                domain = domain.trim(),
                transport = transport,
                pushEnabled = pushEnabled,
                outboundProxy = outboundProxy.trim(),
            )
        )
    }

    /**
     * Aplica e espera o servidor responder. A conta só é gravada quando o
     * registro dá certo: gravar antes faria o app pular esta tela na próxima
     * abertura com uma senha errada salva.
     */
    fun applyProfile(profile: AccountProfile) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            LinphoneManager.applyAccount(profile)
            val result = withTimeoutOrNull(15_000) {
                LinphoneManager.registration.first { it is Registration.Ok || it is Registration.Failed }
            }
            when (result) {
                is Registration.Ok -> {
                    SettingsStore.saveProfile(profile)
                    ContactsRepository.setUrl(profile.contactsUrl)
                    _loginState.value = LoginState.Success("Conta configurada!")
                }
                is Registration.Failed -> {
                    LinphoneManager.clearAccount()
                    _loginState.value = LoginState.Error("O servidor recusou o registro: ${result.message}")
                }
                else -> {
                    LinphoneManager.clearAccount()
                    _loginState.value = LoginState.Error("Sem resposta do servidor. Confira o endereço, a porta e o transporte.")
                }
            }
        }
    }

    fun showError(message: String) {
        _loginState.value = LoginState.Error(message)
    }

    fun checkIsLoggedIn(): Boolean = SettingsStore.loadProfile() != null
}

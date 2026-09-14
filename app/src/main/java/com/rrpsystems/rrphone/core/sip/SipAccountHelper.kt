package com.rrpsystems.rrphone.core.sip

import android.util.Log
import com.rrpsystems.rrphone.core.security.KeystoreManager
import org.linphone.core.AccountParams
import org.linphone.core.Address
import org.linphone.core.AuthInfo
import org.linphone.core.AuthMethod
import org.linphone.core.Factory

/**
 * Utilitário responsável por configurar a conta SIP no Linphone Core,
 * injetando a senha de forma segura do KeystoreManager.
 */
class SipAccountHelper(
    private val keystoreManager: KeystoreManager
) {
    private val TAG = "SipAccountHelper"

    /**
     * Configura e registra a conta SIP.
     * Deve ser chamado após o Provisioning da API.
     */
    fun configureAndRegisterAccount(
        username: String,
        domain: String,
        proxyAddress: String? = null, // Opcional, caso o server SIP seja diferente do domain
        port: String? = null,
        transport: String = "wss" // Padrão WSS, mas permitimos tcp/udp para testes
    ) {
        val core = LinphoneManager.getCore()
        val factory = Factory.instance()

        // 1. Recuperar a senha criptografada
        val password = keystoreManager.getSecureString(KeystoreManager.KEY_SIP_PASSWORD)
        if (password.isNullOrEmpty()) {
            Log.e(TAG, "Senha SIP não encontrada no Keystore. Abortando registro.")
            return
        }

        // 2. Limpar contas antigas para evitar resquícios
        core.clearAllAuthInfo()
        core.clearAccounts()

        // 3. Criar a identidade (sip:username@domain)
        val identityAddress: Address = factory.createAddress("sip:$username@$domain") ?: run {
            Log.e(TAG, "Falha ao criar o endereço de identidade.")
            return
        }

        // 4. Criar a informação de Autenticação (AuthInfo)
        // Isso fica apenas na memória do Core do liblinphone.
        val authInfo: AuthInfo = factory.createAuthInfo(
            username,
            null, // userid
            password,
            null, // ha1
            null, // realm
            domain,
            null // algorithm
        )
        
        // Adiciona a autenticação no Core
        core.addAuthInfo(authInfo)

        // 5. Configurar os Parâmetros da Conta
        val accountParams: AccountParams = core.createAccountParams()
        accountParams.identityAddress = identityAddress

        // Configurar Rota do Servidor (Proxy/Registrar)
        val serverStr = proxyAddress ?: domain
        val portSuffix = if (!port.isNullOrEmpty()) ":$port" else ""
        
        val serverAddr = factory.createAddress("sip:$serverStr$portSuffix;transport=$transport")
        accountParams.serverAddress = serverAddr

        // Habilitar o registro
        accountParams.setRegisterEnabled(true)
        // Tempo de expiração padrão (ex: 600 segundos)
        accountParams.setExpires(600)

        // 6. Adicionar a Conta no Core e setar como padrão
        val account = core.createAccount(accountParams)
        core.addAccount(account)
        core.defaultAccount = account

        Log.i(TAG, "Conta SIP configurada e adicionada. Tentando registro WSS para $username@$domain")
    }

    /**
     * Remove os registros de autenticação (Logout)
     */
    fun unregisterAndClear() {
        val core = LinphoneManager.getCore()
        core.clearAccounts()
        core.clearAllAuthInfo()
        Log.i(TAG, "Contas e autenticações SIP limpas.")
    }
}

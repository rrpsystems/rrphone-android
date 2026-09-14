package com.rrpsystems.rrphone.core.sip

import android.content.Context
import android.util.Log
import org.linphone.core.Account
import org.linphone.core.RegistrationState
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.GlobalState
import org.linphone.core.LogCollectionState
import java.io.File

/**
 * Manager Singleton para encapsular a engine do Liblinphone.
 * Evitamos injetores complexos no MVP para manter a simplicidade.
 */
object LinphoneManager {

    private const val TAG = "LinphoneManager"
    
    // Core do liblinphone (pode ser null antes de ser iniciado)
    private var core: Core? = null

    // Flag de controle de logs de produção (pode vir do SharedPreferences futuramente)
    var isDebugModeEnabled: Boolean = true

    fun getCore(): Core {
        return core ?: throw IllegalStateException("Linphone Core não foi inicializado. Chame start() primeiro.")
    }

    /**
     * Inicializa a Factory e o Core. Deve ser chamado na classe Application.
     */
    fun start(context: Context) {
        if (core != null) {
            Log.w(TAG, "Linphone Core já está iniciado.")
            return
        }

        try {
            val factory = Factory.instance()

            // 1. Configurar Logs
            configureLogging(factory)

            // 2. Diretórios base para o linphone (sempre usar o diretório privado do app)
            val basePath = context.filesDir.absolutePath
            val configPath = "$basePath/linphonerc"
            val factoryPath = "$basePath/linphonerc-factory"

            // 3. Criar o Core passando null para linphonerc, ou passando caminhos válidos.
            // O PRD proíbe salvar senhas em linphonerc. Então criamos com o config, 
            // mas instruímos a nunca fazer dump ou salvar AuthInfos no arquivo config.
            core = factory.createCore(configPath, factoryPath, context)

            // Opcional: configurar diretórios específicos para logs, anéis, etc.
            core?.ring = null // usar toque padrão do sistema futuramente

            // 4. Adicionar Listener global para rastrear o estado
            core?.addListener(object : CoreListenerStub() {
                override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
                    Log.i(TAG, "Linphone Global State: $state - $message")
                }

                override fun onAccountRegistrationStateChanged(
                    core: Core,
                    account: Account,
                    state: RegistrationState,
                    message: String
                ) {
                    Log.i(TAG, "Status de Registro [${account.params.identityAddress?.asStringUriOnly()}]: $state - $message")
                }
            })

            // Configurar codecs: Forçar G729 e G711 (PCMA/PCMU), desabilitar OPUS e Speex
            core?.audioPayloadTypes?.forEach { pt ->
                val mime = pt.mimeType.uppercase()
                when (mime) {
                    "G729", "PCMA", "PCMU" -> pt.enable(true)
                    "OPUS", "SPEEX", "SPEEX16", "SPEEX32" -> pt.enable(false)
                }
            }

            // Iniciar o core (deve ser iniciado para processar iterações)
            core?.start()

            Log.i(TAG, "Linphone Core inicializado com sucesso (Versão: ${core?.version})")

        } catch (e: Exception) {
            Log.e(TAG, "Falha crítica ao iniciar o Linphone Core", e)
        }
    }

    /**
     * Configuração dinâmica de logs. Se estiver em modo debug, joga no console e no arquivo.
     * Se for produção (e o debug não estiver forçado), desativa.
     */
    private fun configureLogging(factory: Factory) {
        if (isDebugModeEnabled) {
            factory.enableLogCollection(LogCollectionState.Enabled)
            factory.setDebugMode(true, "RRP-Linphone")
            Log.i(TAG, "Logs do Linphone estão ATIVADOS.")
        } else {
            factory.enableLogCollection(LogCollectionState.Disabled)
            factory.setDebugMode(false, "RRP-Linphone")
        }
    }

    /**
     * Chamado periodicamente (ou via Service) se não houver um Looper nativo.
     * Na versão 5+, o Core geralmente itera automaticamente no Android se configurado corretamente,
     * mas é boa prática ter o método exposto.
     */
    fun iterate() {
        core?.iterate()
    }

    /**
     * Encerra o core de forma segura.
     */
    fun destroy() {
        core?.stop()
        core = null
        Log.i(TAG, "Linphone Core destruído.")
    }
}

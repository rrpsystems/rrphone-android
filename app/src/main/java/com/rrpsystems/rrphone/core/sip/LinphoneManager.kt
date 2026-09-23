package com.rrpsystems.rrphone.core.sip

import android.content.Context
import android.util.Log
import com.rrpsystems.rrphone.BuildConfig
import com.rrpsystems.rrphone.core.settings.AccountProfile
import com.rrpsystems.rrphone.core.settings.CodecInfo
import com.rrpsystems.rrphone.core.settings.Codecs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.GlobalState
import org.linphone.core.LogCollectionState
import org.linphone.core.Reason
import org.linphone.core.RegistrationState

/** Estado do registro, já na forma que a UI mostra. */
sealed class Registration {
    object None : Registration()
    object Progress : Registration()
    object Ok : Registration()
    data class Failed(val message: String) : Registration()
}

/**
 * Dono do Core do liblinphone: criação, conta única (D-01), codecs (D-14),
 * DTMF (D-15) e processamento de áudio. Espelha a parte de configuração do
 * SipCoreManager do desktop; o controle de chamadas fica no CallManager.
 */
object LinphoneManager {
    private const val TAG = "LinphoneManager"

    private var core: Core? = null
    private var account: Account? = null

    /** Se o G.729 foi registrado (libmsbcg729.so presente e carregada). */
    var g729Available = false
        private set

    private val _registration = MutableStateFlow<Registration>(Registration.None)
    val registration: StateFlow<Registration> = _registration.asStateFlow()

    var isDebugModeEnabled: Boolean = BuildConfig.DEBUG

    fun getCore(): Core =
        core ?: throw IllegalStateException("Linphone Core não foi inicializado. Chame start() primeiro.")

    fun coreOrNull(): Core? = core

    fun start(context: Context) {
        if (core != null) return
        try {
            val factory = Factory.instance()
            factory.enableLogCollection(if (isDebugModeEnabled) LogCollectionState.Enabled else LogCollectionState.Disabled)
            factory.setDebugMode(isDebugModeEnabled, "RRP-Linphone")

            // Sem linphonerc: a conta é do app (SettingsStore), como no desktop.
            // Com o arquivo, uma conta antiga voltava sozinha a cada abertura e
            // brigava com a que o usuário acabou de configurar.
            //
            // O arquivo de fábrica só existe por causa do G.729: a lista de
            // codecs é montada aqui, na criação, e o filtro G.729 só é
            // registrado logo depois (G729.install). Sem dont_check_codecs o
            // G729 seria descartado como "não suportado" antes de existir.
            // Como efeito colateral a lista crua ganha codecs que o SDK não
            // tem (AMR, iLBC...), mas setAudioCodecsOrder desliga tudo fora
            // de Codecs.supported, e o G729 sai da lista se o registro falhar.
            val factoryRc = java.io.File(context.filesDir, "rrp-factory.rc")
            factoryRc.writeText("[sound]\ndont_check_codecs=1\n")
            val c = factory.createCore(null, factoryRc.absolutePath, context)
            core = c
            g729Available = G729.install(c)

            c.setUserAgent("RRPSoftphone-Android", BuildConfig.VERSION_NAME)
            // Softphone, não mensageiro: sem chat e sem assinaturas de presença,
            // que o PBX não oferece e só geram SUBSCRIBE inútil e erro no log.
            c.disableChat(Reason.NotAcceptable)
            c.isFriendListSubscriptionEnabled = false
            // O toque é do CallManager (toque do sistema), não do engine.
            c.isNativeRingingEnabled = false
            c.ring = null

            // O cancelador de eco padrão (WebRTC AEC3, libmswebrtc) no build
            // x86/x86_64 do SDK 5.5 usa instruções AVX: em CPU sem elas —
            // caso do emulador — o app morre com SIGILL ao montar o áudio da
            // primeira chamada. Nesses ABIs vai o Speex, embutido no
            // mediastreamer2 (o mesmo do desktop). Celular real é ARM e mantém
            // o AEC3, que é melhor no viva-voz.
            if (android.os.Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("x86") == true) {
                c.echoCancellerFilterName = "MSSpeexEC"
            }
            Log.i(TAG, "Cancelador de eco: ${c.echoCancellerFilterName ?: "padrão (MSWebRTCAEC)"}")

            c.addListener(object : CoreListenerStub() {
                override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
                    Log.i(TAG, "Estado global do core: $state $message")
                }

                override fun onAccountRegistrationStateChanged(
                    core: Core, account: Account, state: RegistrationState, message: String
                ) {
                    Log.i(TAG, "Registro [${account.params.identityAddress?.asStringUriOnly()}]: $state - $message")
                    if (account != this@LinphoneManager.account) return
                    _registration.value = when (state) {
                        RegistrationState.Ok -> Registration.Ok
                        RegistrationState.Progress, RegistrationState.Refreshing -> Registration.Progress
                        RegistrationState.Failed -> Registration.Failed(message.ifBlank { "Falha no registro" })
                        else -> Registration.None
                    }
                }
            })

            c.start()
            setAudioCodecsOrder(Codecs.supported)
            configureG729()
            Log.i(TAG, "Core iniciado (liblinphone ${c.version}) | codecs: " +
                audioCodecs().joinToString { "${it.mimeType}/${it.clockRate}" + if (it.enabled) "" else " (off)" })
        } catch (e: Exception) {
            Log.e(TAG, "Falha crítica ao iniciar o Linphone Core", e)
        }
    }

    // --- Conta -----------------------------------------------------------

    /** Configura/substitui a conta única e registra. Aplica também DTMF e codecs do perfil. */
    fun applyAccount(profile: AccountProfile) {
        val c = core ?: return
        clearAccount()
        val factory = Factory.instance()

        val identity = factory.createAddress("sip:${profile.username}@${hostOf(profile.domain)}")
        val server = factory.createAddress("sip:${profile.domain};transport=${profile.transport.lowercase()}")
        if (identity == null || server == null) {
            _registration.value = Registration.Failed("Endereço SIP inválido")
            return
        }
        if (profile.displayName.isNotBlank()) identity.displayName = profile.displayName

        val params = c.createAccountParams()
        params.identityAddress = identity
        params.serverAddress = server
        params.isRegisterEnabled = true
        // Validade curta de propósito (igual ao desktop): contatos órfãos de um
        // processo morto somem logo, em vez de acumular até o PBX responder 403.
        params.expires = 300

        val acc = c.createAccount(params)
        c.addAccount(acc)
        c.defaultAccount = acc
        account = acc

        c.addAuthInfo(
            factory.createAuthInfo(profile.username, null, profile.password, null, null, hostOf(profile.domain), null)
        )
        _registration.value = Registration.Progress

        setDtmfMethod(profile.dtmfMethod)
        val codecs = profile.codecs.mapNotNull { Codecs.findByName(it) }
        if (codecs.isNotEmpty()) setAudioCodecsOrder(codecs)
        Log.i(TAG, "Conta configurada: ${profile.username}@${profile.domain} via ${profile.transport}")
    }

    fun clearAccount() {
        val c = core ?: return
        account?.let { c.removeAccount(it) }
        account = null
        c.clearAccounts()
        // As credenciais ficam separadas da conta: sem isto, uma senha errada
        // corrigida continuava sendo enviada até reiniciar o app.
        c.clearAllAuthInfo()
        _registration.value = Registration.None
    }

    fun refreshRegistration() {
        core?.refreshRegisters()
    }

    private fun hostOf(domain: String) = domain.substringBefore(':')

    // --- Codecs (D-14) -----------------------------------------------------

    /** Codecs que o app oferece e o engine tem, na ordem atual de prioridade. */
    fun audioCodecs(): List<CodecInfo> {
        val c = core ?: return emptyList()
        return c.audioPayloadTypes
            .filter { Codecs.isSupported(it.mimeType, it.clockRate, it.channels) }
            .filter { g729Available || !it.mimeType.equals("G729", ignoreCase = true) }
            .map { CodecInfo(it.mimeType, it.clockRate, it.channels, it.enabled()) }
    }

    /**
     * Reaplica habilitação e ordem: o que está em [ordered] fica habilitado
     * nessa ordem; o resto do que o engine conhece é desabilitado e vai para o
     * fim, para a oferta SDP ficar curta.
     */
    fun setAudioCodecsOrder(ordered: List<CodecInfo>) {
        val c = core ?: return
        val all = c.audioPayloadTypes.toList()
        val chosen = ordered
            .filter { g729Available || !it.mimeType.equals("G729", ignoreCase = true) }
            .mapNotNull { info ->
            all.firstOrNull {
                it.mimeType.equals(info.mimeType, ignoreCase = true) &&
                    it.clockRate == info.clockRate && it.channels == info.channels
            }?.also { it.enable(info.enabled) }
        }
        val rest = all.filter { it !in chosen }.onEach { it.enable(false) }
        c.setAudioPayloadTypes((chosen + rest).toTypedArray())
    }

    /** Nomes dos codecs habilitados, em ordem — o formato do perfil. */
    fun enabledCodecNames(): List<String> = audioCodecs().filter { it.enabled }.map { it.mimeType }

    // G.729 anexo B é armadilha clássica de interoperabilidade (a chamada
    // negocia, o RTP flui e o áudio sai mudo ou picotado); anunciar annexb=no.
    // Só o recv fmtp, que é o nosso lado do SDP — o send vem da negociação.
    private fun configureG729() {
        core?.audioPayloadTypes?.firstOrNull { it.mimeType.equals("G729", ignoreCase = true) }
            ?.recvFmtp = "annexb=no"
    }

    // --- DTMF (D-15) --------------------------------------------------------

    fun setDtmfMethod(method: String) {
        val c = core ?: return
        when (method) {
            "info" -> { c.useRfc2833ForDtmf = false; c.useInfoForDtmf = true }
            "inband" -> { c.useRfc2833ForDtmf = false; c.useInfoForDtmf = false }
            else -> { c.useRfc2833ForDtmf = true; c.useInfoForDtmf = false }
        }
    }

    // --- Processamento do microfone -----------------------------------------
    // Vale a partir da próxima chamada (é aplicado ao montar o stream).
    // Supressão de ruído desligada por padrão, como no desktop: ela come parte
    // da naturalidade da voz, e só compensa em ambiente barulhento.

    fun setAudioProcessing(noiseSuppression: Boolean, echoCancellation: Boolean, automaticGainControl: Boolean) {
        val c = core ?: return
        c.isNoiseSuppressionEnabled = noiseSuppression
        c.isEchoCancellationEnabled = echoCancellation
        c.isAgcEnabled = automaticGainControl
    }

    fun destroy() {
        core?.stop()
        core = null
        account = null
    }
}

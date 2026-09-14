package com.rrpsystems.rrphone.core.sip

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import com.rrpsystems.rrphone.core.telecom.VoiceConnection
import com.rrpsystems.rrphone.core.telecom.TelecomHelper
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager

/**
 * Singleton responsável por orquestrar o gerenciamento de chamadas.
 * Desacopla a UI diretamente da API C++ do Liblinphone.
 */
object CallManager {
    private const val TAG = "CallManager"

    // Estado reativo da chamada atual
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow<Call.State>(Call.State.Idle)
    val callState: StateFlow<Call.State> = _callState.asStateFlow()

    // Conexão Nativa do Android (ConnectionService)
    var currentVoiceConnection: VoiceConnection? = null
    var appContext: Context? = null
    
    private var ringtone: Ringtone? = null

    init {
        // Observar mudanças no Core
        LinphoneManager.getCore()?.addListener(object : CoreListenerStub() {
            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State,
                message: String
            ) {
                Log.i(TAG, "Call State Changed: $state - $message")
                _callState.value = state

                when (state) {
                    Call.State.IncomingReceived -> {
                        _currentCall.value = call
                        val remoteNumber = call.remoteAddress?.username ?: "Desconhecido"
                        
                        // Tocar o ringtone do sistema
                        playSystemRingtone()
                        
                        // Aciona a tela nativa do Android (ConnectionService)
                        appContext?.let { ctx ->
                            TelecomHelper.startIncomingCall(ctx, remoteNumber)
                        }
                    }
                    Call.State.OutgoingInit -> {
                        _currentCall.value = call
                    }
                    Call.State.StreamsRunning -> {
                        stopSystemRingtone()
                        currentVoiceConnection?.setActive()
                    }
                    Call.State.End, Call.State.Released, Call.State.Error -> {
                        stopSystemRingtone()
                        currentVoiceConnection?.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                        currentVoiceConnection?.destroy()
                        currentVoiceConnection = null
                        
                        if (_currentCall.value == call) {
                            _currentCall.value = null
                        }
                    }
                    else -> {}
                }
            }
        })
    }

    /**
     * Inicia a chamada via UI. Para usar o ConnectionService nativo, 
     * precisamos passar o Context.
     */
    fun makeCall(context: Context, number: String) {
        TelecomHelper.startOutgoingCall(context, number)
    }

    /**
     * Acionado pelo próprio VoiceConnectionService após a tela nativa estar pronta.
     */
    fun internalMakeCall(number: String) {
        val core = LinphoneManager.getCore() ?: return
        
        try {
            // Criar o endereço destino
            val proxy = core.defaultProxyConfig
            val domain = proxy?.domain ?: "escritorio.rrpsystems.com.br"
            val address = core.createAddress("sip:$number@$domain")
            
            if (address != null) {
                Log.i(TAG, "Iniciando chamada para: ${address.asStringUriOnly()}")
                // Usamos os parâmetros padrão (apenas áudio)
                core.inviteAddress(address)
            } else {
                Log.e(TAG, "Falha ao criar endereço SIP para o número: $number")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao realizar chamada", e)
        }
    }

    private fun playSystemRingtone() {
        try {
            val ctx = appContext ?: return
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(ctx, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tocar ringtone", e)
        }
    }

    private fun stopSystemRingtone() {
        try {
            ringtone?.takeIf { it.isPlaying }?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar ringtone", e)
        }
    }

    fun acceptCall() {
        _currentCall.value?.accept()
    }

    fun hangUp() {
        val core = LinphoneManager.getCore()
        val call = _currentCall.value
        
        if (call != null) {
            call.terminate()
        } else {
            // Se por algum motivo perdermos a ref, garantimos que tudo encerre
            core?.terminateAllCalls()
        }
    }

    fun toggleMute() {
        // TODO: A API de Mute mudou no SDK 5.3+ (agora usa Call.microphoneMuted ou AudioDevice).
        // Desabilitado temporariamente apenas para testarmos a ligação rodar primeiro!
    }

    fun toggleHold() {
        val call = _currentCall.value ?: return
        if (call.state == Call.State.Paused || call.state == Call.State.Pausing) {
            call.resume()
        } else {
            call.pause()
        }
    }

    fun sendDtmf(digit: Char) {
        val call = _currentCall.value ?: return
        if (call.state == Call.State.StreamsRunning) {
            LinphoneManager.getCore()?.playDtmf(digit, 100) // Toca o som local
            call.sendDtmf(digit) // Envia o sinal SIP INFO ou RFC2833
        }
    }
}

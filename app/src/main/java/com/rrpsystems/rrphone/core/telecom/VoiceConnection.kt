package com.rrpsystems.rrphone.core.telecom

import android.telecom.Connection
import android.telecom.DisconnectCause
import com.rrpsystems.rrphone.core.sip.CallManager

/**
 * A "sessão" de chamada vista pelo Android: é o que aparece na tela de
 * bloqueio, recebe os botões do Bluetooth/carro e dá ao app o áudio de
 * ligação. Os comandos vindos daqui são repassados ao CallManager.
 */
class VoiceConnection : Connection() {
    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
    }

    override fun onAnswer(videoState: Int) {
        CallManager.answer()
        setActive()
    }

    override fun onReject() {
        CallManager.decline()
        close(DisconnectCause.REJECTED)
    }

    override fun onDisconnect() {
        CallManager.hangUp()
        close(DisconnectCause.LOCAL)
    }

    override fun onAbort() {
        CallManager.hangUp()
        close(DisconnectCause.CANCELED)
    }

    // Espera pedida pelo sistema: outra ligação celular chegando, botão do carro.
    override fun onHold() {
        CallManager.setHeld(true)
    }

    override fun onUnhold() {
        CallManager.setHeld(false)
    }

    private fun close(cause: Int) {
        setDisconnected(DisconnectCause(cause))
        destroy()
        if (CallManager.currentVoiceConnection === this) CallManager.currentVoiceConnection = null
    }
}

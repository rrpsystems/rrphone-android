package com.rrpsystems.rrphone.core.telecom

import android.telecom.Connection
import android.telecom.DisconnectCause
import com.rrpsystems.rrphone.core.sip.CallManager

/**
 * Representa uma única chamada VoIP no sistema operacional Android.
 * É isso que o Android gerencia quando aparece na tela de bloqueio.
 */
class VoiceConnection : Connection() {
    init {
        // Indica que nosso app gerencia a própria UI, mas quer o roteamento de áudio do SO
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
    }

    // Quando o usuário aperta o botão verde na tela de bloqueio do Android
    override fun onAnswer(videoState: Int) {
        super.onAnswer(videoState)
        CallManager.acceptCall()
        setActive()
    }

    // Quando o usuário recusa na tela nativa
    override fun onReject() {
        super.onReject()
        CallManager.hangUp()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    // Quando o usuário desliga durante a chamada
    override fun onDisconnect() {
        super.onDisconnect()
        CallManager.hangUp()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    // Cancela antes de atender
    override fun onAbort() {
        super.onAbort()
        CallManager.hangUp()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    // Quando clica em "Espera" no painel nativo do carro/bluetooth
    override fun onHold() {
        super.onHold()
        CallManager.toggleHold()
        setOnHold()
    }

    override fun onUnhold() {
        super.onUnhold()
        CallManager.toggleHold()
        setActive()
    }
}

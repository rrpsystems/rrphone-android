package com.rrpsystems.rrphone.core.sip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rrpsystems.rrphone.MainActivity
import com.rrpsystems.rrphone.R
import com.rrpsystems.rrphone.core.history.CallHistoryStore
import com.rrpsystems.rrphone.core.history.CallRecord
import com.rrpsystems.rrphone.core.settings.SettingsStore
import com.rrpsystems.rrphone.core.telecom.TelecomHelper
import com.rrpsystems.rrphone.core.telecom.VoiceConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Reason

/** Quem está do outro lado. */
data class Party(val name: String, val number: String) {
    val label: String get() = name.ifBlank { number }
}

enum class CallPhase { Idle, Incoming, Outgoing, Active }

enum class AudioRoute(val label: String) {
    Earpiece("Telefone"), Speaker("Alto-falante"), Headset("Fone com fio"), Bluetooth("Bluetooth")
}

/** Perna de consulta durante uma transferência (D-06). */
data class TransferUi(
    val target: String,
    val answered: Boolean,
    val label: String,
    val connectedAt: Long?,
)

/**
 * Tudo o que as telas de chamada precisam, num objeto só. É recalculado a
 * partir do estado real das chamadas a cada evento, em vez de mantido com
 * flags soltas — foi assim que o desktop parou de ter UI "em repouso" com
 * chamada no ar.
 */
data class CallUiState(
    val phase: CallPhase = CallPhase.Idle,
    val party: Party? = null,
    val statusLabel: String = "",
    // SystemClock.elapsedRealtime() de quando a chamada em primeiro plano foi atendida.
    val connectedAt: Long? = null,
    val muted: Boolean = false,
    val held: Boolean = false,
    // Segunda chamada tocando durante a conversa, aguardando decisão.
    val waiting: Party? = null,
    // Chamada estacionada enquanto se fala com a outra (alternável).
    val parked: Party? = null,
    // O número discado, quando a chamada foi atendida por outro (grupo de
    // captura, desvio): o visor mostra quem atendeu e, embaixo, o discado.
    val dialed: String = "",
    val transfer: TransferUi? = null,
    // Conferência a três em andamento: as duas pessoas além do usuário.
    val conference: List<Party>? = null,
    // Há duas chamadas atendidas que podem virar uma conferência.
    val canConference: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.Earpiece,
    val availableRoutes: List<AudioRoute> = emptyList(),
)

/**
 * Controle de chamadas — porte da máquina de estados do SipCoreManager do
 * desktop:
 *
 * - no máximo duas chamadas: uma em primeiro plano (active) e uma estacionada
 *   (held); uma terceira recebe 486 Busy;
 * - chamada em espera é anunciada, nunca atendida ou recusada sozinha;
 * - desligar a chamada em primeiro plano retoma a estacionada;
 * - transferência é um fluxo só: consulta que, concluída antes de o destino
 *   atender, vira transferência cega;
 * - siga-me vence o não perturbe, e ambos são registrados no histórico.
 *
 * O ConnectionService do Android representa a "sessão" (a primeira chamada),
 * para o sistema tratar o app como uma ligação (áudio, Bluetooth, tela de
 * bloqueio). Consulta e chamada em espera ficam só dentro do app.
 */
object CallManager {
    private const val TAG = "CallManager"
    private const val WAITING_NOTIFICATION_ID = 1002

    private val _ui = MutableStateFlow(CallUiState())
    val ui: StateFlow<CallUiState> = _ui.asStateFlow()

    // Mensagem da última chamada que falhou ("Ocupado", "Número não encontrado"),
    // mostrada na linha de gancho até a próxima ação.
    private val _hookMessage = MutableStateFlow("")
    val hookMessage: StateFlow<String> = _hookMessage.asStateFlow()

    fun clearHookMessage() {
        _hookMessage.value = ""
    }

    private val _doNotDisturb = MutableStateFlow(false)
    val doNotDisturb: StateFlow<Boolean> = _doNotDisturb.asStateFlow()

    private val _forwardTarget = MutableStateFlow("")
    val forwardTarget: StateFlow<String> = _forwardTarget.asStateFlow()

    var currentVoiceConnection: VoiceConnection? = null
    private var appContext: Context? = null

    private var activeCall: Call? = null        // chamada A, em primeiro plano
    private var heldCall: Call? = null          // estacionada, durante chamada em espera
    private var waitingCall: Call? = null       // segunda chamada tocando, ainda sem decisão
    private var consultationCall: Call? = null  // chamada C, só durante a transferência
    // Conferência local a três (o áudio é misturado no aparelho). Enquanto
    // existe, activeCall e heldCall são os dois participantes. É do Core.
    private var conference: org.linphone.core.Conference? = null
    private var transferTarget = ""             // guardado para o fallback de transferência cega
    private var consultationAnswered = false
    private var transferCompleting = false
    private var muted = false

    private val activeLabels = mutableMapOf<String, String>()
    private val parties = mutableMapOf<String, Party>()
    private val connectedAt = mutableMapOf<String, Long>()
    private val tracks = mutableMapOf<String, Track>()

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    /** Contexto de cada chamada para gravar o histórico quando ela termina. */
    private data class Track(
        val peer: String,
        var displayName: String,
        val startedAt: Long,
        val incoming: Boolean,
        var answered: Boolean = false,
        var connectedPeer: String = "",
        var note: String = "",
    )

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _doNotDisturb.value = SettingsStore.doNotDisturb
        _forwardTarget.value = SettingsStore.forwardTarget

        LinphoneManager.coreOrNull()?.addListener(object : CoreListenerStub() {
            override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                handleCallState(core, call, state, message)
            }

            override fun onAudioDevicesListUpdated(core: Core) = publish()
            override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) = publish()
        })
    }

    // --- Não perturbe / siga-me ---------------------------------------------

    fun setDoNotDisturb(enabled: Boolean) {
        _doNotDisturb.value = enabled
        SettingsStore.doNotDisturb = enabled
        Log.i(TAG, "Não perturbe: ${if (enabled) "ligado" else "desligado"}")
    }

    /** Vazio desliga. Redireciona com 302: o aparelho nem toca. */
    fun setForwardTarget(target: String) {
        _forwardTarget.value = target.trim()
        SettingsStore.forwardTarget = target
        Log.i(TAG, "Siga-me: ${target.ifBlank { "desligado" }}")
    }

    // --- Discagem / atender / desligar --------------------------------------

    /**
     * Pede ao Android para iniciar a chamada (ConnectionService), que por sua
     * vez chama [placeCall]. Se o sistema recusar, disca direto — sem a
     * integração com o sistema, mas sem deixar o usuário sem ligação.
     */
    fun dial(context: Context, number: String) {
        val target = number.trim()
        if (target.isEmpty() || activeCall != null) return
        _hookMessage.value = ""
        if (!TelecomHelper.startOutgoingCall(context, target)) {
            placeCall(target)
        }
    }

    /** Chamado pelo VoiceConnectionService (ou pelo fallback de [dial]). */
    fun placeCall(number: String) {
        val core = LinphoneManager.coreOrNull() ?: return
        val address = core.interpretUrl(number, false)
        if (address == null) {
            _hookMessage.value = "Não foi possível interpretar o destino: $number"
            endSessionIfIdle()
            return
        }
        activeCall = core.inviteAddress(address)
        Log.i(TAG, "Discando para $number ${if (activeCall != null) "| INVITE enviado" else "| FALHOU"}")
        activeCall?.let { call ->
            parties[id(call)] = Party("", number)
            tracks[id(call)] = Track(peer = number, displayName = "", startedAt = System.currentTimeMillis(), incoming = false)
        } ?: endSessionIfIdle()
        publish()
    }

    fun answer() {
        val call = activeCall ?: return
        if (call.state == Call.State.IncomingReceived || call.state == Call.State.IncomingEarlyMedia) {
            stopRinging()
            call.accept()
        }
    }

    /** Recusar uma chamada recebida (a primeira; a em espera usa [declineWaitingCall]). */
    fun decline() {
        val call = activeCall ?: return
        stopRinging()
        tracks[id(call)]?.note = "recusada"
        call.decline(Reason.Declined)
    }

    fun hangUp() {
        Log.i(TAG, "Desligar solicitado | principal: ${activeCall != null} | consulta: ${consultationCall != null}" +
            " | conferência: ${conference != null}")
        if (conference != null) {
            // Conferência local: sair dela encerra para os dois (BYE em cada um).
            heldCall?.terminate()
            activeCall?.terminate()
            return
        }
        consultationCall?.terminate()
        val call = activeCall
        if (call != null) {
            if (call.state == Call.State.IncomingReceived) decline() else call.terminate()
        } else if (heldCall == null && waitingCall == null) {
            // Referência perdida por algum motivo: garante que nada fique no ar.
            LinphoneManager.coreOrNull()?.terminateAllCalls()
        }
    }

    // --- Durante a chamada ----------------------------------------------------

    /** O liblinphone muta no nível do core (microfone), não por chamada. */
    fun setMuted(value: Boolean) {
        muted = value
        LinphoneManager.coreOrNull()?.isMicEnabled = !value
        // O mixer da conferência tem o próprio mudo.
        conference?.microphoneMuted = value
        publish()
    }

    fun setHeld(held: Boolean) {
        val call = activeCall ?: return
        if (held) call.pause() else call.resume()
        if (held) currentVoiceConnection?.setOnHold() else currentVoiceConnection?.setActive()
    }

    fun sendDtmf(digit: Char) {
        val core = LinphoneManager.coreOrNull() ?: return
        // O DTMF vai fora de banda: sem o tom local, o teclado fica mudo.
        core.playDtmf(digit, 120)
        activeCall?.sendDtmf(digit)
    }

    fun playLocalDtmf(digit: Char) {
        LinphoneManager.coreOrNull()?.playDtmf(digit, 120)
    }

    fun setAudioRoute(route: AudioRoute) {
        val core = LinphoneManager.coreOrNull() ?: return
        val call = activeCall ?: core.currentCall ?: return
        val output = core.audioDevices.firstOrNull {
            it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) && routeOf(it.type) == route
        } ?: return
        call.outputAudioDevice = output
        // No Bluetooth o microfone tem de ir junto, senão fala-se pelo aparelho.
        val input = core.audioDevices.firstOrNull {
            it.hasCapability(AudioDevice.Capabilities.CapabilityRecord) &&
                (if (route == AudioRoute.Bluetooth) it.type == AudioDevice.Type.Bluetooth
                else it.type == AudioDevice.Type.Microphone || it.type == AudioDevice.Type.Headset)
        }
        if (input != null) call.inputAudioDevice = input
        publish()
    }

    // --- Chamada em espera ----------------------------------------------------

    fun answerWaitingCall() {
        val incoming = waitingCall ?: return
        waitingCall = null
        cancelWaitingNotification()
        // Estaciona a conversa atual antes: atender sem pausar misturaria os dois áudios.
        activeCall?.let { it.pause(); heldCall = it }
        activeCall = incoming
        incoming.accept()
        Log.i(TAG, "Chamada em espera atendida; a anterior ficou em espera")
        publish()
    }

    fun declineWaitingCall() {
        val call = waitingCall ?: return
        Log.i(TAG, "Chamada em espera recusada")
        tracks[id(call)]?.note = "recusada (em outra chamada)"
        call.decline(Reason.Busy)
        waitingCall = null
        cancelWaitingNotification()
        publish()
    }

    fun swapCalls() {
        val goingToHold = activeCall ?: return
        val comingBack = heldCall ?: return
        goingToHold.pause()
        comingBack.resume()
        activeCall = comingBack
        heldCall = goingToHold
        Log.i(TAG, "Alternando entre as duas chamadas")
        publish()
    }

    // --- Conferência a três ---------------------------------------------------

    private fun canStartConference(): Boolean =
        LinphoneManager.coreOrNull() != null && conference == null && activeCall != null && waitingCall == null &&
            ((consultationCall != null && consultationAnswered) || heldCall != null)

    /**
     * Junta as duas chamadas atendidas (a consulta da transferência, ou as duas
     * da chamada em espera) numa conferência local. Sem endereço de fábrica de
     * conferência, o liblinphone mistura o áudio no próprio aparelho — nada do PBX.
     */
    fun startConference() {
        if (!canStartConference()) return
        val core = LinphoneManager.coreOrNull() ?: return
        val first = activeCall ?: return
        val other = consultationCall ?: heldCall ?: return
        val params = core.createConferenceParams(null).apply {
            isAudioEnabled = true
            isVideoEnabled = false
            isChatEnabled = false
            isLocalParticipantEnabled = true
            subjectUtf8 = "Conferência"
        }
        val conf = core.createConferenceWithParams(params)
        if (conf == null) {
            _hookMessage.value = "Não foi possível iniciar a conferência"
            return
        }
        conference = conf
        conf.addParticipant(first)
        conf.addParticipant(other)
        conf.microphoneMuted = muted
        // Daqui em diante as duas chamadas são a conferência: sem consulta,
        // sem estacionada, nada a transferir.
        heldCall = other
        consultationCall = null
        consultationAnswered = false
        transferTarget = ""
        transferCompleting = false
        Log.i(TAG, "Conferência iniciada: ${parties[id(first)]?.label} + ${parties[id(other)]?.label}")
        publish()
    }

    /** Desliga um dos dois (0 ou 1, na ordem de CallUiState.conference); o outro segue em chamada normal. */
    fun dropConferenceParticipant(index: Int) {
        if (conference == null) return
        (if (index == 0) activeCall else heldCall)?.terminate()
    }

    // --- Transferência (D-06 / D-07) ----------------------------------------

    /** Põe A em espera e disca para o destino (perna de consulta). */
    fun beginTransfer(target: String) {
        val core = LinphoneManager.coreOrNull() ?: return
        val call = activeCall ?: return
        val dest = target.trim()
        if (dest.isEmpty() || consultationCall != null) return
        transferTarget = dest
        consultationAnswered = false
        transferCompleting = false
        call.pause()
        val address = core.interpretUrl(dest, false)
        if (address == null) {
            _hookMessage.value = "Não foi possível interpretar o destino: $dest"
            call.resume()
            transferTarget = ""
            publish()
            return
        }
        consultationCall = core.inviteAddress(address)
        consultationCall?.let { activeLabels[id(it)] = "Chamando..." }
        publish()
    }

    /**
     * Destino já atendeu: REFER com Replaces (consultiva). Ainda chamando:
     * transferência cega — o mesmo gesto funciona com ou sem anúncio.
     */
    fun completeTransfer() {
        val call = activeCall ?: return
        val consult = consultationCall
        if (transferTarget.isEmpty() && consult == null) return
        tracks[id(call)]?.note = "transferida para $transferTarget"
        transferCompleting = true
        if (consult != null && consultationAnswered) {
            Log.i(TAG, "Concluindo transferência consultiva")
            call.transferToAnother(consult)
        } else {
            Log.i(TAG, "Concluindo transferência cega para $transferTarget")
            consult?.terminate()
            consultationCall = null
            LinphoneManager.coreOrNull()?.interpretUrl(transferTarget, false)?.let { call.transferTo(it) }
        }
        transferTarget = ""
        consultationAnswered = false
        publish()
    }

    fun cancelTransfer() {
        transferTarget = ""
        consultationAnswered = false
        transferCompleting = false
        consultationCall?.terminate()
        consultationCall = null
        activeCall?.resume()
        publish()
    }

    // --- Estados do engine ----------------------------------------------------

    private fun handleCallState(core: Core, call: Call, state: Call.State, message: String) {
        val callId = id(call)
        val isConsultation = same(call, consultationCall)
        Log.i(TAG, "Chamada ${if (isConsultation) "(consulta)" else ""} estado: $state ${if (message.isNotBlank()) "| $message" else ""}")

        if (same(call, activeCall) || isConsultation) {
            when (state) {
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia, Call.State.Connected,
                Call.State.StreamsRunning, Call.State.UpdatedByRemote, Call.State.Referred -> publishRemoteParty(call)
                else -> {}
            }
        }

        when (state) {
            Call.State.IncomingReceived -> onIncoming(core, call)

            Call.State.OutgoingInit, Call.State.OutgoingProgress -> activeLabels[callId] = "Chamando..."
            Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> activeLabels[callId] = "Tocando..."

            Call.State.Connected, Call.State.StreamsRunning -> {
                if (!connectedAt.containsKey(callId)) connectedAt[callId] = SystemClock.elapsedRealtime()
                activeLabels[callId] = if (isConsultation) "Em conversa" else "Em chamada"
                tracks[callId]?.answered = true
                if (isConsultation) consultationAnswered = true
                if (same(call, activeCall)) {
                    stopRinging()
                    currentVoiceConnection?.setActive()
                }
                if (state == Call.State.StreamsRunning) logMedia(core, call)
            }

            Call.State.Pausing, Call.State.Paused -> activeLabels[callId] = "Em espera"
            Call.State.PausedByRemote -> activeLabels[callId] = "Em espera pelo outro lado"
            Call.State.Resuming -> activeLabels[callId] = "Retomando..."
            Call.State.Referred -> activeLabels[callId] = "Transferindo..."

            Call.State.Error, Call.State.End -> onCallFinished(call, state, message)
            Call.State.Released -> onCallFinished(call, state, message)
            else -> {}
        }
        publish()
    }

    private fun onIncoming(core: Core, call: Call) {
        val remote = call.remoteAddress
        val number = remote.username ?: ""
        val name = remote.displayName ?: ""

        // Siga-me primeiro: 302 direto para o outro ramal, sem tocar aqui.
        val forward = _forwardTarget.value
        if (forward.isNotEmpty()) {
            val target = core.interpretUrl(forward, false)
            if (target != null) {
                Log.i(TAG, "Siga-me: redirecionando chamada para $forward")
                call.redirectTo(target)
                recordAutoHandled(number, name, "encaminhada para $forward")
                return
            }
            Log.w(TAG, "Siga-me: destino inválido, ignorando: $forward")
        }

        if (_doNotDisturb.value) {
            Log.i(TAG, "Não perturbe: recusando chamada recebida")
            call.decline(Reason.Busy)
            recordAutoHandled(number, name, "recusada (não perturbe)")
            return
        }

        val track = Track(peer = number, displayName = name, startedAt = System.currentTimeMillis(), incoming = true)

        // Chamada em espera. Nunca reatribuir activeCall aqui: no desktop isso
        // derrubava a UI para "em repouso" com a conversa real ainda no ar.
        if (activeCall != null) {
            val canWait = consultationCall == null && heldCall == null && waitingCall == null
            if (!canWait) {
                Log.i(TAG, "Linha ocupada: recusando chamada de $number")
                call.decline(Reason.Busy)
                recordAutoHandled(number, name, "recusada (linha ocupada)")
                return
            }
            waitingCall = call
            parties[id(call)] = Party(name, number)
            tracks[id(call)] = track
            playWaitingBeep()
            showWaitingNotification(Party(name, number))
            return
        }

        activeCall = call
        parties[id(call)] = Party(name, number)
        tracks[id(call)] = track
        _hookMessage.value = ""
        startRinging()
        appContext?.let { TelecomHelper.startIncomingCall(it, number) }
    }

    private fun onCallFinished(call: Call, state: Call.State, message: String) {
        val callId = id(call)
        // End e Released chegam os dois; só o primeiro conta.
        val track = tracks.remove(callId)
        if (track != null) {
            writeHistory(call, track)
            if (same(call, activeCall) && !track.answered && !track.incoming) {
                _hookMessage.value = failureText(call, state, message)
            }
        }

        if (conference != null && (same(call, activeCall) || same(call, heldCall))) {
            // Um dos participantes saiu: o liblinphone já devolve o outro a uma
            // chamada normal; aqui só acompanhamos. Com os ponteiros ajustados,
            // o restante desta função não confunde a chamada que terminou.
            val remaining = if (same(call, activeCall)) heldCall else activeCall
            conference = null
            activeCall = remaining
            heldCall = null
            Log.i(TAG, "Conferência encerrada; segue a chamada com ${remaining?.let { parties[id(it)]?.label }}")
        }

        if (same(call, waitingCall)) {
            // Quem ligava desistiu antes de ser atendido.
            waitingCall = null
            cancelWaitingNotification()
        }
        if (same(call, heldCall)) {
            // O estacionado desligou enquanto falávamos com o outro.
            heldCall = null
        }
        if (same(call, consultationCall)) {
            consultationCall = null
            consultationAnswered = false
            // O destino desligou ou não atendeu: volta para quem estava em espera,
            // em vez de deixar A esquecido na música de espera.
            if (!transferCompleting && transferTarget.isNotEmpty()) {
                transferTarget = ""
                activeCall?.resume()
                _hookMessage.value = "Transferência não concluída"
            }
        }
        if (same(call, activeCall)) {
            activeCall = null
            stopRinging()
            transferCompleting = false
            when {
                // Terminar a chamada para a qual se alternou devolve a outra,
                // como todo telefone de mesa faz.
                heldCall != null -> {
                    activeCall = heldCall
                    heldCall = null
                    activeCall?.resume()
                    Log.i(TAG, "Chamada encerrada; retomando a que estava em espera")
                }
                // A conversa acabou com a segunda ainda tocando: ela vira a
                // chamada recebida, em vez de sumir.
                waitingCall != null -> {
                    activeCall = waitingCall
                    waitingCall = null
                    cancelWaitingNotification()
                    startRinging()
                    Log.i(TAG, "Chamada encerrada; a que aguardava vira a chamada recebida")
                }
            }
        }
        if (state == Call.State.Released) {
            activeLabels.remove(callId)
            parties.remove(callId)
            connectedAt.remove(callId)
        }
        endSessionIfIdle()
    }

    /** Sem nenhuma chamada: encerra a conexão do sistema e zera o mudo. */
    private fun endSessionIfIdle() {
        if (activeCall != null || heldCall != null || waitingCall != null || consultationCall != null) return
        stopRinging()
        currentVoiceConnection?.let {
            it.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            it.destroy()
        }
        currentVoiceConnection = null
        if (muted) {
            muted = false
            LinphoneManager.coreOrNull()?.isMicEnabled = true
        }
        publish()
    }

    private fun failureText(call: Call, state: Call.State, message: String): String = when (call.reason) {
        Reason.Busy -> "Ocupado"
        Reason.Declined -> "Chamada recusada"
        Reason.NotFound -> "Número não encontrado"
        Reason.NotAnswered -> "Não atendeu"
        Reason.Forbidden -> "Chamada não permitida"
        // 488: nenhum codec oferecido é aceito pelo servidor (Ajustes → Áudio → Codecs).
        Reason.NotAcceptable -> "Codec não aceito pelo servidor"
        Reason.IOError -> "Sem conexão com o servidor"
        Reason.None -> if (state == Call.State.Error) message.ifBlank { "Erro na chamada" } else ""
        else -> message
    }

    // --- Histórico -------------------------------------------------------------

    private fun writeHistory(call: Call, track: Track) {
        // Grupo de toque: todos os aparelhos do ramal tocam juntos e, quando um
        // atende, o PBX cancela os outros com "Reason: SIP;cause=200 Call
        // completed elsewhere". O liblinphone traduz isso no status do call
        // log — é o que separa uma chamada atendida no desktop de uma perdida.
        val elsewhere = if (track.incoming && !track.answered) when (call.callLog.status) {
            org.linphone.core.Call.Status.AcceptedElsewhere -> "atendida em outro aparelho"
            org.linphone.core.Call.Status.DeclinedElsewhere -> "recusada em outro aparelho"
            else -> ""
        } else ""
        val note = when {
            elsewhere.isNotEmpty() -> elsewhere
            track.note.isNotEmpty() -> track.note
            !track.answered -> if (track.incoming) "não atendida" else "sem resposta"
            track.connectedPeer.isNotEmpty() && track.connectedPeer != track.peer -> "atendida por ${track.connectedPeer}"
            else -> ""
        }
        CallHistoryStore.append(
            CallRecord(
                peer = track.peer,
                displayName = track.displayName,
                startedAt = track.startedAt,
                durationSeconds = if (track.answered) call.duration else 0,
                incoming = track.incoming,
                answered = track.answered,
                note = note,
            )
        )
    }

    private fun recordAutoHandled(number: String, name: String, note: String) {
        CallHistoryStore.append(
            CallRecord(peer = number, displayName = name, startedAt = System.currentTimeMillis(),
                incoming = true, answered = false, note = note)
        )
    }

    // --- Identidade do interlocutor ------------------------------------------

    private val userRe = Regex("""[sS][iI][pP][sS]?:([^@>;\s]+)""")
    private val quotedNameRe = Regex("\"([^\"]*)\"")

    /**
     * Quem está de fato do outro lado, que nem sempre é quem foi discado
     * (grupo de captura, desvio, transferência). P-Asserted-Identity ou
     * Remote-Party-ID, com o endereço remoto do diálogo como reserva.
     */
    private fun publishRemoteParty(call: Call) {
        var name = ""
        var number = ""
        // Identidade com o nosso próprio ramal é descartada: o PBX devolve a
        // de quem ligou em algumas respostas ("2128" <sip:2128@pbx> enquanto
        // o 2128 liga para o 2123), e aceitá-la punha o próprio ramal no
        // visor. O parâmetro "party" não serve para isso: este PBX marca a
        // identidade de quem foi chamado, no 180, também como party=calling.
        val ownNumber = LinphoneManager.coreOrNull()?.defaultAccount?.params?.identityAddress?.username ?: ""
        call.remoteParams?.let { params ->
            for (header in listOf("P-Asserted-Identity", "Remote-Party-ID")) {
                val raw = params.getCustomHeader(header) ?: continue
                val user = userRe.find(raw) ?: continue
                if (ownNumber.isNotEmpty() && user.groupValues[1] == ownNumber) continue
                number = user.groupValues[1]
                name = quotedNameRe.find(raw)?.groupValues?.get(1)?.trim()
                    ?: raw.substringBefore('<', "").trim()
                break
            }
        }
        if (number.isEmpty()) {
            number = call.remoteAddress.username ?: ""
            if (name.isEmpty()) name = call.remoteAddress.displayName ?: ""
        }
        if (number.isEmpty()) return
        val callId = id(call)
        val previous = parties[callId]
        // Mantém o nome já conhecido se o PBX não mandou um novo.
        parties[callId] = Party(name.ifEmpty { if (previous?.number == number) previous.name else "" }, number)
        tracks[callId]?.let { t ->
            if (name.isNotEmpty()) t.displayName = name
            t.connectedPeer = number
        }
    }

    private fun logMedia(core: Core, call: Call) {
        val pt = call.currentParams.usedAudioPayloadType
        Log.i(TAG, "[audio] chamada ativa | codec: ${pt?.mimeType ?: "?"} | saída: ${core.outputAudioDevice?.deviceName ?: "?"}")
    }

    // --- Publicação do estado de UI ------------------------------------------

    private fun publish() {
        val core = LinphoneManager.coreOrNull()
        val call = activeCall
        val phase = when {
            call == null -> CallPhase.Idle
            call.state == Call.State.IncomingReceived || call.state == Call.State.IncomingEarlyMedia -> CallPhase.Incoming
            call.state == Call.State.OutgoingInit || call.state == Call.State.OutgoingProgress ||
                call.state == Call.State.OutgoingRinging || call.state == Call.State.OutgoingEarlyMedia -> CallPhase.Outgoing
            else -> CallPhase.Active
        }
        val consult = consultationCall
        val transfer = if (consult != null || transferTarget.isNotEmpty()) {
            TransferUi(
                target = consult?.let { parties[id(it)]?.label } ?: transferTarget,
                answered = consultationAnswered,
                label = consult?.let { activeLabels[id(it)] } ?: "Transferindo...",
                connectedAt = consult?.let { connectedAt[id(it)] },
            )
        } else null

        val routes = core?.audioDevices
            ?.filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
            ?.mapNotNull { routeOf(it.type) }?.distinct()?.sortedBy { it.ordinal } ?: emptyList()
        val currentRoute = (call?.outputAudioDevice ?: core?.outputAudioDevice)?.type?.let { routeOf(it) }
            ?: AudioRoute.Earpiece

        _ui.value = CallUiState(
            phase = phase,
            party = call?.let { parties[id(it)] },
            statusLabel = when (phase) {
                CallPhase.Incoming -> "Chamada recebida"
                CallPhase.Idle -> ""
                else -> call?.let { activeLabels[id(it)] } ?: "Conectando..."
            },
            connectedAt = call?.let { connectedAt[id(it)] },
            muted = muted,
            held = call?.state == Call.State.Paused || call?.state == Call.State.Pausing,
            waiting = waitingCall?.let { parties[id(it)] },
            parked = if (conference == null) heldCall?.let { parties[id(it)] } else null,
            conference = if (conference != null) listOfNotNull(activeCall, heldCall).map { parties[id(it)] ?: Party("", "") } else null,
            canConference = canStartConference(),
            dialed = call?.let { c ->
                val track = tracks[id(c)]
                val connected = parties[id(c)]?.number
                if (track != null && !track.incoming && connected != null && connected != track.peer) track.peer else ""
            } ?: "",
            transfer = transfer,
            audioRoute = currentRoute,
            availableRoutes = routes,
        )
        syncForegroundService(_ui.value)
    }

    // Quem está no serviço de chamada agora; null = serviço parado. Evita
    // reiniciar o serviço a cada evento da chamada.
    private var foregroundWho: String? = null

    /**
     * Serviço em primeiro plano enquanto há chamada efetuada ou atendida. A
     * que só está tocando já tem a notificação de chamada recebida.
     */
    private fun syncForegroundService(state: CallUiState) {
        val ctx = appContext ?: return
        val live = state.phase == CallPhase.Outgoing || state.phase == CallPhase.Active
        val who = if (live) state.party?.label.orEmpty() else null
        if (who == foregroundWho) return
        foregroundWho = who
        if (who != null) {
            com.rrpsystems.rrphone.core.telecom.CallForegroundService.start(ctx, who)
        } else {
            com.rrpsystems.rrphone.core.telecom.CallForegroundService.stop(ctx)
        }
    }

    private fun routeOf(type: AudioDevice.Type): AudioRoute? = when (type) {
        AudioDevice.Type.Earpiece -> AudioRoute.Earpiece
        AudioDevice.Type.Speaker -> AudioRoute.Speaker
        AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> AudioRoute.Headset
        AudioDevice.Type.Bluetooth -> AudioRoute.Bluetooth
        else -> null
    }

    // O wrapper Java pode entregar objetos diferentes para a mesma chamada
    // nativa; o Call-ID é a identidade estável.
    private fun id(call: Call): String = call.callLog.callId ?: call.hashCode().toString()
    private fun same(a: Call?, b: Call?) = a != null && b != null && (a === b || id(a) == id(b))

    // --- Toque, bipe e notificação ---------------------------------------------

    private fun startRinging() {
        val ctx = appContext ?: return
        stopRinging()
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                ringtone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                ringtone?.play()
            }
            if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 1200), 0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tocar", e)
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.takeIf { it.isPlaying }?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar o toque", e)
        }
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    /**
     * Bipe discreto de chamada em espera, no fluxo de voz: quem está na linha
     * não ouve nada, e o toque cheio falaria por cima da conversa.
     */
    private fun playWaitingBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            tone.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 900)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 1500)
        } catch (e: Exception) {
            Log.w(TAG, "Bipe de chamada em espera indisponível", e)
        }
    }

    private fun showWaitingNotification(party: Party) {
        val ctx = appContext ?: return
        if (MainActivity.isAppInForeground) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "rrphone_call_waiting"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Chamada em espera", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            WAITING_NOTIFICATION_ID,
            NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Chamada em espera")
                .setContentText(party.label)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun cancelWaitingNotification() {
        val ctx = appContext ?: return
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(WAITING_NOTIFICATION_ID)
    }
}

package com.rrpsystems.rrphone.core.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rrpsystems.rrphone.MainActivity
import com.rrpsystems.rrphone.R
import com.rrpsystems.rrphone.core.sip.CallManager

class VoiceConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i("VoiceConnectionSvc", "Criando conexão recebida nativa (Tela de Bloqueio)")
        
        val connection = VoiceConnection()
        val caller = request?.address
        val callerName = caller?.schemeSpecificPart ?: "Desconhecido"
        
        connection.setAddress(caller, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        
        // Define que a ligação está chamando
        connection.setRinging()
        connection.setInitialized()

        CallManager.currentVoiceConnection = connection

        // Se o app já estiver aberto na tela, não precisamos jogar a notificação de bloqueio
        if (!MainActivity.isAppInForeground) {
            // Disparar Notificação FullScreen (Para o Android acender a tela)
            showIncomingCallNotification(callerName)
        }

        return connection
    }

    private fun showIncomingCallNotification(caller: String) {
        val channelId = "rrphone_incoming_calls"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chamadas Recebidas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de chamadas SIP"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para tela cheia (quando bloqueado)
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALLER_NAME", caller)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0,
            fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação: Recusar
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.rrpsystems.rrphone.ACTION_DECLINE_CALL"
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, 1,
            declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação: Atender (Abre o App principal e atende)
        val answerIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "com.rrpsystems.rrphone.ACTION_ANSWER_CALL"
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this, 2,
            answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Estilo nativo de chamada do Android (Garante os botões vermelho e verde grandes no Android 12+)
        val callerPerson = androidx.core.app.Person.Builder()
            .setName(caller)
            .setImportant(true)
            .build()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(callerPerson, declinePendingIntent, answerPendingIntent))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)

        notificationManager.notify(1001, notificationBuilder.build())
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i("VoiceConnectionSvc", "Criando conexão efetuada nativa")
        
        val connection = VoiceConnection()
        val target = request?.address
        connection.setAddress(target, TelecomManager.PRESENTATION_ALLOWED)
        
        // Define que a ligação está discando
        connection.setDialing()
        connection.setInitialized()

        CallManager.currentVoiceConnection = connection
        
        // Dispara a ligação real pelo Liblinphone
        val numberToCall = target?.schemeSpecificPart
        if (numberToCall != null) {
            CallManager.internalMakeCall(numberToCall)
        }

        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e("VoiceConnectionSvc", "Falha ao criar conexão recebida nativa")
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e("VoiceConnectionSvc", "Falha ao criar conexão efetuada nativa")
    }
}

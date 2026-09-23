package com.rrpsystems.rrphone.core.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import com.rrpsystems.rrphone.MainActivity
import com.rrpsystems.rrphone.R
import com.rrpsystems.rrphone.core.sip.CallManager

/**
 * Mantém o processo vivo enquanto existe uma chamada. Sem ele, ao sair da
 * tela o Android pode encerrar o app no meio da conversa (e cortar o
 * microfone), e desde o Android 14 um app de chamadas precisa declarar esse
 * serviço com o tipo phoneCall.
 *
 * A notificação é a da própria chamada (estilo "chamada em andamento", com
 * Desligar), então não aparece nada a mais para o usuário.
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val CHANNEL_ID = "rrphone_ongoing_call"
        private const val NOTIFICATION_ID = 1003
        private const val EXTRA_WHO = "who"
        private const val ACTION_HANG_UP = "com.rrpsystems.rrphone.ACTION_HANG_UP"

        /** Inicia ou atualiza (quem está na linha). */
        fun start(context: Context, who: String) {
            val intent = Intent(context, CallForegroundService::class.java).putExtra(EXTRA_WHO, who)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Ex.: app em segundo plano sem isenção. A chamada segue; só
                // fica sem a proteção contra ser encerrada pelo sistema.
                Log.w(TAG, "Não foi possível iniciar o serviço da chamada", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            CallManager.hangUp()
            return START_NOT_STICKY
        }
        val who = intent?.getStringExtra(EXTRA_WHO).orEmpty().ifBlank { "Chamada" }
        val notification = buildNotification(who)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // O tipo microphone exige o app visível ao iniciar; só phoneCall
            // basta para um app de chamadas registrado no Telecom.
            Log.w(TAG, "startForeground com microfone recusado; tentando só phoneCall", e)
            try {
                val phoneOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, phoneOnly)
            } catch (e2: Exception) {
                Log.e(TAG, "Falha ao iniciar o serviço da chamada", e2)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(who: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Chamada em andamento", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangUp = PendingIntent.getService(
            this, 11, Intent(this, CallForegroundService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(Person.Builder().setName(who).build(), hangUp))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .setOngoing(true)
            .setUsesChronometer(true)
            .build()
    }
}

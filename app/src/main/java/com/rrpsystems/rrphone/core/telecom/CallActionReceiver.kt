package com.rrpsystems.rrphone.core.telecom

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rrpsystems.rrphone.core.sip.CallManager

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.rrpsystems.rrphone.ACTION_DECLINE_CALL" -> {
                // Desliga a chamada SIP
                CallManager.decline()
                
                // Remove a notificação da barra superior
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(1001)
            }
        }
    }
}

package com.rrpsystems.rrphone.core.telecom

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import com.rrpsystems.rrphone.core.sip.LinphoneManager
import org.linphone.core.tools.firebase.FirebaseMessaging

/**
 * Push do Flexisip (push.rrpsystems.com.br). Todo push termina num REGISTER
 * novo pelo servidor de push, em poucos segundos:
 *
 * - chamada (loc-key = IC_MSG): o Flexisip segura o INVITE até esse REGISTER
 *   e só então o entrega; daí em diante é uma chamada recebida comum
 *   (Telecom + notificação em tela cheia);
 * - renovação (loc-key vazio, uma vez por dia): o REGISTER é o que mantém
 *   vivo o registro de 7 dias.
 *
 * Quem faz o trabalho é o serviço do próprio Linphone SDK, que entrega o
 * Call-ID ao Core (processPushNotification: renova os registros e espera a
 * chamada) e repassa um token novo do Firebase ao Core, que registra de novo
 * com o pn-prid atualizado. Sem este serviço declarado no manifesto, o push
 * chegava ao app e morria no FirebaseMessagingService padrão, sem REGISTER.
 *
 * Com o processo morto, o Android sobe o app antes de chamar este serviço:
 * RRPApplication.onCreate já criou o Core e aplicou a conta salva.
 */
class PushService : FirebaseMessaging() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        Log.i(TAG, "Push recebido | loc-key: ${data["loc-key"].orEmpty().ifEmpty { "(renovação)" }}" +
            " | call-id: ${data["call-id"].orEmpty()} | send-time: ${data["send-time"].orEmpty()}" +
            " | prioridade: ${remoteMessage.priority}/${remoteMessage.originalPriority}")
        super.onMessageReceived(remoteMessage)
        // Garantia extra: se o Core não estava pronto quando o SDK olhou, o
        // registro ainda assim é refeito assim que ele existir. O Core vive
        // na thread principal.
        Handler(Looper.getMainLooper()).post {
            LinphoneManager.coreOrNull()?.ensureRegistered()
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "Token novo do Firebase; registrando de novo")
        super.onNewToken(token)
    }

    private companion object {
        const val TAG = "PushService"
    }
}

package com.rrpsystems.rrphone.core.telecom

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

object TelecomHelper {
    private const val TAG = "TelecomHelper"
    lateinit var phoneAccountHandle: PhoneAccountHandle
        private set

    fun registerPhoneAccount(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(context, VoiceConnectionService::class.java)
        
        phoneAccountHandle = PhoneAccountHandle(componentName, "RRPhoneAccount")

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "RRP Softphone")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .build()

        try {
            telecomManager.registerPhoneAccount(phoneAccount)
            Log.i(TAG, "PhoneAccount registrado com sucesso para SELF_MANAGED.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar PhoneAccount", e)
        }
    }

    /**
     * Avisa o Android que temos uma chamada chegando.
     * O Android vai acordar a tela e mostrar a UI de atender padrão do sistema.
     */
    fun startIncomingCall(context: Context, callerNumber: String) {
        if (!::phoneAccountHandle.isInitialized) registerPhoneAccount(context)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("sip", callerNumber, null))
            // No futuro, podemos passar o Call ID do Liblinphone aqui se precisarmos mapear
        }

        try {
            telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
            Log.i(TAG, "Android notificado sobre chamada recebida: $callerNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao acionar a tela de chamada nativa", e)
        }
    }

    /**
     * Avisa o Android que o usuário quer iniciar uma chamada pelo nosso app.
     * Isso permite que a chamada apareça no histórico do Android e funcione com Bluetooth.
     */
    /** False quando o sistema recusou — quem chama disca direto nesse caso. */
    fun startOutgoingCall(context: Context, targetNumber: String): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        
        if (!::phoneAccountHandle.isInitialized) registerPhoneAccount(context)
        val uri = Uri.fromParts("sip", targetNumber, null)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
        }

        try {
            telecomManager.placeCall(uri, extras)
            Log.i(TAG, "Solicitando ao Android para iniciar chamada saindo: $targetNumber")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão MANAGE_OWN_CALLS negada ou não concedida.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar chamada pelo TelecomManager", e)
        }
        return false
    }
}

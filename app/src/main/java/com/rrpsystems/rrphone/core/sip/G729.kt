package com.rrpsystems.rrphone.core.sip

import android.util.Log
import org.linphone.core.Core

/**
 * G.729 no Android: a libmsbcg729.so (native/, compilada por
 * native/build-g729.ps1) traz o bcg729 e o filtro do mediastreamer2, que o SDK
 * do Maven não inclui, e aqui ela é registrada na fábrica de mídia do Core.
 *
 * A operadora fala G.711/G.729; sem G.729 no cliente, toda chamada externa
 * obriga o Asterisk a transcodificar.
 */
object G729 {
    private const val TAG = "G729"

    private val loaded: Boolean = try {
        System.loadLibrary("msbcg729")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "libmsbcg729.so ausente: G.729 indisponível (rode native/build-g729.ps1)", e)
        false
    }

    @JvmStatic
    private external fun register(corePointer: Long): Boolean

    /** Registra encoder e decoder. False = o app não deve oferecer G.729. */
    fun install(core: Core): Boolean {
        if (!loaded) return false
        return try {
            register(core.nativePointer).also {
                Log.i(TAG, if (it) "G.729 registrado no mediastreamer2" else "Falha ao registrar o G.729")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao registrar o G.729", e)
            false
        }
    }
}

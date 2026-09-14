package com.rrpsystems.rrphone

import android.app.Application
import android.util.Log
import com.rrpsystems.rrphone.core.sip.LinphoneManager

class RRPApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("RRPApplication", "Inicializando RRPApplication e serviços globais.")

        // Inicializar a Factory e o Core do Liblinphone
        // OBS: Isso pode ser movido para uma inicialização Assíncrona caso o 
        // tempo de inicialização impacte muito a abertura do app.
        LinphoneManager.start(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        // O Android raramente chama onTerminate em dispositivos reais, 
        // mas é boa prática ter o cleanup definido.
        LinphoneManager.destroy()
    }
}

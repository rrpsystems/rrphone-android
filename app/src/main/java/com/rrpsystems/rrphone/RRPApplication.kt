package com.rrpsystems.rrphone

import android.app.Application
import android.util.Log
import com.rrpsystems.rrphone.core.contacts.ContactsRepository
import com.rrpsystems.rrphone.core.history.CallHistoryStore
import com.rrpsystems.rrphone.core.settings.SettingsStore
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.LinphoneManager

class RRPApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("RRPApplication", "Inicializando RRPApplication e serviços globais.")

        SettingsStore.init(this)
        CallHistoryStore.init(this)
        ContactsRepository.init(this)

        LinphoneManager.start(this)
        CallManager.init(this)
        LinphoneManager.setAudioProcessing(
            SettingsStore.noiseSuppression, SettingsStore.echoCancellation, SettingsStore.automaticGainControl
        )

        // Conta salva: registra já, sem depender de a tela abrir.
        SettingsStore.loadProfile()?.let { profile ->
            LinphoneManager.applyAccount(profile)
            ContactsRepository.setUrl(profile.contactsUrl)
        }
    }
}

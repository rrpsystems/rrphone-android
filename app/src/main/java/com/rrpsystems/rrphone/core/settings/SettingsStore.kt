package com.rrpsystems.rrphone.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.rrpsystems.rrphone.core.security.KeystoreManager

/**
 * Configuração persistida entre execuções. Tudo o que não é sensível vai para
 * SharedPreferences; a senha SIP fica cifrada pelo Android Keystore
 * (KeystoreManager) — o equivalente ao Credential Manager do desktop.
 */
object SettingsStore {
    private const val PREFS = "rrp_settings"

    private lateinit var prefs: SharedPreferences
    private lateinit var keystore: KeystoreManager

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        keystore = KeystoreManager(context)
    }

    // --- Conta -----------------------------------------------------------

    fun saveProfile(profile: AccountProfile) {
        prefs.edit()
            .putString("displayName", profile.displayName)
            .putString("username", profile.username)
            .putString("domain", profile.domain)
            .putString("transport", profile.transport)
            .putString("dtmfMethod", profile.dtmfMethod)
            .putString("contactsUrl", profile.contactsUrl)
            .putString("codecs", profile.codecs.joinToString(","))
            .apply()
        keystore.saveSecureString(KeystoreManager.KEY_SIP_PASSWORD, profile.password)
    }

    /** Null numa instalação nova, ou depois de "Sair da conta". */
    fun loadProfile(): AccountProfile? {
        val username = prefs.getString("username", null) ?: return null
        val password = keystore.getSecureString(KeystoreManager.KEY_SIP_PASSWORD) ?: return null
        return AccountProfile(
            displayName = prefs.getString("displayName", "") ?: "",
            username = username,
            password = password,
            domain = prefs.getString("domain", "") ?: "",
            transport = prefs.getString("transport", "udp") ?: "udp",
            dtmfMethod = prefs.getString("dtmfMethod", "rfc2833") ?: "rfc2833",
            contactsUrl = prefs.getString("contactsUrl", "") ?: "",
            codecs = (prefs.getString("codecs", "") ?: "").split(',').filter { it.isNotBlank() },
        )
    }

    fun clearProfile() {
        prefs.edit()
            .remove("displayName").remove("username").remove("domain").remove("transport")
            .remove("dtmfMethod").remove("contactsUrl").remove("codecs")
            .apply()
        keystore.removeSecureString(KeystoreManager.KEY_SIP_PASSWORD)
    }

    // --- Não perturbe / siga-me --------------------------------------------
    // Sobrevivem a reinícios de propósito: quem desviou o ramal espera que ele
    // continue desviado.

    var doNotDisturb: Boolean
        get() = prefs.getBoolean("dnd", false)
        set(value) = prefs.edit().putBoolean("dnd", value).apply()

    var forwardTarget: String
        get() = prefs.getString("forwardTarget", "") ?: ""
        set(value) = prefs.edit().putString("forwardTarget", value.trim()).apply()

    // --- Processamento de áudio --------------------------------------------
    // Só o cancelamento de eco vem ligado, como no desktop.

    var noiseSuppression: Boolean
        get() = prefs.getBoolean("noiseSuppression", false)
        set(value) = prefs.edit().putBoolean("noiseSuppression", value).apply()

    var echoCancellation: Boolean
        get() = prefs.getBoolean("echoCancellation", true)
        set(value) = prefs.edit().putBoolean("echoCancellation", value).apply()

    var automaticGainControl: Boolean
        get() = prefs.getBoolean("agc", false)
        set(value) = prefs.edit().putBoolean("agc", value).apply()

    // --- Contatos -----------------------------------------------------------

    /** Quando ligado, a lista do servidor apaga os contatos locais ao chegar. */
    var replaceLocalContacts: Boolean
        get() = prefs.getBoolean("replaceLocalContacts", false)
        set(value) = prefs.edit().putBoolean("replaceLocalContacts", value).apply()
}

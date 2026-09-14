package com.rrpsystems.rrphone.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gerenciador de segurança que utiliza o Android Keystore System
 * para criptografar informações sensíveis (como a senha SIP e o JWT).
 * O texto em claro NUNCA toca o disco. Apenas o texto cifrado (ciphertext)
 * é salvo em SharedPreferences.
 */
class KeystoreManager(context: Context) {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createKeyIfNeeded()
    }

    private fun createKeyIfNeeded() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Criptografa uma string e salva o resultado (Ciphertext + IV) em SharedPreferences.
     */
    fun saveSecureString(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        
        val base64Encoded = Base64.encodeToString(combined, Base64.DEFAULT)
        prefs.edit().putString(key, base64Encoded).apply()
    }

    /**
     * Recupera a string criptografada e a descriptografa em memória.
     */
    fun getSecureString(key: String): String? {
        val base64Encoded = prefs.getString(key, null) ?: return null
        
        return try {
            val combined = Base64.decode(base64Encoded, Base64.DEFAULT)
            
            // GCM IV tem 12 bytes
            val iv = ByteArray(12)
            val ciphertext = ByteArray(combined.size - 12)
            
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            val plaintextBytes = cipher.doFinal(ciphertext)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Remove uma chave segura.
     */
    fun removeSecureString(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "RRP_SIP_KEY"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "rrp_secure_prefs"

        // Chaves conhecidas
        const val KEY_SIP_PASSWORD = "sip_password"
        const val KEY_AUTH_TOKEN = "auth_jwt_token"
    }
}

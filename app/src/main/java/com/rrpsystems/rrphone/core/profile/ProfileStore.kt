package com.rrpsystems.rrphone.core.profile

import android.util.Base64
import com.rrpsystems.rrphone.core.settings.AccountProfile
import com.rrpsystems.rrphone.core.settings.Codecs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Importação/exportação do .rrpprofile, formato 3 — o mesmo arquivo do
 * desktop (desktop/src/profile/ProfileStore.cpp). A conta inteira, inclusive
 * a URL de contatos (que costuma levar credenciais HTTP), vai dentro do
 * envelope cifrado.
 *
 * Os formatos 1 e 2 (senha com XOR, sem autenticação) nunca existiram no
 * mobile, então não são lidos aqui: quem tiver um arquivo desses reexporta
 * pelo desktop atual.
 */
object ProfileStore {

    class ProfileException(message: String) : Exception(message)

    fun export(profile: AccountProfile, passphrase: String): ByteArray {
        val account = JSONObject()
            .put("displayName", profile.displayName)
            .put("username", profile.username)
            .put("password", profile.password)
            .put("domain", profile.domain)
            .put("transport", profile.transport)
            .put("dtmfMethod", profile.dtmfMethod)
            .put("contactsUrl", profile.contactsUrl)
            .put("push", profile.pushEnabled)
            .put("outboundProxy", profile.outboundProxy)
            .put("codecs", JSONArray(profile.codecs))

        val box = ProfileCipher.seal(account.toString().toByteArray(Charsets.UTF_8), passphrase)
        val root = JSONObject()
            .put("version", 3)
            .put("cipher", "AES-256-GCM")
            .put("kdf", "PBKDF2-HMAC-SHA256")
            .put("passphrase", box.passphraseUsed)
            .put("salt", b64(box.salt))
            .put("nonce", b64(box.nonce))
            .put("tag", b64(box.tag))
            .put("payload", b64(box.ciphertext))
        return root.toString(4).toByteArray(Charsets.UTF_8)
    }

    /** Se o arquivo pede senha — para a UI só perguntar quando precisa. */
    fun requiresPassphrase(data: ByteArray): Boolean {
        val root = parseRoot(data)
        return root.optBoolean("passphrase", false)
    }

    fun import(data: ByteArray, passphrase: String): AccountProfile {
        val root = parseRoot(data)
        if (root.optInt("version") < 3) {
            throw ProfileException("Arquivo em formato antigo. Exporte novamente pela versão atual do desktop.")
        }
        val box = ProfileCipher.SealedBox(
            salt = unb64(root.optString("salt")),
            nonce = unb64(root.optString("nonce")),
            tag = unb64(root.optString("tag")),
            ciphertext = unb64(root.optString("payload")),
            passphraseUsed = root.optBoolean("passphrase", false),
        )
        val plaintext = try {
            ProfileCipher.open(box, passphrase)
        } catch (e: ProfileCipher.ProfileCipherException) {
            throw ProfileException(e.message ?: "Falha ao abrir o arquivo.")
        }
        val account = try {
            JSONObject(String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ProfileException("Conteúdo do perfil inválido após a descriptografia.")
        }

        val codecs = mutableListOf<String>()
        account.optJSONArray("codecs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optString(i)
                Codecs.findByName(name)?.let { codecs += it.mimeType }
            }
        }
        return AccountProfile(
            displayName = account.optString("displayName"),
            username = account.optString("username"),
            password = account.optString("password"),
            domain = account.optString("domain"),
            transport = account.optString("transport").ifBlank { "udp" },
            dtmfMethod = account.optString("dtmfMethod").ifBlank { "rfc2833" },
            contactsUrl = account.optString("contactsUrl"),
            // Sem a chave (arquivo do desktop, ou anterior a ela): push ligado,
            // que é o que um celular quer. O proxy próprio só vale com push desligado.
            pushEnabled = account.optBoolean("push", true),
            outboundProxy = account.optString("outboundProxy"),
            codecs = codecs,
        )
    }

    private fun parseRoot(data: ByteArray): JSONObject = try {
        JSONObject(String(data, Charsets.UTF_8))
    } catch (e: Exception) {
        throw ProfileException("Este arquivo não é um perfil RRP válido.")
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String): ByteArray = try {
        Base64.decode(text, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        ByteArray(0)
    }
}

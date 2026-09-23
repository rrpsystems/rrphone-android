package com.rrpsystems.rrphone.core.profile

import com.rrpsystems.rrphone.BuildConfig
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifra autenticada do .rrpprofile — byte a byte compatível com o desktop
 * (desktop/src/profile/ProfileCipher.cpp): AES-256-GCM, chave derivada por
 * PBKDF2-HMAC-SHA256 de (chave do app || senha opcional), sal de 16 bytes,
 * nonce de 12, tag de 16 e AAD "rrpprofile-v3".
 *
 * A chave do app vem de fora do repositório (profile_key.txt, injetado no
 * BuildConfig pelo build.gradle). Como no desktop, isso só encarece a
 * extração: a chave vai dentro de todo APK. Sem senha, o arquivo é ofuscado e
 * à prova de adulteração, não confidencial.
 */
object ProfileCipher {
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 200_000
    private val AAD = "rrpprofile-v3".toByteArray(Charsets.US_ASCII)

    class SealedBox(
        val salt: ByteArray,
        val nonce: ByteArray,
        val tag: ByteArray,
        val ciphertext: ByteArray,
        val passphraseUsed: Boolean,
    )

    class ProfileCipherException(message: String) : Exception(message)

    private fun appKey(): ByteArray {
        val hex = BuildConfig.RRP_PROFILE_KEY_HEX
        require(hex.length == 64) { "RRP_PROFILE_KEY_HEX malformada" }
        return ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /**
     * PBKDF2 do Java recebe a senha como char[] e a codifica em UTF-8 por
     * dentro (PBKDF2WithHmacSHA256 no Android/BoringSSL). A chave do app é
     * binária, então não dá para passá-la por ali sem estragar os bytes — por
     * isso o HMAC é feito à mão, sobre os bytes exatos que o mbedTLS usa.
     */
    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val secret = appKey() + passphrase.toByteArray(Charsets.UTF_8)
        return pbkdf2HmacSha256(secret, salt, ITERATIONS, KEY_BITS / 8)
    }

    internal fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        // HMAC aceita chave vazia em teoria, mas o SecretKeySpec não.
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA256"))
        val out = ByteArray(length)
        var block = 1
        var offset = 0
        while (offset < length) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val n = minOf(t.size, length - offset)
            System.arraycopy(t, 0, out, offset, n)
            offset += n
            block++
        }
        return out
    }

    fun seal(plaintext: ByteArray, passphrase: String): SealedBox {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        val sealed = cipher.doFinal(plaintext)
        // O Java devolve ciphertext||tag; o formato guarda os dois separados.
        val tagStart = sealed.size - TAG_BITS / 8
        key.fill(0)
        return SealedBox(
            salt = salt,
            nonce = nonce,
            tag = sealed.copyOfRange(tagStart, sealed.size),
            ciphertext = sealed.copyOfRange(0, tagStart),
            passphraseUsed = passphrase.isNotEmpty(),
        )
    }

    fun open(box: SealedBox, passphrase: String): ByteArray {
        if (box.salt.size != SALT_BYTES || box.nonce.size != NONCE_BYTES || box.tag.size != TAG_BITS / 8) {
            throw ProfileCipherException("Arquivo de perfil malformado.")
        }
        val key = deriveKey(passphrase, box.salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, box.nonce))
            cipher.updateAAD(AAD)
            return cipher.doFinal(box.ciphertext + box.tag)
        } catch (e: javax.crypto.AEADBadTagException) {
            // Uma só mensagem para toda falha: senha errada e arquivo adulterado
            // são indistinguíveis aqui.
            throw ProfileCipherException(
                if (box.passphraseUsed) "Senha incorreta, ou o arquivo está corrompido/alterado."
                else "O arquivo está corrompido ou foi alterado."
            )
        } finally {
            key.fill(0)
        }
    }
}

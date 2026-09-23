package com.rrpsystems.rrphone.core.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCipherTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // RFC 7914, seção 11: PBKDF2-HMAC-SHA256("passwd", "salt", 1, 64). A
    // derivação é feita à mão (para aceitar a chave binária do app), então
    // precisa bater com o vetor oficial — é o que garante que abre os
    // arquivos do desktop, que usa o mbedTLS.
    @Test
    fun pbkdf2MatchesRfc7914Vector() {
        val expected = hex(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
        )
        val actual = ProfileCipher.pbkdf2HmacSha256("passwd".toByteArray(), "salt".toByteArray(), 1, 64)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun roundTripWithAndWithoutPassphrase() {
        val plain = """{"username":"2125","password":"segredo"}""".toByteArray()
        for (pass in listOf("", "senha forte")) {
            val box = ProfileCipher.seal(plain, pass)
            assertEquals(pass.isNotEmpty(), box.passphraseUsed)
            assertEquals(16, box.tag.size)
            assertArrayEquals(plain, ProfileCipher.open(box, pass))
        }
    }

    @Test
    fun wrongPassphraseOrTamperingIsRejected() {
        val box = ProfileCipher.seal("conteudo".toByteArray(), "certa")
        assertTrue(runCatching { ProfileCipher.open(box, "errada") }.isFailure)
        val tampered = ProfileCipher.SealedBox(
            box.salt, box.nonce, box.tag,
            box.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, true
        )
        assertFalse(runCatching { ProfileCipher.open(tampered, "certa") }.isSuccess)
    }
}

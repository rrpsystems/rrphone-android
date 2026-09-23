package com.rrpsystems.rrphone.core.settings

/**
 * Conta SIP e preferências que viajam junto no .rrpprofile — o mesmo conjunto
 * de campos do desktop (desktop/src/profile/ProfileStore.h), para que um
 * arquivo exportado num lado seja importado no outro.
 */
data class AccountProfile(
    val displayName: String = "",
    val username: String = "",
    val password: String = "",
    // Servidor SIP, opcionalmente com porta: "sip.exemplo.com" ou "sip.exemplo.com:5090".
    val domain: String = "",
    val transport: String = "udp",      // "udp" | "tcp" | "tls"
    val dtmfMethod: String = "rfc2833", // "rfc2833" | "info" | "inband"
    val contactsUrl: String = "",
    // Nomes dos codecs habilitados, em ordem de prioridade. Vazio = padrão do app.
    val codecs: List<String> = emptyList(),
)

data class CodecInfo(
    val mimeType: String,
    val clockRate: Int,
    val channels: Int,
    val enabled: Boolean = true,
)

/**
 * Os codecs que o app oferece, na ordem padrão: banda estreita primeiro,
 * porque a operadora fala G.711/G.729 e começar pelo OPUS só empurraria
 * transcodificação para o PBX. Igual ao desktop.
 *
 * O G.729 vem da libmsbcg729.so do próprio app (ver core/sip/G729.kt): o
 * pacote do liblinphone publicado no Maven não traz o bcg729.
 */
object Codecs {
    val supported = listOf(
        CodecInfo("PCMA", 8000, 1),
        CodecInfo("PCMU", 8000, 1),
        CodecInfo("G729", 8000, 1),
        CodecInfo("opus", 48000, 2),
    )

    fun isSupported(mime: String, clockRate: Int, channels: Int) = supported.any {
        it.mimeType.equals(mime, ignoreCase = true) && it.clockRate == clockRate && it.channels == channels
    }

    fun findByName(name: String) = supported.firstOrNull { it.mimeType.equals(name, ignoreCase = true) }
}

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
    // Push ligado: registro e chamadas passam pelo Flexisip da RRP
    // (BuildConfig.RRP_PUSH_PROXY), que acorda o app quando chega chamada.
    // Não há outro servidor de push possível: só o nosso projeto Firebase
    // consegue enviar para este app. Contas novas vêm com push ligado.
    val pushEnabled: Boolean = true,
    // Só com o push desligado: proxy opcional (um SBC do cliente, por
    // exemplo). Vazio = direto no servidor SIP.
    val outboundProxy: String = "",
    // Nomes dos codecs habilitados, em ordem de prioridade. Vazio = padrão do app.
    val codecs: List<String> = emptyList(),
) {
    /** O proxy que vale de fato para esta conta; vazio = sem proxy. */
    val effectiveProxy: String
        get() = if (pushEnabled) com.rrpsystems.rrphone.BuildConfig.RRP_PUSH_PROXY else outboundProxy
}

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
/**
 * "push.exemplo.com" vira "sip:push.exemplo.com;transport=tls". Uma URI
 * sip:/sips: completa fica como foi digitada. TLS é o padrão porque é o que um
 * gateway de push na internet deve falar. Igual ao
 * SipCoreManager::normalizeProxyUri do desktop.
 */
fun normalizeProxyUri(proxy: String): String {
    var uri = proxy.trim()
    if (uri.isEmpty()) return uri
    val lower = uri.lowercase()
    if (!lower.startsWith("sip:") && !lower.startsWith("sips:")) uri = "sip:$uri"
    if (!uri.lowercase().contains("transport=") && !uri.lowercase().startsWith("sips:")) uri += ";transport=tls"
    return uri
}

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

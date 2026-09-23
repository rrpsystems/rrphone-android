package com.rrpsystems.rrphone.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyUriTest {
    @Test
    fun normalizesHostToTlsUri() {
        assertEquals("", normalizeProxyUri("  "))
        assertEquals("sip:push.rrpsystems.com.br;transport=tls", normalizeProxyUri("push.rrpsystems.com.br"))
        assertEquals("sip:push.rrpsystems.com.br:5070;transport=tls", normalizeProxyUri("push.rrpsystems.com.br:5070"))
        assertEquals("sip:proxy.exemplo.com;transport=udp", normalizeProxyUri("sip:proxy.exemplo.com;transport=udp"))
        assertEquals("sips:push.exemplo.com", normalizeProxyUri("sips:push.exemplo.com"))
    }

    @Test
    fun pushOnIgnoresCustomProxy() {
        assertEquals(com.rrpsystems.rrphone.BuildConfig.RRP_PUSH_PROXY, AccountProfile(pushEnabled = true, outboundProxy = "sbc.cliente").effectiveProxy)
        assertEquals("sbc.cliente", AccountProfile(pushEnabled = false, outboundProxy = "sbc.cliente").effectiveProxy)
        assertEquals("", AccountProfile(pushEnabled = false).effectiveProxy)
    }
}

package com.foleyit.itflow.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins what Server Setup will accept, because that string ends up in an OkHttp
 * base URL, a WebView baseUrl, and an `src="…"` attribute built by
 * [KbMediaUrl.rewriteContent].
 */
class ServerUrlTest {

    @Test
    fun `an ordinary https url is accepted`() {
        assertNull(ServerUrl.rejectionReason("https://mw-itflow.foleyit.com"))
        assertNull(ServerUrl.rejectionReason("https://itflow.local:8443/itflow"))
    }

    @Test
    fun `surrounding whitespace is trimmed rather than rejected`() {
        // Phone keyboards append a space after a paste more often than not.
        assertNull(ServerUrl.rejectionReason("  https://mw-itflow.foleyit.com  "))
    }

    @Test
    fun `the pre-existing https requirement is unchanged`() {
        assertEquals("URL must start with https://", ServerUrl.rejectionReason("http://h"))
        assertEquals("URL must start with https://", ServerUrl.rejectionReason("mw-itflow.foleyit.com"))
    }

    @Test
    fun `a scheme with no host is rejected`() {
        assertEquals(
            "URL needs a host, e.g. https://itflow.example.com",
            ServerUrl.rejectionReason("https://")
        )
        assertEquals(
            "URL needs a host, e.g. https://itflow.example.com",
            ServerUrl.rejectionReason("https:///")
        )
    }

    @Test
    fun `a url carrying html metacharacters is rejected at the source`() {
        // The exact string that injected markup into the KB article document
        // through KbMediaUrl.rewriteContent before it escaped its base.
        assertNotNull(ServerUrl.rejectionReason("""https://h"><script>alert(1)</script>"""))
        assertEquals(
            "URL must not contain the character \"",
            ServerUrl.rejectionReason("https://h\"x")
        )
        assertEquals("URL must not contain the character <", ServerUrl.rejectionReason("https://h<x"))
        assertEquals("URL must not contain the character >", ServerUrl.rejectionReason("https://h>x"))
        assertEquals("URL must not contain the character '", ServerUrl.rejectionReason("https://h'x"))
        assertEquals("URL must not contain the character \\", ServerUrl.rejectionReason("https://h\\x"))
    }

    @Test
    fun `an embedded space or control character is rejected`() {
        assertEquals("URL must not contain spaces", ServerUrl.rejectionReason("https://h ost"))
        assertEquals("URL must not contain spaces", ServerUrl.rejectionReason("https://h\nost"))
    }
}

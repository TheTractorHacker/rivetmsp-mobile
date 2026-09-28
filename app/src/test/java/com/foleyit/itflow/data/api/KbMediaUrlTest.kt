package com.foleyit.itflow.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the contract between `api/v1/kb.php` and this screen.
 *
 * Every literal below is a REAL string produced by running the server's own
 * `ITFlow\KB\MediaToken::signedUrl()` and `ITFlow\KB\MediaUrlRewriter::toSigned()`
 * (PHP 8.4.25, 2026-09-08) with a dummy 64-hex signing key injected by
 * reflection — not a hand-written guess at the shape. If the server changes the
 * URL shape, these break, which is the point: the app has no other way to notice.
 */
class KbMediaUrlTest {

    private val server = "https://mw-itflow.foleyit.com"

    // Traced from MediaToken::signedUrl(KIND_ATTACHMENT, 7, '', 't42', TTL_ATTACH).
    private val signedAttachment =
        "https://mw-itflow.foleyit.com/agent/kb_media.php?att=7&p=t42&e=1788976882" +
            "&s=535c8e759d551caa176c0e66f53f711d9ec0876683be8c94dc84980a178dcb06"

    @Test
    fun `signed attachment url on the configured server is passed through unchanged`() {
        assertEquals(signedAttachment, KbMediaUrl.forAttachment(signedAttachment, server))
    }

    @Test
    fun `signed attachment url on a different host is repointed at the configured server`() {
        // $config_base_url is captured once from HTTP_HOST at install time
        // (setup/index.php:57), so it can name a host this device cannot reach.
        // The host is not part of MediaToken::payload(), so moving it keeps the
        // signature valid.
        val stale = signedAttachment.replace("mw-itflow.foleyit.com", "itflow.local:8443")
        assertEquals(signedAttachment, KbMediaUrl.forAttachment(stale, server))
    }

    @Test
    fun `unsigned root-relative fallback still resolves against the server`() {
        // api/v1/kb.php's attachment loop emits this shape when the install has
        // no signing key yet (`$att_url ?? '/agent/kb_media.php?att=' . $att_id`).
        // Cited by symbol: that file is being edited around this change and a
        // stale line number reads as a lie.
        assertEquals(
            "$server/agent/kb_media.php?att=7",
            KbMediaUrl.forAttachment("/agent/kb_media.php?att=7", server)
        )
    }

    @Test
    fun `a trailing slash on the configured server url never doubles`() {
        assertEquals(
            "$server/agent/kb_media.php?att=7",
            KbMediaUrl.forAttachment("/agent/kb_media.php?att=7", "$server/")
        )
    }

    @Test
    fun `a subdirectory install keeps its path prefix`() {
        // $config_base_url is host-only, so the server cannot know about /itflow;
        // dropping the whole authority and re-prefixing is what recovers it.
        assertEquals(
            signedAttachment.replace("$server/agent/", "$server/itflow/agent/"),
            KbMediaUrl.forAttachment(signedAttachment, "$server/itflow")
        )
    }

    @Test
    fun `a userinfo authority is discarded rather than trusted`() {
        val spoofed =
            "https://mw-itflow.foleyit.com@evil.example/agent/kb_media.php?att=7&p=t42&e=1&s=x"
        assertEquals(
            "$server/agent/kb_media.php?att=7&p=t42&e=1&s=x",
            KbMediaUrl.forAttachment(spoofed, server)
        )
    }

    @Test
    fun `a non-media absolute url is left alone`() {
        val other = "https://vendor.example/downloads/manual.pdf"
        assertEquals(other, KbMediaUrl.forAttachment(other, server))
    }

    /**
     * The `content` string below is real `MediaUrlRewriter::toSigned()` output with
     * only its host swapped: purified HTML with `&amp;`-escaped separators, one
     * article-scoped image (`?a=&f=`), one TinyMCE-pool image (`?f=` alone), and one
     * genuinely external image that must not be touched.
     *
     * No `/uploads/kb/...` case, because the app has no answer for one: it holds no
     * signing key, so a raw upload path can only be left where it is. `toSigned()`
     * normalises the two shapes its `describe()` recognises
     * (`/uploads/kb/<id>/<name>` and flat `/uploads/kb/<name>`) before the JSON is
     * built; anything else it does not recognise — a subdirectory, a percent-encoded
     * space, a second dot in the name — stays a raw path and breaks in the app once
     * nginx denies /uploads. That is a server-side gap, recorded here so the absence
     * of a test is not mistaken for the absence of the case.
     */
    @Test
    fun `article html has only its kb media origins moved, byte for byte otherwise`() {
        val content =
            """<p>Runbook</p><img src="https://itflow.local/agent/kb_media.php?a=13&amp;""" +
                """f=aabbccddeeff00112233445566778899.png&amp;p=t42&amp;e=1788897682&amp;""" +
                """s=5c94daa0626847d430c20d098778b596627347dd3a1600d277c602d43a65bd8b" alt="diagram">""" +
                """<img src="https://itflow.local/agent/kb_media.php?f=d41d8cd98f00b204e9800998ecf8427e-Q.jpg&amp;""" +
                """p=t42&amp;e=1788897682&amp;s=ae35a863ead0c9668221e234cc54e43bcd62ade30ddabfe37daa134dc464a08c" alt="pool">""" +
                """<img src="https://example.com/external.png" alt="external">"""

        val expected = content.replace("https://itflow.local/agent/", "$server/agent/")

        assertEquals(expected, KbMediaUrl.rewriteContent(content, server))
    }

    @Test
    fun `entity escaping and the signature survive the rewrite untouched`() {
        val out = KbMediaUrl.rewriteContent(
            """<img src="https://other.host/agent/kb_media.php?a=13&amp;f=x.png&amp;p=t42&amp;e=9&amp;s=abc">""",
            server
        )
        // Never decoded, so `&amp;` is still `&amp;` — the WebView, not this code,
        // is what turns it back into `&` when it resolves the attribute.
        assertEquals(
            """<img src="$server/agent/kb_media.php?a=13&amp;f=x.png&amp;p=t42&amp;e=9&amp;s=abc">""",
            out
        )
    }

    @Test
    fun `content with no kb media is returned identical`() {
        val plain = """<p>Just text</p><img src="data:image/png;base64,iVBORw0KGgo=" alt="">"""
        assertEquals(plain, KbMediaUrl.rewriteContent(plain, server))
        assertEquals("", KbMediaUrl.rewriteContent("", server))
    }

    @Test
    fun `a protocol-relative url is parsed, not concatenated`() {
        assertEquals(
            "$server/agent/kb_media.php?att=7",
            KbMediaUrl.forAttachment("//evil.example/agent/kb_media.php?att=7", server)
        )
    }

    @Test
    fun `a host with no path is not mistaken for the media endpoint`() {
        assertEquals("https://evil.example", KbMediaUrl.forAttachment("https://evil.example", server))
        assertEquals(
            "https://evil.example?/agent/kb_media.php",
            KbMediaUrl.forAttachment("https://evil.example?/agent/kb_media.php", server)
        )
    }

    @Test
    fun `a path that merely ends with the endpoint name is not repointed`() {
        // Matched exactly, because MediaToken::signedUrl() hardcodes the path.
        val decoy = "https://evil.example/wp-content/agent/kb_media.php?att=7"
        assertEquals(decoy, KbMediaUrl.forAttachment(decoy, server))
    }

    // ---------------------------------------------------------------------
    // TOKEN CONFINEMENT. forAttachment() hands its result to a SEPARATE
    // browser app, so a `?s=` capability in it is a credential leaving this
    // app. These pin the rule its KDoc states: the token reaches the
    // configured origin or it reaches nothing.
    // ---------------------------------------------------------------------

    // hash_hmac('sha256', …) output shape: 64 hex. carriesCapabilityToken() checks
    // the length as well as the name, so `?s=<term>` search links stay openable.
    private val sig64 = "535c8e759d551caa176c0e66f53f711d9ec0876683be8c94dc84980a178dcb06"

    /** The shape a hand-edited `$config_base_url = 'host/itflow'` makes MediaToken sign. */
    private fun subdirSigned(host: String) =
        "https://$host/itflow/agent/kb_media.php?att=7&p=t42&e=1788976882" +
            "&s=535c8e759d551caa176c0e66f53f711d9ec0876683be8c94dc84980a178dcb06"

    @Test
    fun `a signed url on a host we were not configured against is refused, not opened`() {
        // repoint() cannot fix it (the path is not the canonical one and the app
        // cannot guess a server-side prefix), so the only safe answer is refusal.
        assertNull(KbMediaUrl.forAttachment(subdirSigned("somehost.example"), server))
    }

    @Test
    fun `the same subdirectory url IS opened when it is on the configured origin`() {
        val url = subdirSigned("mw-itflow.foleyit.com")
        assertEquals(url, KbMediaUrl.forAttachment(url, "$server/itflow"))
    }

    @Test
    fun `a userinfo authority cannot smuggle a token past the origin check`() {
        val spoofed =
            "https://mw-itflow.foleyit.com@evil.example/x/agent/kb_media.php?att=7&p=t42&e=1&s=$sig64"
        assertNull(KbMediaUrl.forAttachment(spoofed, server))
    }

    @Test
    fun `a plaintext downgrade of the configured host is refused`() {
        val downgraded = subdirSigned("mw-itflow.foleyit.com").replace("https://", "http://")
        assertNull(KbMediaUrl.forAttachment(downgraded, server))
    }

    @Test
    fun `an html-escaped separator does not hide the token from the origin check`() {
        assertNull(
            KbMediaUrl.forAttachment(
                "https://evil.example/x/agent/kb_media.php?att=7&amp;p=t42&amp;e=1&amp;s=$sig64",
                server
            )
        )
    }

    @Test
    fun `an external link with no token of ours is still opened unchanged`() {
        // The refusal keys on the capability parameter, not on being external:
        // a vendor download link in an attachment row must keep working.
        val vendor = "https://vendor.example/dl?file=manual.pdf&s3=eu-west-1"
        assertEquals(vendor, KbMediaUrl.forAttachment(vendor, server))
    }

    // ---------------------------------------------------------------------
    // ATTRIBUTE SAFETY AND SCANNER PRECISION.
    // ---------------------------------------------------------------------

    @Test
    fun `a server url containing html metacharacters cannot break out of the src attribute`() {
        // ServerSetupScreen rejects this shape now (ServerUrlTest), but the sink
        // is what has to hold: serverUrl is the one value here the server did not
        // escape for us.
        val hostile = "https://h\"><script>alert(1)</script>"
        val out = KbMediaUrl.rewriteContent("""<img src="/agent/kb_media.php?att=1" alt="">""", hostile)

        assertEquals(
            """<img src="https://h&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;/agent/kb_media.php?att=1" alt="">""",
            out
        )
        assertFalse(out.contains("<script>"))
    }

    @Test
    fun `an unbalanced quote in prose no longer swallows the next image`() {
        // Measured on the bundled HTMLPurifier 4.15.0: a `"` in a text node comes
        // back unescaped, so this content is reachable. A bare src= scanner
        // matched `src="foo</p><img src="` here and left the real image alone.
        val content =
            """<p>type src="foo</p><img src="/agent/kb_media.php?a=1&amp;f=x.png" alt="">"""

        assertEquals(
            """<p>type src="foo</p><img src="$server/agent/kb_media.php?a=1&amp;f=x.png" alt="">""",
            KbMediaUrl.rewriteContent(content, server)
        )
    }

    @Test
    fun `a src= written as prose outside a tag is not rewritten at all`() {
        val prose = """<p>Put src="/agent/kb_media.php?att=1" in the template.</p>"""
        assertEquals(prose, KbMediaUrl.rewriteContent(prose, server))
    }

    @Test
    fun `a self-closed img tag is rewritten - that is what the purifier emits`() {
        // HTMLPurifier 4.15.0 emits `<img … />`; measured.
        assertEquals(
            """<img src="$server/agent/kb_media.php?att=1" alt="" />""",
            KbMediaUrl.rewriteContent("""<img src="/agent/kb_media.php?att=1" alt="" />""", server)
        )
    }

    // ---------------------------------------------------------------------
    // HYPERLINKED MEDIA. The server rewrites <a href> alongside <img src>
    // (MediaUrlRewriter walks whole start tags), and a hyperlink is the shape
    // that leaves this app entirely — shouldOverrideUrlLoading hands it to a
    // browser with no cookie and no header of ours.
    // ---------------------------------------------------------------------

    @Test
    fun `a hyperlinked attachment has its origin moved like an image`() {
        val content =
            """<p>See <a href="https://itflow.local/agent/kb_media.php?att=5&amp;p=t42&amp;""" +
                """e=1788976882&amp;s=abc">the manual</a></p>"""

        assertEquals(
            content.replace("https://itflow.local/agent/", "$server/agent/"),
            KbMediaUrl.rewriteContent(content, server)
        )
    }

    @Test
    fun `an ordinary hyperlink is not touched`() {
        val content = """<p><a href="https://vendor.example/manual.pdf">datasheet</a></p>"""
        assertEquals(content, KbMediaUrl.rewriteContent(content, server))
    }

    @Test
    fun `wouldLeakCapability is the same rule both exits from the app apply`() {
        val signedElsewhere = subdirSigned("somehost.example")
        assertTrue(KbMediaUrl.wouldLeakCapability(signedElsewhere, server))
        // Same origin: the token is going where it was always going.
        assertFalse(KbMediaUrl.wouldLeakCapability(subdirSigned("mw-itflow.foleyit.com"), server))
        // No token: an external link is just an external link.
        assertFalse(KbMediaUrl.wouldLeakCapability("https://vendor.example/manual.pdf", server))
        // Root-relative resolves against the configured server by construction.
        assertFalse(KbMediaUrl.wouldLeakCapability("/agent/kb_media.php?att=7&s=$sig64", server))
        // And what forAttachment hands out never trips it — that is the contract.
        val handed = KbMediaUrl.forAttachment(signedAttachment.replace("mw-itflow", "itflow-old"), server)
        assertFalse(KbMediaUrl.wouldLeakCapability(handed!!, server))
    }

    @Test
    fun `a search link whose query happens to use s is not mistaken for a token`() {
        // `?s=<term>` is WordPress's search URL and shows up in vendor
        // documentation links; only a 64-hex `s` is a MediaToken signature.
        val blogSearch = "https://vendor.example/blog/?s=printer+driver"
        assertFalse(KbMediaUrl.wouldLeakCapability(blogSearch, server))
        assertEquals(blogSearch, KbMediaUrl.forAttachment(blogSearch, server))
    }
}

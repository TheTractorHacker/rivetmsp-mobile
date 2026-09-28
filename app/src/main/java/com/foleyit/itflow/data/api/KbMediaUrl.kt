package com.foleyit.itflow.data.api

/**
 * Repoints Knowledge Base media URLs at the server THIS APP was configured against.
 *
 * ---------------------------------------------------------------------------
 * WHAT CHANGED ON THE SERVER, AND WHY THE APP NOTICES
 * ---------------------------------------------------------------------------
 * KB media used to be addressed by its raw filesystem path — `/uploads/kb/...` —
 * served straight off nginx with no authentication at all. Those URLs were
 * ROOT-RELATIVE, so [KbArticleDetailScreen] resolved them against
 * [ApiClient.serverUrl] by construction: an `<img src="/uploads/kb/13/x.png">`
 * inside `loadDataWithBaseURL(baseUrl, …)` resolves against `baseUrl`, and the
 * attachment branch literally concatenated `"$baseUrl${att.url}"`.
 *
 * `api/v1/kb.php` now hands us ABSOLUTE, HMAC-signed URLs instead, of the shape
 * (traced against the real `ITFlow\KB\MediaToken::signedUrl()` on 2026-09-08):
 *
 *   https://<host>/agent/kb_media.php?att=7&p=t42&e=1788976882&s=<64 hex>
 *   https://<host>/agent/kb_media.php?a=13&f=<name>&p=t42&e=…&s=<64 hex>
 *
 * The signature is the app's ONLY credential on those requests: a WebView `<img>`
 * subresource and an external browser both send no `Authorization` header and no
 * cookie of ours, which is the entire reason the server signs them.
 *
 * ---------------------------------------------------------------------------
 * WHY REPOINT THEM AT ALL, WHEN THEY ARE ALREADY ABSOLUTE
 * ---------------------------------------------------------------------------
 * `<host>` above is the SERVER's `$config_base_url` setting, not the URL the
 * user typed into Server Setup. `setup/index.php` captures it once, from
 * `$_SERVER['HTTP_HOST']` at install time, and nothing updates it afterwards.
 * So on any install reached under a different name than it was set up under —
 * renamed host, LAN address vs public DNS, a non-default port, a reverse proxy —
 * the absolute URL points somewhere the app may not be able to reach, and every
 * KB image silently breaks. That failure mode did not exist while the URLs were
 * relative, so it is new, and it is ours to absorb.
 *
 * Two properties make repointing safe and correct:
 *
 *  1. THE HOST IS NOT SIGNED. `MediaToken::payload()` is
 *     `CONTEXT \n kind \n ref \n file \n principal \n expires` — the host appears
 *     nowhere in it, and `signedUrl()` merely prepends `https://$base_host` to a
 *     query string that is already complete. Swapping the origin therefore
 *     cannot invalidate the signature. Path and query are copied BYTE-IDENTICAL
 *     (see [repoint]), so `&amp;` escaping and the hex signature survive intact.
 *
 *  2. IT CONFINES THE CAPABILITY TOKEN. The token in `?s=` authenticates this
 *     app's principal, so it must only ever be sent to the server the user
 *     configured. The `$kb_media_host` normalisation in `api/v1/kb.php` refuses
 *     to sign against a caller-supplied `Host` header for exactly this reason —
 *     "echoing a capability token back on a caller-chosen host is a
 *     token-exfiltration primitive". The same rule is enforced from the client
 *     end: [wouldLeakCapability] is that rule, [forAttachment] applies it to the
 *     attachment list, and `openExternalUrl` applies it to every tap that leaves
 *     the article WebView. Between them they state EXACTLY how far it reaches.
 *
 * Repointing is a no-op on a correctly-configured install, which is the common
 * case — it only ever rewrites the scheme+authority, and only on URLs whose path
 * is exactly [ENDPOINT_PATH].
 *
 * Both `<img src>` and `<a href>` are moved, because the server signs both —
 * see [URL_ATTRIBUTE] for why the hyperlink case is the one that matters most
 * here, and what is deliberately left alone.
 *
 * NOT REWRITTEN, DELIBERATELY: anything that is not the KB media endpoint. An
 * article may legitimately embed `https://vendor.example/logo.png`, and a `data:`
 * URI (a TinyMCE paste) is passed through untouched. The `data:` case is NOT a
 * DOCX or PDF import artefact — `src/KB/DocxConverter.php` and
 * `src/KB/PdfConverter.php` contain no base64 image handling at all; both
 * importers write extracted images to disk and reference them by URL. It is what
 * a paste into TinyMCE leaves behind. Measured against the bundled HTMLPurifier
 * 4.15.0 with `api/v1/kb.php`'s own config: a VALID `data:image/png;base64,…`
 * survives purification byte-identical, an invalid payload is dropped whole
 * (4.15.0 runs getimagesize on it), so a `data:` src really can reach this code.
 */
object KbMediaUrl {

    /**
     * The one endpoint path KB media is served from. Matched EXACTLY, not by
     * suffix: `MediaToken::signedUrl()` hardcodes `'/agent/kb_media.php?'`, so
     * anything else is somebody else's URL and gets left alone.
     */
    const val ENDPOINT_PATH = "/agent/kb_media.php"

    /**
     * A START TAG in HTMLPurifier output — any element, not just `<img>`.
     *
     * ANCHORED ON THE TAG, NOT ON A BARE `src=` RUN, and that is a correctness
     * fix rather than a tidy-up. Measured against the bundled HTMLPurifier
     * 4.15.0 with `api/v1/kb.php`'s config: a `"` in a TEXT NODE comes back
     * UNESCAPED (`<p>type src="foo</p>` is byte-identical in and out), so a KB
     * article that documents HTML — a plausible runbook — shifts quote parity for
     * a bare `src="…"` scanner and makes it swallow the NEXT real image's src.
     * That image is then not repointed, and a bare scanner on the server would not
     * have SIGNED it either — which the app cannot repair, it holds no key, so the
     * image 302s to the login page and renders broken. `MediaUrlRewriter` moved to
     * a tag anchor off the same measurement, so the two ends now agree rather than
     * sharing a defect. A tag-anchored
     * match cannot desync on prose, because 4.15.0 escapes `<` and `>` in text
     * (`1<2` -> `1&lt;2`, measured), so a literal `<img` in prose is unreachable,
     * and prose that merely QUOTES an attribute keeps displaying what its author
     * wrote.
     *
     * `[^>]*` is a safe tag body for the same reason in reverse: 4.15.0 escapes
     * `>` inside attribute VALUES (`alt="a > b"` -> `alt="a &gt; b"`, measured),
     * so the first `>` after `<tag` really is the end of the tag.
     *
     * Deliberately the same shape as `ITFlow\KB\MediaUrlRewriter`'s own per-tag
     * walker on the server. Diverging in sophistication here would be a second,
     * subtly different set of edge cases over the same documents.
     */
    private val START_TAG = Regex("""<[a-zA-Z][^>]*>""")

    /**
     * `src="…"` or `href="…"` inside an already-matched tag.
     *
     * BOTH, because the server signs both. A KB author can hyperlink an
     * attachment from TinyMCE's link dialog (`<a href="/uploads/kb/13/x.pdf">`),
     * `MediaUrlRewriter` rewrites that href alongside `<img src>`, and in this
     * app a hyperlink is the case that matters MOST: tapping it fires
     * `shouldOverrideUrlLoading` and the URL goes to an EXTERNAL browser that has
     * neither our cookie nor our header. An href still naming a stale
     * `$config_base_url` host would send that tap to a host the device may not
     * reach — carrying a capability token there.
     *
     * NOT `style="…url(…)…"`, which the server does rewrite. A CSS `url()` is
     * a second quoting context stacked inside the HTML attribute, and the value
     * inserted here is the user-typed server URL. The server refuses that
     * rewrite outright when its replacement holds a quote, a paren or a space
     * rather than inventing a CSS escape; inventing one HERE, over a value this
     * app does not control, would be a new injection surface for a shape that is
     * not in the live corpus — `MediaUrlRewriter`'s own KDoc records the
     * measurement it was written from: 0 `kb_articles` rows matching `url(`.
     * A CSS background image therefore keeps whatever origin the server signed:
     * correct on any install whose `$config_base_url` matches the URL in Server
     * Setup, and broken on one where it does not — exactly as every URL was
     * before repointing existed.
     *
     * Double quotes only, because `api/v1/kb.php` purifies before it rewrites
     * and HTMLPurifier's generator always emits `name="value"` with the value
     * HTML-escaped — a single-quoted `src='…'` in the source comes back
     * double-quoted (measured). So `[^"]*` cannot run past the closing quote,
     * and a literal `"` inside a value is impossible: it would have been escaped
     * to `&quot;`. The pattern is linear (one unnested `[^"]*`), so it cannot
     * backtrack catastrophically on a large article.
     */
    private val URL_ATTRIBUTE = Regex("""(?i)\b(src|href)\s*=\s*"([^"]*)"""")

    /**
     * Rewrite the KB media origins inside article HTML, for the WebView.
     *
     * Operates on the ESCAPED attribute value and never decodes it: only the
     * scheme+authority prefix is replaced, so `&amp;` stays `&amp;` and the
     * signature is copied verbatim. Decoding and re-escaping would be a second
     * place for the `&` vs `&amp;` ambiguity to go wrong for no benefit.
     *
     * [serverUrl] IS ESCAPED FOR THE ATTRIBUTE, and it is the only value here
     * that needs it. Everything else in the emitted attribute reached us from
     * the server already HTML-escaped, but `serverUrl` is typed by the device
     * owner in Server Setup; unescaped, `https://h"><script>…` closed the
     * attribute and injected markup into the document handed to
     * `loadDataWithBaseURL` (bounded — `javaScriptEnabled = false` — but it was
     * the one unescaped value reaching HTML in this class). Escaping here is the
     * sink-side half; [ServerUrl.rejectionReason] refuses such a URL at the
     * source. It is lossless for the fetch: the WebView un-escapes the attribute
     * before resolving it, so a `&` in a base path survives as a `&`.
     */
    fun rewriteContent(html: String, serverUrl: String): String {
        // Cheap bail-out: most articles are text and tables with no media at all.
        if (html.isEmpty() || !html.contains(ENDPOINT_PATH)) return html

        val base = escapeForAttribute(serverUrl.trimEnd('/'))
        return START_TAG.replace(html) { tag ->
            // Per-tag early-out, the same predicate the whole document got
            // above: most tags in an article carry no URL at all, and skipping
            // them makes "a tag with no media comes back byte-identical"
            // structural rather than something the rewriting below happens to
            // be a no-op for.
            if (!tag.value.contains(ENDPOINT_PATH)) {
                tag.value
            } else {
                // Both replace() calls return their lambda's result LITERALLY —
                // no group substitution — so a `$` in a filename is safe at
                // both levels.
                URL_ATTRIBUTE.replace(tag.value) { attr ->
                    val repointed = repoint(attr.groupValues[2], base)
                    // groupValues[1] keeps whether the author wrote src or href,
                    // in the case they wrote it.
                    if (repointed == null) attr.value else "${attr.groupValues[1]}=\"$repointed\""
                }
            }
        }
    }

    /**
     * Resolve a [KbArticleAttachment.url] to the URL to hand an external browser,
     * or NULL when handing it out would send a capability token off the
     * configured server.
     *
     * Falls back to the pre-signing rule for anything that is not KB media, so
     * behaviour is unchanged for every other shape: an absolute URL is passed
     * through, a relative one is resolved against the configured server.
     *
     * THE NULL CASE, AND WHY IT EXISTS. [repoint] only recognises a path that is
     * EXACTLY [ENDPOINT_PATH]. An install whose `$config_base_url` has had a path
     * hand-edited into it (`somehost.example/itflow`) signs
     * `https://somehost.example/itflow/agent/kb_media.php?…&s=<64 hex>`, whose
     * path is not [ENDPOINT_PATH] — so before this check the fallback below
     * handed that URL, capability token and all, to an external browser on
     * whatever host the response named. That is precisely what the class KDoc
     * claims cannot happen, so the code now makes it true instead of the comment
     * describing it away:
     *
     *   - Same origin as [serverUrl] (scheme AND authority, case-insensitive)?
     *     Hand it over. This is the subdirectory install working correctly: the
     *     URL is already right, it is just not the canonical path.
     *   - A different origin, and the URL carries a signature (`s=<64 hex>`, see
     *     [carriesCapabilityToken])? REFUSE, and the caller disables the open
     *     action. A signed URL on a host we were not
     *     configured against is either a stale `$config_base_url` we cannot
     *     repair (we do not know the server's path prefix, and guessing one would
     *     be the same guess an attacker wants us to make) or an exfiltration
     *     attempt. Both end the same way: the token does not leave.
     *   - A different origin with NO token (a vendor download link in an
     *     attachment row)? Unchanged — handed over as before. Nothing of ours is
     *     in it.
     */
    fun forAttachment(url: String, serverUrl: String): String? {
        val base = serverUrl.trimEnd('/')
        repoint(url, base)?.let { return it }

        if (wouldLeakCapability(url, base)) return null

        return if (url.startsWith("http", ignoreCase = true)) url else "$base$url"
    }

    /**
     * Would handing [url] to another app send a capability token somewhere other
     * than the configured server?
     *
     * The rule [forAttachment] applies, made public because the attachment button
     * is not the only way a URL leaves this app: a tap on a hyperlink inside the
     * article WebView fires `shouldOverrideUrlLoading` and also ends in an
     * external browser. Both call sites ask this one question, so there is one
     * rule to read and one to test rather than two that drift apart.
     *
     * Ask it about a FINAL url — after [rewriteContent] or [forAttachment] have
     * had their say — since repointing is what puts a canonical URL on the
     * configured origin in the first place.
     */
    fun wouldLeakCapability(url: String, serverUrl: String): Boolean =
        carriesCapabilityToken(url) && !isOnConfiguredOrigin(url, serverUrl.trimEnd('/'))

    /**
     * `<anything>/agent/kb_media.php?<query>` -> `<base>/agent/kb_media.php?<query>`,
     * or null when the URL is not the KB media endpoint.
     *
     * Dropping the whole authority rather than editing it is what makes this
     * robust: a userinfo trick (`https://real.host@evil.example/agent/kb_media.php`)
     * loses `evil.example` along with everything else, and [base] contributes
     * whatever path the user entered in Server Setup.
     *
     * THE PATH IS MATCHED EXACTLY, so this is not a general subdirectory fixer.
     * If the SERVER's own URL carries a path prefix
     * (`https://host/itflow/agent/kb_media.php`, from a hand-edited
     * `$config_base_url`) this returns null and the caller decides — see
     * [forAttachment]. Exact matching is what keeps a decoy path
     * (`https://evil.example/wp-content/agent/kb_media.php`) from being pulled
     * onto our origin, and the prefix cannot simply be guessed: the app has no
     * way to tell a server-side subdirectory from an attacker-chosen one.
     */
    private fun repoint(url: String, base: String): String? {
        val pathAndQuery = when {
            url.startsWith("https://", ignoreCase = true) -> afterAuthority(url.substring(8))
            url.startsWith("http://", ignoreCase = true) -> afterAuthority(url.substring(7))
            // Protocol-relative. The server never emits one, but blind
            // concatenation onto `base` would produce `https://host//evil/…`,
            // so it gets parsed rather than assumed to be a path.
            url.startsWith("//") -> afterAuthority(url.substring(2))
            url.startsWith("/") -> url
            else -> null
        } ?: return null

        val path = pathAndQuery.substringBefore('?').substringBefore('#')
        if (path != ENDPOINT_PATH) return null

        return base + pathAndQuery
    }

    /**
     * Everything from the authority's terminating `/` onwards, or null if the
     * URL has no path at all (`https://host`, `https://host?x=1`) and therefore
     * cannot be the media endpoint.
     */
    private fun afterAuthority(afterScheme: String): String? {
        val end = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (end < 0 || afterScheme[end] != '/') return null
        return afterScheme.substring(end)
    }

    /**
     * Does this URL carry a `MediaToken` signature?
     *
     * A parameter named exactly `s` whose value is 64 hex characters — the shape
     * `MediaToken::sign()` produces (`hash_hmac('sha256', …)`, hex) and the only
     * shape `agent/kb_media.php` will even attempt to verify: it format-checks
     * `/^[a-f0-9]{64}$/` before touching the database.
     *
     * THE LENGTH CHECK IS NOT DECORATION. Without it this predicate refuses to
     * open any link with an `s=` parameter, and `?s=<term>` is WordPress's search
     * URL — a KB article linking to a vendor's blog search would have died with a
     * "different server" toast. Matching the real token shape keeps the refusal
     * aimed at what it is for. A malformed or truncated `s` is not a capability:
     * the serve endpoint rejects it before verifying, so nothing is lost by
     * letting it through.
     *
     * `&amp;` counts as a separator too — attachment URLs arrive raw in JSON
     * today, but a scanner blind to the escaped form would be one API change away
     * from being wrong in the unsafe direction.
     */
    private fun carriesCapabilityToken(url: String): Boolean {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return false
        return query.replace("&amp;", "&").split('&').any { param ->
            param.startsWith("s=") && SIGNATURE.matches(param.substring(2))
        }
    }

    /** `MediaToken::sign()` output: sha256 HMAC, hex. Case-insensitive, since a URL can be retyped. */
    private val SIGNATURE = Regex("""(?i)[0-9a-f]{64}""")

    /**
     * Is [url] on the same origin as the configured server — scheme AND
     * authority, compared case-insensitively as whole strings?
     *
     * Whole-string authority comparison is deliberate:
     * `https://cfg.host@evil.example` has authority `cfg.host@evil.example`,
     * which is not `cfg.host`, so the userinfo trick cannot read as same-origin.
     *
     * A URL with no authority of its own (root-relative, or a bare path) IS on
     * the configured origin — [forAttachment] resolves it against [base] by
     * concatenation. A protocol-relative `//host/…` has an authority but no
     * scheme, so it can never match, which is the safe answer.
     */
    private fun isOnConfiguredOrigin(url: String, base: String): Boolean {
        val urlOrigin = originOf(url) ?: return true
        val baseOrigin = originOf(base) ?: return false
        return urlOrigin == baseOrigin
    }

    /** `scheme://authority`, lowercased, or null when [url] names no authority. */
    private fun originOf(url: String): String? {
        val scheme = when {
            url.startsWith("https://", ignoreCase = true) -> "https"
            url.startsWith("http://", ignoreCase = true) -> "http"
            // `//host/…`: an authority with no scheme. Return a marker that
            // cannot equal a real origin rather than guessing the scheme.
            url.startsWith("//") -> return "//"
            else -> return null
        }
        val rest = url.substringAfter("://")
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (end < 0) rest else rest.substring(0, end)
        return "$scheme://${authority.lowercase()}"
    }

    /**
     * HTML escaping for a double-quoted attribute value. `&` first, or the
     * escapes themselves would be re-escaped.
     */
    private fun escapeForAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

package com.foleyit.itflow.data.api

/**
 * What this app will accept as a server URL, in one place.
 *
 * The URL the device owner types in Server Setup is stored in
 * [ApiClient.serverUrl] and then reaches several very different sinks: an OkHttp
 * `baseUrl`, a WebView `baseUrl` for `loadDataWithBaseURL`, and — since KB media
 * moved behind a signed endpoint — the value of an `src="…"` attribute in the
 * HTML that [KbMediaUrl.rewriteContent] builds. That last one is why this file
 * exists: a URL containing `"` and a `<tag>` was injected into the article
 * document as markup, because nothing on the way in had ruled such a string out
 * and nothing at the sink escaped it. [KbMediaUrl.rewriteContent] now escapes at
 * the sink, which is the fix that must hold on its own; this is the second half
 * of the same defence, rejecting the input where the user can still see why.
 *
 * Only the SOURCE is guarded here — an already-stored URL from an older install
 * never comes back through this function, so the sink-side escaping is what
 * actually protects it.
 *
 * Kept free of Android imports so it stays a plain JVM unit test target.
 */
object ServerUrl {

    /**
     * A message to show the user, or null when [input] is acceptable.
     *
     * The https:// requirement is the app's pre-existing rule (the manifest sets
     * `usesCleartextTraffic="false"`, so an `http://` URL could not connect
     * anyway); the rest rules out characters that cannot legally appear in a URL
     * authority or path and that carry meaning in one of the sinks above.
     */
    fun rejectionReason(input: String): String? {
        val url = input.trim()

        if (!url.startsWith("https://")) return "URL must start with https://"

        val afterScheme = url.substring("https://".length).trimEnd('/')
        if (afterScheme.isEmpty()) return "URL needs a host, e.g. https://itflow.example.com"

        if (url.any { it.isWhitespace() || it.isISOControl() }) {
            return "URL must not contain spaces"
        }
        // " < > break out of an HTML attribute or element; ' and ` are the same
        // hazard in the quoting styles this app does not use but a WebView does;
        // \ is not a URL separator and is a classic path-parsing divergence.
        FORBIDDEN.firstOrNull { url.contains(it) }?.let {
            return "URL must not contain the character $it"
        }

        return null
    }

    private val FORBIDDEN = listOf('"', '<', '>', '\'', '`', '\\')
}

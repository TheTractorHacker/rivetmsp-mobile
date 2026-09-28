package com.foleyit.itflow.ui.screens.kb

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.KbArticleDetail
import com.foleyit.itflow.data.api.KbMediaUrl
import com.foleyit.itflow.ui.components.ErrorScreen
import com.foleyit.itflow.ui.components.LoadingScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbArticleDetailScreen(id: Int, navController: NavController) {
    var state by remember { mutableStateOf<Result<KbArticleDetail>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        state = runCatching { ApiClient.service().getKbArticle(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.getOrNull()?.title ?: "Article") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val result = state) {
            null -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingScreen() }
            else -> result.fold(
                onSuccess = { article ->
                    ArticleContent(article, Modifier.fillMaxSize().padding(padding))
                },
                onFailure = {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        ErrorScreen(it.message ?: "Failed to load article") {
                            scope.launch { state = runCatching { ApiClient.service().getKbArticle(id) } }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ArticleContent(article: KbArticleDetail, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val baseUrl = ApiClient.serverUrl

    /* Built here, not in the update lambda, so that recomposition can be told apart
     * from a genuine content change — see the WHY on that lambda. */
    val articleHtml = remember(article.content, baseUrl) {
        articleDocument(KbMediaUrl.rewriteContent(article.content ?: "", baseUrl))
    }
    val loadedDocument = remember { LoadedDocument() }

    Column(modifier) {
        article.categoryName?.let { category ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        category,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            openExternalUrl(context, request.url)
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                /* Inline KB images are served by an authenticated PHP endpoint, not off
                 * /uploads, and the URLs arrive HMAC-signed because a WebView subresource
                 * carries NO credential we control: loadDataWithBaseURL() gives the document
                 * an origin, but the image fetch goes out on the system network stack with
                 * no Authorization header (ApiClient's interceptor is OkHttp-only) and no
                 * cookie of ours. The `?s=` capability in the URL is the whole credential.
                 *
                 * shouldOverrideUrlLoading() (in the factory above) does not fire for
                 * subresources — it is a navigation callback — so the images load in place
                 * rather than being bounced to an external browser.
                 *
                 * KbMediaUrl.rewriteContent() only moves those URLs onto the server this app
                 * is configured against; see its KDoc for why that is both safe (the host is
                 * not part of the signed payload) and necessary (the server signs against
                 * $config_base_url, captured once at install time from HTTP_HOST).
                 *
                 * WHAT THAT COSTS, STATED RATHER THAN LEFT OUT. kb_media_serve.php sends
                 * `Cache-Control: private, no-store, max-age=0` on every media response, so
                 * these bytes are never cached anywhere: the WebView does not go through
                 * OkHttp either (this app installs no shouldInterceptRequest), so the
                 * OkHttp disk cache is not in this path. Every load of this document
                 * re-downloads every inline image, where a /uploads URL carried no
                 * Cache-Control at all and was heuristically cacheable. That is the
                 * deliberate price of never persisting a capability URL or the file it
                 * names; the only honest lever, if it ever bites, is a short
                 * `private, max-age=` on image MIME types server-side — not a change here.
                 *
                 * Which is why the document is loaded only when it actually CHANGES.
                 * AndroidView's update lambda runs on every recomposition, and every
                 * loadDataWithBaseURL() is a fresh document that re-fetches every image, so
                 * without this guard an unrelated recomposition costs a full round of image
                 * downloads. A configuration change still recreates the WebView and
                 * refetches, and the URLs baked into `article` expire on the server's
                 * MediaToken::TTL_IMAGE — an article left on screen past that renders its
                 * images broken until the screen is re-entered and the API re-queried. */
                if (loadedDocument.html != articleHtml) {
                    webView.loadDataWithBaseURL(baseUrl, articleHtml, "text/html", "utf-8", null)
                    loadedDocument.html = articleHtml
                }
            }
        )

        if (!article.attachments.isNullOrEmpty()) {
            HorizontalDivider()
            Column(Modifier.padding(12.dp)) {
                Text("Attachments", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                article.attachments.forEach { att ->
                    /* att.url is now an absolute, HMAC-signed URL at the KB media endpoint
                     * (the attachment loop in api/v1/kb.php — cited by symbol, the line
                     * moves). It is handed to a SEPARATE browser app, which has neither our
                     * header nor a cookie, so the signature is again the only credential —
                     * and it is why this must stay a plain URL open and never gain an auth
                     * header it could not carry anyway. forAttachment() confines that
                     * capability token to the configured server and still resolves the
                     * unsigned root-relative fallback exactly as before.
                     *
                     * NULL means it refused: the URL carries a token but names an origin
                     * this app was not configured against, so opening it would hand the
                     * capability to a host the server chose. The row stays visible with the
                     * open action disabled, because a greyed-out button the user can ask
                     * about beats a silent request to somewhere else. It cannot happen on an
                     * install whose $config_base_url matches the URL in Server Setup. */
                    val url = KbMediaUrl.forAttachment(att.url, baseUrl)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(att.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { url?.let { openExternalUrl(context, Uri.parse(it)) } },
                            enabled = url != null
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                if (url != null) "Open" else "Not available: this link points at another server"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The document currently showing in the WebView, so an unchanged one is not reloaded. */
private class LoadedDocument {
    var html: String? = null
}

/** Wraps already-rewritten article HTML in the viewport and typography this screen shows it with. */
private fun articleDocument(content: String): String = """
    <html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { font-family: sans-serif; padding: 16px; line-height: 1.5; font-size: 16px; }
        img { max-width: 100%; height: auto; }
        table { width: 100%; border-collapse: collapse; }
        td, th { border: 1px solid #ccc; padding: 4px; }
    </style>
    </head><body>$content</body></html>
""".trimIndent()

/**
 * Opens a URL sourced from KB article HTML/attachments in an external app. Restricted to http(s)
 * only — article content originates from the trusted backend today, but this is defense-in-depth
 * against a stored-HTML/URL injection issue rejecting schemes like `intent://`, `file://`, `javascript:`.
 *
 * It is also the ONE place a URL leaves this app, which is why the capability-token rule is
 * enforced here as well as in KbMediaUrl.forAttachment(): a hyperlinked KB attachment inside the
 * article reaches an external browser through shouldOverrideUrlLoading(), not through the
 * attachment list, and a `?s=` capability handed to a host we were not configured against is a
 * token leaving for somewhere the user never named. Refusing is a toast, not a silent no-op,
 * because a dead tap with no explanation is the worst of the three outcomes.
 */
private fun openExternalUrl(context: Context, uri: Uri?) {
    if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https")) return
    if (KbMediaUrl.wouldLeakCapability(uri.toString(), ApiClient.serverUrl)) {
        android.widget.Toast.makeText(
            context,
            "This link points at a different server, so it was not opened",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app found to open this link", android.widget.Toast.LENGTH_SHORT).show()
    }
}

package com.foleyit.itflow.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a (possibly server-supplied) URL externally; only http/https is allowed. */
fun openWebUrl(context: Context, url: String?) {
    val uri = try { Uri.parse(url?.trim() ?: return) } catch (_: Exception) { return }
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app found to open this link", android.widget.Toast.LENGTH_SHORT).show()
    }
}

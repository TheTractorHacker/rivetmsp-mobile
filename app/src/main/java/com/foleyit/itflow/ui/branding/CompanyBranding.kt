package com.foleyit.itflow.ui.branding

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.foleyit.itflow.R
import kotlinx.coroutines.flow.map
import com.foleyit.itflow.data.local.AppPreferences

/** Brand data is public, scoped to the selected server, and never contains an auth token. */
data class CompanyBranding(
    val name: String = "RivetMSP",
    val logoConfigured: Boolean = false,
    val bitmap: Bitmap? = null,
    val loading: Boolean = false,
)
val LocalCompanyBranding = staticCompositionLocalOf { CompanyBranding() }

@Composable
fun CompanyBrandingProvider(prefs: AppPreferences, content: @Composable () -> Unit) {
    val serverFlow = remember(prefs) { prefs.serverUrl.map<String, String?> { it } }
    val server by serverFlow.collectAsState(initial = null)
    val certificate by prefs.trustedCertSha.collectAsState(initial = null)
    val context = LocalContext.current.applicationContext
    // Reset synchronously on server/certificate changes; never flash the previous tenant's logo.
    var branding by remember(server, certificate) { mutableStateOf(CompanyBranding(loading = server == null || !server.isNullOrBlank())) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeCount++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(server, certificate, resumeCount) {
        val selectedServer = server ?: return@LaunchedEffect
        if (selectedServer.isBlank()) return@LaunchedEffect
        CompanyBrandingRepository(context).load(selectedServer, certificate) { branding = it }
    }
    CompositionLocalProvider(LocalCompanyBranding provides branding, content = content)
}

/** Uploaded logo wins. A failed company image shows the company name, never a competing app logo. */
@Composable
fun CompanyLogo(modifier: Modifier = Modifier) {
    val brand = LocalCompanyBranding.current
    if (brand.loading) {
        Box(modifier) // Avoid flashing product branding while cached company identity loads.
    } else if (brand.bitmap != null) {
        Image(brand.bitmap.asImageBitmap(), brand.name, modifier, contentScale = ContentScale.Fit)
    } else if (brand.logoConfigured) {
        Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(brand.name, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    } else {
        Image(painterResource(R.drawable.rivetmsp_logo), "RivetMSP",
            modifier.background(Color.White).padding(4.dp), contentScale = ContentScale.Fit)
    }
}

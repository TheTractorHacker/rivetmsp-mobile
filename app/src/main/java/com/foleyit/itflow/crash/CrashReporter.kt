package com.foleyit.itflow.crash

import android.content.Context
import android.os.Build
import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.CrashReportRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Captures fatal crashes locally and reports them to the server on the NEXT clean
 * launch instead of from inside the crash handler itself - the process may be dying,
 * so a network call (or anything that can block/throw) at that point is unreliable
 * and must never be what determines whether the real crash gets to propagate.
 *
 * Reachable even when the user isn't logged in / has no valid token, since the crash
 * most worth seeing is often one that happens before login succeeds - see the
 * semi-public POST /api/v1/crash-reports carve-out server-side.
 *
 * SECURITY NOTE: the upload is intentionally unauthenticated when no token exists; the
 * server MUST rate-limit and size-cap this endpoint and treat the body as untrusted text.
 * A bearer token is attached automatically when the user is signed in.
 */
object CrashReporter {
    private const val CRASH_FILE = "pending_crash.txt"
    private const val META_LINES = 7
    private const val TRIMMED_TRACE_CHARS = 600

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Call once, as the very first thing in Application.onCreate(). */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(appContext.filesDir, CRASH_FILE).writeText(buildReport(appContext, thread, throwable))
            }
            // Always defer to the previous/system handler so the crash still surfaces
            // normally (system "keeps stopping" dialog, Play Vitals, etc.) - this must
            // never swallow or alter the actual crash.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val pkgInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        return buildString {
            appendLine(pkgInfo?.versionName ?: "unknown")
            appendLine((pkgInfo?.longVersionCode ?: 0L).toString())
            appendLine(Build.MODEL ?: "unknown")
            appendLine(Build.VERSION.RELEASE ?: "unknown")
            appendLine(Build.VERSION.SDK_INT.toString())
            appendLine(thread.name)
            appendLine(isoFormat.format(Date()))
            append(sw.toString())
        }
    }

    /**
     * Call once on every clean launch (e.g. from Application.onCreate(), after
     * ApiClient.init()). Best-effort: if sending fails (offline, server not yet
     * configured, etc.) the file is left in place and retried on the next launch.
     * Deleted only once the server confirms it was logged.
     */
    fun sendPendingCrashIfAny(context: Context) {
        val file = File(context.applicationContext.filesDir, CRASH_FILE)
        if (!file.exists()) return

        CoroutineScope(Dispatchers.IO).launch {
            val sent = runCatching {
                val lines = file.readText().split("\n")
                if (lines.size <= META_LINES) return@runCatching true // malformed: nothing worth retrying
                fun request(stackTrace: String) = CrashReportRequest(
                    appVersionName = lines[0],
                    appVersionCode = lines[1].toIntOrNull() ?: 0,
                    deviceModel = lines[2],
                    osVersion = lines[3],
                    sdkInt = lines[4].toIntOrNull() ?: 0,
                    threadName = lines[5],
                    occurredAt = lines[6],
                    stackTrace = stackTrace
                )
                val stackTrace = lines.drop(META_LINES).joinToString("\n")
                try {
                    ApiClient.service().reportCrash(request(stackTrace))
                } catch (e: HttpException) {
                    // A server-side failure (e.g. a log column narrower than the report) would
                    // otherwise make every launch re-send the same oversized report forever.
                    // Retry once with just the top of the trace; whatever the outcome, stop.
                    if (e.code() < 500) throw e
                    runCatching { ApiClient.service().reportCrash(request(stackTrace.take(TRIMMED_TRACE_CHARS))) }
                }
                true
            }.getOrElse { e ->
                // Keep the file for the next launch only when the failure was transient
                // (connectivity) or the server isn't configured yet; an HTTP rejection is final.
                e is HttpException
            }

            if (sent) {
                runCatching { file.delete() }
            }
        }
    }
}

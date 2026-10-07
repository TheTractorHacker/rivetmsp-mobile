package com.foleyit.itflow.ui.util

/**
 * Display helpers for user-entered names. The backend permits blank names (and, through Gson,
 * a null can slip into a non-null Kotlin property), so UI code must never assume a name has a
 * first character.
 */

/** Single-letter avatar initial for [name], or "?" when it is null/blank. */
fun initialOf(name: String?): String =
    name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** [name] when it has visible text, otherwise [fallback] (e.g. "Unnamed client"). */
fun nameOrFallback(name: String?, fallback: String): String =
    if (name.isNullOrBlank()) fallback else name

package com.wingmanc2.core.common

import java.util.Locale

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600L
private const val MS_PER_SECOND = 1_000L

fun formatClock(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", total / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
}

fun formatTPlus(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / MS_PER_SECOND
    return String.format(Locale.ROOT, "T+%02d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

fun formatAge(elapsedMs: Long): String {
    val seconds = elapsedMs.coerceAtLeast(0L) / MS_PER_SECOND
    return when {
        seconds < SECONDS_PER_MINUTE -> String.format(Locale.ROOT, "%d s", seconds)
        seconds < SECONDS_PER_HOUR -> String.format(Locale.ROOT, "%d min", seconds / SECONDS_PER_MINUTE)
        else -> String.format(Locale.ROOT, "%d h", seconds / SECONDS_PER_HOUR)
    }
}

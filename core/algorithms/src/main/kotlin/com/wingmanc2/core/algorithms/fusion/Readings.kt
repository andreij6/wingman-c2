package com.wingmanc2.core.algorithms.fusion

import kotlin.math.abs
import kotlin.math.max

internal fun Map<String, String>.field(vararg aliases: String): String? {
    for (alias in aliases) {
        val value = this[alias] ?: continue
        if (value.isNotBlank() && !value.equals("null", ignoreCase = true) && value != "?") return value
    }
    return null
}

internal fun leadingNumber(raw: String): Double? {
    var start = 0
    while (start < raw.length && !raw[start].isDigit() && raw[start] != '-' && raw[start] != '+' && raw[start] != '.') start++
    val digits = StringBuilder(raw.length - start)
    var end = start
    if (end < raw.length && (raw[end] == '-' || raw[end] == '+')) digits.append(raw[end++])
    while (end < raw.length) {
        val c = raw[end]
        when {
            c.isDigit() || c == '.' -> digits.append(c)
            (c == ',' || c == '_') && raw[end - 1].isDigit() && end + 1 < raw.length && raw[end + 1].isDigit() -> Unit
            else -> break
        }
        end++
    }
    return digits.toString().toDoubleOrNull()
}

internal fun coordinate(raw: String): Double? {
    var degrees = 0.0
    var scale = 1.0
    var components = 0
    var negative = false
    var i = 0
    while (i < raw.length && components < 3) {
        val c = raw[i]
        when {
            c == '-' && components == 0 -> negative = true
            c.isDigit() || c == '.' -> {
                val start = i
                while (i < raw.length && (raw[i].isDigit() || raw[i] == '.')) i++
                degrees += (raw.substring(start, i).toDoubleOrNull() ?: return null) / scale
                scale *= 60.0
                components++
                continue
            }
            isSouthOrWest(c) -> negative = true
        }
        i++
    }
    if (components == 0) return null
    while (i < raw.length) {
        val c = raw[i++]
        if (isSouthOrWest(c)) negative = true
    }
    return if (negative) -degrees else degrees
}

internal fun frequencyGhz(raw: String): Double? {
    val value = leadingNumber(raw) ?: return bandCenterGhz(raw.trim().uppercase())
    val unit = raw.lowercase()
    return when {
        "mhz" in unit || (unit.endsWith("m") && value > 100) -> value / 1_000.0
        "khz" in unit -> value / 1_000_000.0
        value > 1_000 -> value / 1_000.0
        else -> value
    }
}

internal fun bandLetter(ghz: Double): String = when {
    ghz < 1.0 -> "UHF"
    ghz < 2.0 -> "L"
    ghz < 4.0 -> "S"
    ghz < 8.0 -> "C"
    ghz < 12.0 -> "X"
    ghz < 18.0 -> "Ku"
    else -> "Ka"
}

internal fun sameEmitterBand(a: Double, b: Double): Boolean =
    a.isNaN() || b.isNaN() || (abs(a - b) <= SAME_FREQUENCY_TOLERANCE * max(a, b) && bandLetter(a) == bandLetter(b))

private const val SAME_FREQUENCY_TOLERANCE = 0.1

private fun bandCenterGhz(label: String): Double? = when (label.removeSuffix("-BAND").removeSuffix(" BAND")) {
    "L" -> 1.5
    "S" -> 3.0
    "C" -> 6.0
    "X" -> 10.0
    "KU" -> 15.0
    else -> null
}

private fun isSouthOrWest(c: Char) = c == 'S' || c == 'W'

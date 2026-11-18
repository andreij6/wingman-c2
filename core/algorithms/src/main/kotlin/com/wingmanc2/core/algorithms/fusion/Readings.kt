package com.wingmanc2.core.algorithms.fusion

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
    var end = start
    if (end < raw.length && (raw[end] == '-' || raw[end] == '+')) end++
    while (end < raw.length && (raw[end].isDigit() || raw[end] == '.')) end++
    return raw.substring(start, end).toDoubleOrNull()
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

private fun isSouthOrWest(c: Char) = c == 'S' || c == 'W'

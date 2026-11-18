package com.wingmanc2.core.algorithms.nlp

internal data class Quantity(val value: Double, val unit: String) {

    val meters: Double?
        get() = when (unit) {
            "", "m", "meter", "meters", "metre", "metres" -> value
            "km", "kilometer", "kilometers", "kilometre", "kilometres", "klick", "klicks" -> value * 1_000.0
            "ft", "foot", "feet" -> value * 0.3048
            "nm", "nmi" -> value * 1_852.0
            else -> null
        }

    val minutes: Double?
        get() = when (unit) {
            "", "m", "min", "mins", "minute", "minutes" -> value
            "h", "hr", "hrs", "hour", "hours" -> value * 60.0
            "s", "sec", "secs", "second", "seconds" -> value / 60.0
            "d", "day", "days" -> value * 1_440.0
            else -> null
        }

    val percent: Double?
        get() = when (unit) {
            Lexer.PERCENT, "percent", "pct" -> value
            "" -> value.takeIf { it in 0.0..100.0 }
            else -> null
        }

    val isUnitless: Boolean get() = unit.isEmpty()

    companion object {
        fun of(token: String): Quantity? {
            var end = 0
            while (end < token.length && (token[end].isDigit() || token[end] == '.')) end++
            if (end == 0) return null
            val value = token.substring(0, end).toDoubleOrNull() ?: return null
            return Quantity(value, token.substring(end))
        }

        fun isUnitWord(token: String): Boolean {
            val probe = Quantity(1.0, token)
            return token.isNotEmpty() && (probe.meters != null || probe.minutes != null || probe.percent != null)
        }
    }
}

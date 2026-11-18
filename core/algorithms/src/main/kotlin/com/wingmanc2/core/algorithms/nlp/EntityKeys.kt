package com.wingmanc2.core.algorithms.nlp

object EntityKeys {

    fun normalize(raw: String): String {
        val out = StringBuilder(raw.length + 1)
        var separated = false
        for (c in raw.trim()) {
            when {
                c.isLetterOrDigit() -> out.append(c.uppercaseChar())
                out.isNotEmpty() && out[out.length - 1] != '-' -> {
                    out.append('-')
                    separated = true
                }
            }
        }
        while (out.isNotEmpty() && out[out.length - 1] == '-') out.setLength(out.length - 1)
        if (!separated) {
            val boundary = out.indexOfFirst(Char::isDigit)
            if (boundary > 0) out.insert(boundary, '-')
        }
        return out.toString()
    }

    fun join(prefix: String, suffix: String): String = normalize("$prefix-$suffix")

    fun looksLikeKey(token: String): Boolean {
        if (token.isEmpty() || !token[0].isLetter() || token.none(Char::isDigit)) return false
        val dash = token.indexOf('-')
        if (dash > 0) return true
        val firstDigit = token.indexOfFirst(Char::isDigit)
        return firstDigit >= MIN_FUSED_PREFIX && token.substring(0, firstDigit).all(Char::isLetter)
    }

    internal fun isKeySuffix(token: String): Boolean =
        token.length <= MAX_SUFFIX && token.any(Char::isDigit) && token.all(Char::isLetterOrDigit) && !isMeasurement(token)

    private fun isMeasurement(token: String): Boolean {
        val quantity = Quantity.of(token) ?: return false
        return !quantity.isUnitless && Quantity.isUnitWord(quantity.unit)
    }

    private const val MIN_FUSED_PREFIX = 3
    private const val MAX_SUFFIX = 5
}

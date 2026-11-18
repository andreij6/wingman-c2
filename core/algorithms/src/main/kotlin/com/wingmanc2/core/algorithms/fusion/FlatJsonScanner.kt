package com.wingmanc2.core.algorithms.fusion

internal object FlatJsonScanner {

    fun scan(payload: CharSequence): Map<String, String> {
        val fields = HashMap<String, String>(INITIAL_FIELDS)
        val buffer = StringBuilder(32)
        val n = payload.length
        var i = 0
        while (i < n) {
            val c = payload[i]
            if (!isQuote(c) && !isIdentifierStart(c)) {
                i++
                continue
            }
            i = readKey(payload, i, buffer)
            val key = buffer.toString().lowercase()
            i = skipWhitespace(payload, i)
            if (i >= n || (payload[i] != ':' && payload[i] != '=')) continue
            i = skipWhitespace(payload, i + 1)
            if (i >= n) break
            when (payload[i]) {
                '{' -> i++
                '[' -> {
                    val end = skipBalanced(payload, i)
                    fields[key] = payload.substring(i, end)
                    i = end
                }
                else -> {
                    i = readValue(payload, i, buffer)
                    fields[key] = buffer.toString().trim()
                }
            }
        }
        return fields
    }

    private fun readKey(s: CharSequence, start: Int, out: StringBuilder): Int {
        if (isQuote(s[start])) return readQuoted(s, start, out)
        out.setLength(0)
        var i = start
        while (i < s.length && isIdentifierPart(s[i])) out.append(s[i++])
        return i
    }

    private fun readValue(s: CharSequence, start: Int, out: StringBuilder): Int {
        if (isQuote(s[start])) return readQuoted(s, start, out)
        out.setLength(0)
        var i = start
        while (i < s.length && s[i] !in VALUE_TERMINATORS) out.append(s[i++])
        return i
    }

    private fun readQuoted(s: CharSequence, start: Int, out: StringBuilder): Int {
        out.setLength(0)
        val quote = s[start]
        var i = start + 1
        while (i < s.length) {
            val c = s[i++]
            when {
                c == quote -> return i
                c == '\\' && i < s.length -> i = unescape(s, i, out)
                else -> out.append(c)
            }
        }
        return i
    }

    private fun unescape(s: CharSequence, at: Int, out: StringBuilder): Int {
        when (val e = s[at]) {
            'n' -> out.append('\n')
            't' -> out.append('\t')
            'r' -> out.append('\r')
            'u' -> {
                val code = if (at + 4 < s.length) s.subSequence(at + 1, at + 5).toString().toIntOrNull(16) else null
                if (code != null) {
                    out.append(code.toChar())
                    return at + 5
                }
                out.append(e)
            }
            else -> out.append(e)
        }
        return at + 1
    }

    private fun skipBalanced(s: CharSequence, start: Int): Int {
        var depth = 0
        var i = start
        var quote = NO_QUOTE
        while (i < s.length) {
            val c = s[i++]
            when {
                quote != NO_QUOTE -> if (c == '\\') i++ else if (c == quote) quote = NO_QUOTE
                isQuote(c) -> quote = c
                c == '[' || c == '{' -> depth++
                c == ']' || c == '}' -> if (--depth == 0) return i
            }
        }
        return s.length
    }

    private fun skipWhitespace(s: CharSequence, start: Int): Int {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun isQuote(c: Char) = c == '"' || c == '\''

    private fun isIdentifierStart(c: Char) = c.isLetter() || c == '_'

    private fun isIdentifierPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'

    private const val NO_QUOTE = '\u0000'
    private const val INITIAL_FIELDS = 16
    private val VALUE_TERMINATORS = charArrayOf(',', '}', ']', '\n', '"', ';')
}

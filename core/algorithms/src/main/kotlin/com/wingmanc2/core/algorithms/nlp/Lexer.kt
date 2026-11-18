package com.wingmanc2.core.algorithms.nlp

internal object Lexer {
    const val LESS_THAN = "<"
    const val PERCENT = "%"

    fun tokenize(text: CharSequence): List<String> {
        val tokens = ArrayList<String>(text.length / 5 + 1)
        val word = StringBuilder(16)
        val n = text.length
        for (i in 0 until n) {
            val c = text[i]
            when {
                c.isLetterOrDigit() -> word.append(c.lowercaseChar())
                word.isNotEmpty() && i + 1 < n && joins(c, word[word.length - 1], text[i + 1]) -> word.append(if (c == '_') '-' else c)
                else -> {
                    tokens.flush(word)
                    when (c) {
                        '<', '≤' -> tokens += LESS_THAN
                        '%' -> tokens += PERCENT
                    }
                }
            }
        }
        tokens.flush(word)
        return tokens
    }

    private fun joins(c: Char, previous: Char, next: Char): Boolean = when (c) {
        '-', '_' -> next.isLetterOrDigit()
        '.' -> previous.isDigit() && next.isDigit()
        else -> false
    }

    private fun MutableList<String>.flush(word: StringBuilder) {
        if (word.isEmpty()) return
        add(word.toString())
        word.setLength(0)
    }
}

package com.wingmanc2.core.algorithms.nlp

internal class TokenStream(private val tokens: List<String>) {
    private var position = 0

    val hasNext: Boolean get() = position < tokens.size

    fun peek(offset: Int = 0): String? = tokens.getOrNull(position + offset)

    fun next(): String = tokens[position++]

    fun skip(word: String): Boolean {
        if (peek() != word) return false
        position++
        return true
    }

    fun quantity(): Quantity? {
        val quantity = peek()?.let(Quantity::of) ?: return null
        position++
        if (!quantity.isUnitless) return quantity
        val unit = peek() ?: return quantity
        if (!Quantity.isUnitWord(unit)) return quantity
        position++
        return quantity.copy(unit = unit)
    }

    fun anchor(): String? {
        while (peek()?.let(ANCHOR_FILLERS::contains) == true) position++
        val head = peek() ?: return null
        position++
        if (EntityKeys.looksLikeKey(head)) return EntityKeys.normalize(head)
        val tail = peek()
        if (tail != null && head.all(Char::isLetter) && EntityKeys.isKeySuffix(tail)) {
            position++
            return EntityKeys.join(head, tail)
        }
        return head.uppercase()
    }

    private companion object {
        val ANCHOR_FILLERS = setOf("the", "of", "from", "to", "a")
    }
}

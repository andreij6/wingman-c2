package com.wingmanc2.core.database.embedding

import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

const val EMBEDDING_DIMENSIONS: Int = 64
internal const val EMBEDDING_DIMENSIONS_LONG: Long = EMBEDDING_DIMENSIONS.toLong()

@Singleton
class SemanticEmbedder @Inject constructor() {

    fun embed(text: CharSequence): FloatArray = FloatArray(EMBEDDING_DIMENSIONS).apply {
        accumulate(text, TEXT_WEIGHT)
        normalize()
    }

    fun embed(entity: OntologyEntity): FloatArray = FloatArray(EMBEDDING_DIMENSIONS).apply {
        accumulate(entity.kind.name, CATEGORY_WEIGHT)
        accumulate(entity.affiliation.name, CATEGORY_WEIGHT)
        accumulate(entity.key, KEY_WEIGHT)
        accumulate(entity.label, LABEL_WEIGHT)
        accumulate(entity.subtitle, TEXT_WEIGHT)
        for ((name, value) in entity.attributes) {
            accumulate(name, ATTRIBUTE_NAME_WEIGHT)
            accumulate(value, TEXT_WEIGHT)
        }
        normalize()
    }

    private fun FloatArray.accumulate(text: CharSequence, weight: Float) {
        var start = 0
        val length = text.length
        while (start < length) {
            while (start < length && !text[start].isLetterOrDigit()) start++
            var end = start
            while (end < length && text[end].isLetterOrDigit()) end++
            if (end > start) addToken(text, start, end, weight)
            start = end
        }
    }

    private fun FloatArray.addToken(text: CharSequence, start: Int, end: Int, weight: Float) {
        add(hash(text, start, end), weight)
        if (end - start <= GRAM) return
        val gramWeight = weight * GRAM_WEIGHT
        for (offset in start..end - GRAM) add(hash(text, offset, offset + GRAM), gramWeight)
    }

    private fun FloatArray.add(hash: Int, weight: Float) {
        val bucket = hash and (EMBEDDING_DIMENSIONS - 1)
        this[bucket] += if (hash < 0) -weight else weight
    }

    private fun FloatArray.normalize() {
        var sum = 0.0
        for (component in this) sum += component * component
        if (sum == 0.0) return
        val inverse = (1.0 / sqrt(sum)).toFloat()
        for (index in indices) this[index] *= inverse
    }

    private fun hash(text: CharSequence, start: Int, end: Int): Int {
        var h = FNV_OFFSET
        for (index in start until end) {
            h = h xor text[index].lowercaseChar().code
            h *= FNV_PRIME
        }
        h = h xor (h ushr 16)
        h *= MIX_1
        h = h xor (h ushr 13)
        h *= MIX_2
        return h xor (h ushr 16)
    }

    private companion object {
        const val GRAM = 3
        const val GRAM_WEIGHT = 0.5f
        const val TEXT_WEIGHT = 1f
        const val LABEL_WEIGHT = 1.5f
        const val KEY_WEIGHT = 2f
        const val CATEGORY_WEIGHT = 1.25f
        const val ATTRIBUTE_NAME_WEIGHT = 0.75f
        const val FNV_OFFSET = -0x7ee3623b
        const val FNV_PRIME = 0x01000193
        const val MIX_1 = -0x7a143595
        const val MIX_2 = -0x3d4d51cb
    }
}

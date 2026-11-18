package com.wingmanc2.core.algorithms.embedding

import com.wingmanc2.core.algorithms.random.Mix64
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject
import kotlin.math.sqrt

const val EMBEDDING_DIM = 64

class FeatureHasher @Inject constructor() {

    fun embed(entity: OntologyEntity): FloatArray = embed(entity.semanticText())

    fun embed(text: CharSequence): FloatArray {
        val out = FloatArray(EMBEDDING_DIM)
        var hash = FNV_OFFSET
        var stemmed = FNV_OFFSET
        var length = 0
        var last = ' '
        var previous = 0L
        var hasPrevious = false
        for (i in 0..text.length) {
            val c = if (i < text.length) text[i].lowercaseChar() else ' '
            if (c.isLetterOrDigit()) {
                stemmed = hash
                hash = (hash xor c.code.toLong()) * FNV_PRIME
                last = c
                length++
                continue
            }
            if (length == 0) continue
            val token = if (length > MIN_STEM_LENGTH && last == 's') stemmed else hash
            accumulate(out, token, UNIGRAM_WEIGHT)
            if (hasPrevious) accumulate(out, previous * FNV_PRIME xor token, BIGRAM_WEIGHT)
            previous = token
            hasPrevious = true
            hash = FNV_OFFSET
            length = 0
        }
        return out.normalized()
    }

    private fun OntologyEntity.semanticText(): String = buildString(SEMANTIC_TEXT_CAPACITY) {
        append(kind.name).append(' ').append(affiliation.name).append(' ')
        append(key).append(' ').append(label).append(' ').append(subtitle)
        for ((name, value) in attributes) append(' ').append(name).append(' ').append(value)
    }

    private fun accumulate(out: FloatArray, feature: Long, weight: Float) {
        val h = Mix64.fmix(feature)
        val bucket = (h and DIM_MASK).toInt()
        out[bucket] += if (h < 0) -weight else weight
    }

    private fun FloatArray.normalized(): FloatArray {
        var sumSq = 0f
        for (v in this) sumSq += v * v
        if (sumSq == 0f) return this
        val inverse = 1f / sqrt(sumSq)
        for (i in indices) this[i] *= inverse
        return this
    }

    private companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
        const val DIM_MASK = (EMBEDDING_DIM - 1).toLong()
        const val MIN_STEM_LENGTH = 3
        const val UNIGRAM_WEIGHT = 1f
        const val BIGRAM_WEIGHT = 0.6f
        const val SEMANTIC_TEXT_CAPACITY = 160
    }
}

package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.index.LongIntHashMap
import com.wingmanc2.core.model.EntityKind

internal class CandidateTable(expectedSize: Int) {
    private val rowById = LongIntHashMap(expectedSize)
    private var kinds = ByteArray(expectedSize.coerceAtLeast(1))
    private var frequenciesGhz = DoubleArray(kinds.size)
    private var vectors = FloatArray(kinds.size * EMBEDDING_DIM)

    private val size: Int get() = rowById.size

    fun upsert(id: Long, kind: EntityKind, frequencyGhz: Double, unitEmbedding: FloatArray) {
        var row = rowById[id]
        if (row == LongIntHashMap.ABSENT) {
            row = size
            if (row == kinds.size) grow()
            rowById[id] = row
        }
        kinds[row] = kind.ordinal.toByte()
        frequenciesGhz[row] = frequencyGhz
        unitEmbedding.copyInto(vectors, row * EMBEDDING_DIM)
    }

    fun rowOf(id: Long): Int = rowById[id]

    fun kindAt(row: Int): EntityKind = KINDS[kinds[row].toInt()]

    fun frequencyGhzAt(row: Int): Double = frequenciesGhz[row]

    fun similarityAt(row: Int, unitQuery: FloatArray): Float {
        val base = row * EMBEDDING_DIM
        var dot = 0f
        for (d in 0 until EMBEDDING_DIM) dot += vectors[base + d] * unitQuery[d]
        return dot
    }

    private fun grow() {
        val capacity = kinds.size shl 1
        kinds = kinds.copyOf(capacity)
        frequenciesGhz = frequenciesGhz.copyOf(capacity)
        vectors = vectors.copyOf(capacity * EMBEDDING_DIM)
    }

    companion object {
        const val ABSENT = LongIntHashMap.ABSENT
        private val KINDS = EntityKind.entries
    }
}

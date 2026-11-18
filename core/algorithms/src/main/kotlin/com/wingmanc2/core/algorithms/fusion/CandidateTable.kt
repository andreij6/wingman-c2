package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.index.LongIntHashMap
import com.wingmanc2.core.model.EntityKind
import kotlin.math.sqrt

internal class CandidateTable(expectedSize: Int) {
    private val rowById = LongIntHashMap(expectedSize)
    private var ids = LongArray(expectedSize.coerceAtLeast(1))
    private var kinds = ByteArray(ids.size)
    private var vectors = FloatArray(ids.size * EMBEDDING_DIM)

    val size: Int get() = rowById.size

    fun upsert(id: Long, kind: EntityKind, embedding: FloatArray) {
        var row = rowById[id]
        if (row == LongIntHashMap.ABSENT) {
            row = size
            if (row == ids.size) grow()
            ids[row] = id
            rowById[id] = row
        }
        kinds[row] = kind.ordinal.toByte()
        writeNormalized(row, embedding)
    }

    fun remove(id: Long): Boolean {
        val row = rowById.remove(id)
        if (row == LongIntHashMap.ABSENT) return false
        val last = size
        if (row != last) {
            ids[row] = ids[last]
            kinds[row] = kinds[last]
            vectors.copyInto(vectors, row * EMBEDDING_DIM, last * EMBEDDING_DIM, (last + 1) * EMBEDDING_DIM)
            rowById[ids[row]] = row
        }
        return true
    }

    fun kindOf(id: Long): EntityKind? {
        val row = rowById[id]
        return if (row == LongIntHashMap.ABSENT) null else KINDS[kinds[row].toInt()]
    }

    fun similarity(id: Long, unitQuery: FloatArray): Float {
        val row = rowById[id]
        if (row == LongIntHashMap.ABSENT) return 0f
        val base = row * EMBEDDING_DIM
        var dot = 0f
        for (d in 0 until EMBEDDING_DIM) dot += vectors[base + d] * unitQuery[d]
        return dot
    }

    private fun writeNormalized(row: Int, embedding: FloatArray) {
        require(embedding.size == EMBEDDING_DIM) { "Embedding must have $EMBEDDING_DIM dimensions" }
        var sumSq = 0f
        for (v in embedding) sumSq += v * v
        val inverse = if (sumSq == 0f) 0f else 1f / sqrt(sumSq)
        val base = row * EMBEDDING_DIM
        for (d in 0 until EMBEDDING_DIM) vectors[base + d] = embedding[d] * inverse
    }

    private fun grow() {
        val capacity = ids.size shl 1
        ids = ids.copyOf(capacity)
        kinds = kinds.copyOf(capacity)
        vectors = vectors.copyOf(capacity * EMBEDDING_DIM)
    }

    private companion object {
        val KINDS = EntityKind.entries
    }
}

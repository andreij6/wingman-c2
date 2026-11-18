package com.wingmanc2.core.database.record

import com.wingmanc2.core.database.embedding.EMBEDDING_DIMENSIONS_LONG
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.IndexType
import io.objectbox.annotation.Unique
import io.objectbox.annotation.VectorDistanceType
import io.objectbox.converter.StringMapConverter

@Entity
internal class OntologyEntityRecord(
    @Id(assignable = true) var id: Long = 0,
    @Unique @Index(type = IndexType.HASH) var key: String = "",
    @Index var kind: Int = 0,
    @Index var affiliation: Int = 0,
    var label: String = "",
    var subtitle: String = "",
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var confidence: Float = 0f,
    var verified: Boolean = false,
    @Convert(converter = StringMapConverter::class, dbType = ByteArray::class)
    var attributes: Map<String, String>? = null,
    var footprint: DoubleArray? = null,
    var threatRadiusM: Double = 0.0,
    var headingDeg: Double? = null,
    var version: Long = 0,
    @Index var updatedAtMs: Long = 0,
    @Index var syncState: Int = 0,
)

@Entity
internal class OntologyEmbeddingRecord(
    @Id(assignable = true) var id: Long = 0,
    @HnswIndex(dimensions = EMBEDDING_DIMENSIONS_LONG, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,
)

@Entity
internal class OntologyEdgeRecord(
    @Id(assignable = true) var id: Long = 0,
    @Index var sourceId: Long = 0,
    @Index var targetId: Long = 0,
    var relation: Int = 0,
    var weight: Float = 0f,
    var label: String = "",
    var provenance: String = "",
)

@Entity
internal class RawObservationRecord(
    @Id(assignable = true) var id: Long = 0,
    @Index var entityId: Long = 0,
    var format: Int = 0,
    var payload: String = "",
    var note: String = "",
    var ingestedAtMs: Long = 0,
)

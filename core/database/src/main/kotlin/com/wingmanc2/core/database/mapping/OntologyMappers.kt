package com.wingmanc2.core.database.mapping

import com.wingmanc2.core.database.record.OntologyEdgeRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord
import com.wingmanc2.core.database.record.RawObservationRecord
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawObservation

internal fun OntologyEntityRecord.toModel(): OntologyEntity = OntologyEntity(
    id = id,
    key = key,
    kind = Codes.kind.decode(kind),
    affiliation = Codes.affiliation.decode(affiliation),
    label = label,
    subtitle = subtitle,
    position = GeoPoint(lat, lon),
    confidence = confidence,
    verified = verified,
    attributes = attributes.orEmpty(),
    footprint = footprint.toGeoPoints(),
    threatRadiusM = threatRadiusM,
    headingDeg = headingDeg,
    version = version,
    updatedAtMs = updatedAtMs,
    syncState = Codes.syncState.decode(syncState),
)

internal fun OntologyEntity.toRecord(id: Long, embedding: FloatArray): OntologyEntityRecord = OntologyEntityRecord(
    id = id,
    key = key,
    kind = Codes.kind.encode(kind),
    affiliation = Codes.affiliation.encode(affiliation),
    label = label,
    subtitle = subtitle,
    lat = position.lat,
    lon = position.lon,
    confidence = confidence,
    verified = verified,
    attributes = attributes.ifEmpty { null },
    footprint = footprint.toInterleaved(),
    threatRadiusM = threatRadiusM,
    headingDeg = headingDeg,
    version = version,
    updatedAtMs = updatedAtMs,
    syncState = Codes.syncState.encode(syncState),
    embedding = embedding,
)

internal fun OntologyEdgeRecord.toModel(): OntologyEdge = OntologyEdge(
    id = id,
    sourceId = sourceId,
    targetId = targetId,
    relation = Codes.relation.decode(relation),
    weight = weight,
    label = label,
    provenance = provenance,
)

internal fun OntologyEdge.toRecord(): OntologyEdgeRecord = OntologyEdgeRecord(
    id = id,
    sourceId = sourceId,
    targetId = targetId,
    relation = Codes.relation.encode(relation),
    weight = weight,
    label = label,
    provenance = provenance,
)

internal fun RawObservationRecord.toModel(): RawObservation = RawObservation(
    id = id,
    entityId = entityId,
    format = Codes.sourceFormat.decode(format),
    payload = payload,
    note = note,
    ingestedAtMs = ingestedAtMs,
)

internal fun RawObservation.toRecord(): RawObservationRecord = RawObservationRecord(
    id = id,
    entityId = entityId,
    format = Codes.sourceFormat.encode(format),
    payload = payload,
    note = note,
    ingestedAtMs = ingestedAtMs,
)

private fun List<GeoPoint>.toInterleaved(): DoubleArray? {
    if (isEmpty()) return null
    val packed = DoubleArray(size * 2)
    forEachIndexed { index, point ->
        packed[index * 2] = point.lat
        packed[index * 2 + 1] = point.lon
    }
    return packed
}

private fun DoubleArray?.toGeoPoints(): List<GeoPoint> =
    if (this == null) emptyList() else List(size / 2) { GeoPoint(this[it * 2], this[it * 2 + 1]) }

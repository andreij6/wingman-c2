package com.wingmanc2.core.model

enum class Affiliation { FRIEND, HOSTILE, UNKNOWN, NEUTRAL }

enum class EntityKind { TRACK, EMITTER, ZONE, ASSET, REPORT, SENSOR, CLASS }

enum class SyncState { SYNCED, PENDING, CONFLICT }

enum class RelationType { DETECTED_IN, NEAR, CORROBORATES, TASKED_TO, INFERRED }

enum class SourceFormat { RF_PING, SPOT_TEXT, AIS_JSON, ADSB_JSON }

data class OntologyEntity(
    val id: Long,
    val key: String,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val label: String,
    val subtitle: String,
    val position: GeoPoint,
    val confidence: Float,
    val verified: Boolean,
    val attributes: Map<String, String>,
    val footprint: List<GeoPoint>,
    val threatRadiusM: Double,
    val headingDeg: Double?,
    val version: Long,
    val updatedAtMs: Long,
    val syncState: SyncState,
)

object EntityAttributes {
    const val DOMAIN = "Domain"
    const val FREQ = "Freq"
    const val BEARING = "Bearing"
    const val BAND = "Band"
    const val RESTRICTION = "Restriction"
    const val ALT = "Alt"
    const val SPEED = "Speed"
    const val SUBJECT = "Subject"
    const val SOURCE = "Source"
    const val AIR = "Air"
}

val OntologyEntity.isAir: Boolean get() = attributes[EntityAttributes.DOMAIN] == EntityAttributes.AIR

data class OntologyEdge(
    val id: Long,
    val sourceId: Long,
    val targetId: Long,
    val relation: RelationType,
    val weight: Float,
    val label: String,
    val provenance: String,
)

data class RawObservation(
    val id: Long,
    val entityId: Long,
    val format: SourceFormat,
    val payload: String,
    val note: String,
    val ingestedAtMs: Long,
)

data class EntityGraph(
    val focusId: Long,
    val nodes: List<OntologyEntity>,
    val edges: List<OntologyEdge>,
    val totalNodes: Int,
    val totalEdges: Int,
)


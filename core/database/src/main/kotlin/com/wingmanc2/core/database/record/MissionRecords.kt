package com.wingmanc2.core.database.record

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
internal class MissionRecord(
    @Id var id: Long = 0,
    @Index var status: Int = 0,
    var intentText: String = "",
    var intentTargetKey: String? = null,
    var constraints: ByteArray? = null,
    @Index var targetId: Long = 0,
    var assetId: Long = 0,
    var coa: ByteArray? = null,
    @Index var startedAtMs: Long = 0,
)

@Entity
internal class AutonomyDecisionRecord(
    @Id var id: Long = 0,
    @Index var missionId: Long = 0,
    var tPlusMs: Long = 0,
    var kind: Int = 0,
    var title: String = "",
    var rationale: String = "",
    var provenance: String = "",
)

@Entity
internal class TelemetryRecord(
    @Id var id: Long = 0,
    @Index var missionId: Long = 0,
    var tMs: Long = 0,
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var headingDeg: Double = 0.0,
    var altitudeAglM: Double = 0.0,
    var airspeedMps: Double = 0.0,
    var climbMps: Double = 0.0,
    var batteryPct: Double = 0.0,
    var linkSnrDb: Double = 0.0,
    var waypointIndex: Int = 0,
    var waypointCount: Int = 0,
)

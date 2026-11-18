package com.wingmanc2.core.database.mapping

import com.wingmanc2.core.database.record.AutonomyDecisionRecord
import com.wingmanc2.core.database.record.MissionRecord
import com.wingmanc2.core.database.record.TelemetryRecord
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.TelemetryFrame

internal fun MissionRecord.toModel(): MissionPlan = MissionPlan(
    id = id,
    intent = CommanderIntent(intentText, intentTargetKey, BinaryCodec.decodeConstraints(constraints)),
    targetId = targetId,
    assetId = assetId,
    coa = BinaryCodec.decodeCoa(checkNotNull(coa) { "Mission $id has no course of action" }),
    status = Codes.missionStatus.decode(status),
    startedAtMs = startedAtMs,
)

internal fun MissionPlan.toRecord(): MissionRecord = MissionRecord(
    id = id,
    status = Codes.missionStatus.encode(status),
    intentText = intent.text,
    intentTargetKey = intent.targetKey,
    constraints = BinaryCodec.encodeConstraints(intent.constraints),
    targetId = targetId,
    assetId = assetId,
    coa = BinaryCodec.encodeCoa(coa),
    startedAtMs = startedAtMs,
)

internal fun AutonomyDecisionRecord.toModel(): AutonomyDecision = AutonomyDecision(
    id = id,
    missionId = missionId,
    tPlusMs = tPlusMs,
    kind = Codes.decisionKind.decode(kind),
    title = title,
    rationale = rationale,
    provenance = provenance,
)

internal fun AutonomyDecision.toRecord(): AutonomyDecisionRecord = AutonomyDecisionRecord(
    id = id,
    missionId = missionId,
    tPlusMs = tPlusMs,
    kind = Codes.decisionKind.encode(kind),
    title = title,
    rationale = rationale,
    provenance = provenance,
)

internal fun TelemetryRecord.toModel(): TelemetryFrame = TelemetryFrame(
    tMs = tMs,
    position = GeoPoint(lat, lon),
    headingDeg = headingDeg,
    altitudeAglM = altitudeAglM,
    airspeedMps = airspeedMps,
    climbMps = climbMps,
    batteryPct = batteryPct,
    linkSnrDb = linkSnrDb,
    waypointIndex = waypointIndex,
    waypointCount = waypointCount,
)

internal fun TelemetryFrame.toRecord(missionId: Long): TelemetryRecord = TelemetryRecord(
    missionId = missionId,
    tMs = tMs,
    lat = position.lat,
    lon = position.lon,
    headingDeg = headingDeg,
    altitudeAglM = altitudeAglM,
    airspeedMps = airspeedMps,
    climbMps = climbMps,
    batteryPct = batteryPct,
    linkSnrDb = linkSnrDb,
    waypointIndex = waypointIndex,
    waypointCount = waypointCount,
)

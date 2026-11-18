package com.wingmanc2.core.simulation.scenario

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.Affiliation.FRIEND
import com.wingmanc2.core.model.Affiliation.HOSTILE
import com.wingmanc2.core.model.Affiliation.NEUTRAL
import com.wingmanc2.core.model.Affiliation.UNKNOWN
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint

internal object SeedEntities {
    private const val ZONE_HALF_SIDE_M = 250.0
    private const val TRK_07_EDGE_VERSION = 4L

    val all: List<SeedEntity> = listOf(
        seed(
            ScenarioKeys.TRK_07, EntityKind.EMITTER, HOSTILE, "S-band emitter · suspect hostile", 900.0, -150.0, 0.84f,
            threatRadiusM = 600.0, version = TRK_07_EDGE_VERSION,
            attributes = linkedMapOf(
                EntityAttributes.FREQ to "3.1 GHz",
                EntityAttributes.BEARING to "142°",
                EntityAttributes.BAND to "S-band",
                "Fused from" to "RF ping · SPOT-19 · AIS (partial)",
            ),
        ),
        seed(
            ScenarioKeys.TRK_11, EntityKind.TRACK, HOSTILE, "Technical vehicle · mobile", 1_350.0, -650.0, 0.71f,
            threatRadiusM = 250.0, headingDeg = 210.0, attributes = linkedMapOf("Speed" to "6 m/s"),
        ),
        seed(
            ScenarioKeys.TRK_14, EntityKind.TRACK, HOSTILE, "Dismount team · 4 pax", 700.0, -900.0, 0.58f,
            threatRadiusM = 150.0, headingDeg = 300.0,
        ),
        seed(ScenarioKeys.SPOT_19, EntityKind.REPORT, UNKNOWN, "Spot report · van w/ dish", 500.0, 350.0, 0.55f),
        seed(
            ScenarioKeys.UNK_22, EntityKind.TRACK, UNKNOWN, "Small UAS · low / slow", 1_200.0, 900.0, 0.42f,
            headingDeg = 250.0, attributes = linkedMapOf(EntityAttributes.DOMAIN to EntityAttributes.AIR),
        ),
        seed(ScenarioKeys.UNK_31, EntityKind.TRACK, UNKNOWN, "Vehicle · stationary", -200.0, -1_300.0, 0.48f),
        seed(
            ScenarioKeys.UNK_35, EntityKind.EMITTER, UNKNOWN, "RF burst · unresolved", 1_700.0, -1_500.0, 0.37f,
            attributes = linkedMapOf(EntityAttributes.FREQ to "433 MHz"),
        ),
        seed(
            ScenarioKeys.UAS_01, EntityKind.ASSET, FRIEND, "Wingman UAS · EO/IR", -1_500.0, -300.0, 1f,
            verified = true, headingDeg = 60.0,
            attributes = linkedMapOf(EntityAttributes.DOMAIN to EntityAttributes.AIR, "Payload" to "EO/IR gimbal", "Endurance" to "48 min"),
        ),
        seed(
            ScenarioKeys.RF_RX2, EntityKind.SENSOR, FRIEND, "RF direction finder", -400.0, 1_500.0, 1f,
            verified = true, attributes = linkedMapOf("Coverage" to "0.4–6 GHz"),
        ),
        seed(ScenarioKeys.BLUE_1, EntityKind.TRACK, FRIEND, "Recon section", -1_750.0, -1_450.0, 1f, verified = true),
        seed(ScenarioKeys.BLUE_2, EntityKind.TRACK, FRIEND, "Mortar section", -1_200.0, 1_100.0, 1f, verified = true),
        seed(ScenarioKeys.BLUE_3, EntityKind.TRACK, FRIEND, "Platoon HQ", -1_800.0, 400.0, 1f, verified = true),
        seed(ScenarioKeys.BLUE_4, EntityKind.TRACK, FRIEND, "Observation post", 300.0, -1_700.0, 1f, verified = true),
        seed(ScenarioKeys.BLUE_5, EntityKind.TRACK, FRIEND, "Logistics node", -900.0, -1_750.0, 1f, verified = true),
        seed(
            ScenarioKeys.NEU_02, EntityKind.TRACK, NEUTRAL, "Civilian vehicle", 1_400.0, 1_700.0, 0.9f,
            verified = true, headingDeg = 180.0,
        ),
        seed(
            ScenarioKeys.ZONE_4B, EntityKind.ZONE, NEUTRAL, "No-fly zone", -700.0, 700.0, 1f,
            verified = true, footprint = square(-700.0, 700.0, ZONE_HALF_SIDE_M),
            attributes = linkedMapOf(EntityAttributes.RESTRICTION to "NFZ · all altitudes"),
        ),
        seed(
            ScenarioKeys.S_BAND, EntityKind.CLASS, NEUTRAL, "Ontology class", 0.0, 0.0, 1f,
            verified = true, attributes = linkedMapOf("Range" to "2–4 GHz"),
        ),
        seed(ScenarioKeys.GROUND_VEH, EntityKind.CLASS, NEUTRAL, "Ontology class", 0.0, 0.0, 1f, verified = true),
    )

    val popupThreat: SeedEntity = seed(
        ScenarioKeys.TRK_23, EntityKind.EMITTER, HOSTILE, "Pop-up RF emitter · S-band", 250.0, -450.0, 0.62f,
        threatRadiusM = 450.0, attributes = linkedMapOf(EntityAttributes.FREQ to "3.3 GHz", EntityAttributes.BAND to "S-band"),
    )

    private fun seed(
        key: String,
        kind: EntityKind,
        affiliation: Affiliation,
        subtitle: String,
        x: Double,
        y: Double,
        confidence: Float,
        verified: Boolean = false,
        threatRadiusM: Double = 0.0,
        headingDeg: Double? = null,
        footprint: List<LocalPoint> = emptyList(),
        attributes: Map<String, String> = emptyMap(),
        version: Long = ScenarioSeed.INITIAL_VERSION,
    ) = SeedEntity(
        key = key,
        kind = kind,
        affiliation = affiliation,
        label = key,
        subtitle = subtitle,
        local = LocalPoint(x, y),
        confidence = confidence,
        verified = verified,
        attributes = attributes,
        footprintLocal = footprint,
        threatRadiusM = threatRadiusM,
        headingDeg = headingDeg,
        version = version,
    )

    private fun square(cx: Double, cy: Double, half: Double) = listOf(
        LocalPoint(cx - half, cy + half),
        LocalPoint(cx + half, cy + half),
        LocalPoint(cx + half, cy - half),
        LocalPoint(cx - half, cy - half),
    )
}

package com.wingmanc2.core.simulation.scenario

import com.wingmanc2.core.model.RelationType
import com.wingmanc2.core.model.RelationType.CORROBORATES
import com.wingmanc2.core.model.RelationType.DETECTED_IN
import com.wingmanc2.core.model.RelationType.INFERRED
import com.wingmanc2.core.model.RelationType.NEAR
import com.wingmanc2.core.model.RelationType.TASKED_TO
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.BLUE_1
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.BLUE_2
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.BLUE_3
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.BLUE_4
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.BLUE_5
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.GROUND_VEH
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.NEU_02
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.RF_RX2
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.SPOT_19
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.S_BAND
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.TRK_07
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.TRK_11
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.TRK_14
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.UAS_01
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.UNK_22
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.UNK_31
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.UNK_35
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.ZONE_4B
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class SeedGraph(private val entityByKey: Map<String, SeedEntity>) {

    val edges: List<SeedEdge> = listOf(
        edge(SPOT_19, TRK_07, CORROBORATES, 0.78f, "Spot report text match · van w/ dish"),
        edge(ZONE_4B, TRK_07, NEAR, 0.64f, "Geo-proximity rule", label = "NEAR 180 m"),
        edge(UAS_01, TRK_07, TASKED_TO, 0.92f, "Operator tasking · recon"),
        edge(S_BAND, TRK_07, INFERRED, 0.81f, "Ontology inference · 3.1 GHz ∈ S-band 2–4 GHz"),
        near(TRK_11, TRK_14),
        near(TRK_11, UNK_35),
        near(UNK_22, NEU_02),
        near(UNK_31, BLUE_4),
        near(BLUE_1, BLUE_5),
        near(BLUE_3, UAS_01),
        near(BLUE_2, ZONE_4B),
        near(RF_RX2, BLUE_2),
        near(SPOT_19, UNK_22),
        near(UNK_31, BLUE_5),
        near(TRK_14, BLUE_4),
        edge(UNK_35, TRK_11, CORROBORATES, 0.41f, "Co-located RF burst"),
        edge(BLUE_4, TRK_14, CORROBORATES, 0.73f, "OP visual report"),
        edge(RF_RX2, UNK_22, CORROBORATES, 0.52f, "Control-link bearing fix"),
        edge(UNK_22, RF_RX2, DETECTED_IN, 0.66f, "RF-RX2 sensor coverage"),
        edge(UNK_35, RF_RX2, DETECTED_IN, 0.58f, "RF-RX2 sensor coverage"),
        edge(NEU_02, RF_RX2, DETECTED_IN, 0.44f, "RF-RX2 sensor coverage"),
        edge(TRK_14, BLUE_4, DETECTED_IN, 0.7f, "OP-4 visual sector"),
        edge(UNK_31, BLUE_4, DETECTED_IN, 0.62f, "OP-4 visual sector"),
        edge(BLUE_2, TRK_11, TASKED_TO, 0.88f, "Fire mission · on call"),
        edge(BLUE_4, UNK_31, TASKED_TO, 0.8f, "Observe & report"),
        edge(BLUE_1, UNK_31, TASKED_TO, 0.76f, "Screen line"),
        edge(GROUND_VEH, TRK_11, INFERRED, 0.69f, "Ontology inference · wheeled signature"),
        edge(GROUND_VEH, UNK_31, INFERRED, 0.47f, "Ontology inference · thermal profile"),
        edge(GROUND_VEH, NEU_02, INFERRED, 0.83f, "Ontology inference · AIS class"),
    )

    val observations: List<SeedObservation> = listOf(
        SeedObservation(TRK_07, SourceFormat.RF_PING, """{"src":"rf_rx2","band":"3.1G","az":42}""", "RF-RX2 bearing fix", 6),
        SeedObservation(TRK_07, SourceFormat.SPOT_TEXT, "\"Saw van w/ dish east of ridge\"", "Free-text spot report · SPOT-19", 4),
        SeedObservation(
            TRK_07,
            SourceFormat.AIS_JSON,
            """{"name":"UNKNOWN","lat":38.70065,"lon":-78.53865,"sog":0.0,"cog":null}""",
            "mmsi, alt_m missing · tolerated",
            2,
        ),
        SeedObservation(SPOT_19, SourceFormat.SPOT_TEXT, "\"White van, roof dish, parked by ridge road\"", "BLUE-4 observer", 4),
        SeedObservation(
            UNK_22,
            SourceFormat.ADSB_JSON,
            """{"icao":"A3F1C2","lat":38.71012,"lon":-78.53509,"alt_ft":null,"gs_kt":31}""",
            "alt_ft null · callsign missing",
            3,
        ),
        SeedObservation(TRK_11, SourceFormat.RF_PING, """{"src":"rf_rx2","band":"UHF","az":151}""", "Push-to-talk burst", 9),
        SeedObservation(UNK_35, SourceFormat.RF_PING, """{"src":"rf_rx2","band":"433M","az":"154T"}""", "Azimuth as string · normalized", 12),
    )

    private fun near(sourceKey: String, targetKey: String): SeedEdge {
        val a = entityByKey.getValue(sourceKey).local
        val b = entityByKey.getValue(targetKey).local
        val meters = (hypot(a.x - b.x, a.y - b.y) / 10.0).roundToInt() * 10
        return edge(sourceKey, targetKey, NEAR, 0.5f, "Geo-proximity rule", label = "NEAR $meters m")
    }

    private fun edge(
        sourceKey: String,
        targetKey: String,
        relation: RelationType,
        weight: Float,
        provenance: String,
        label: String = relation.name,
    ) = SeedEdge(sourceKey, targetKey, relation, weight, label, provenance)
}

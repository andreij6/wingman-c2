package com.wingmanc2.core.database.mapping

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.DecisionKind
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.MissionStatus
import com.wingmanc2.core.model.OutboxOp
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.core.model.RelationType
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.model.SyncState
import kotlin.enums.EnumEntries

internal class EnumCodec<E : Enum<E>>(
    private val entries: EnumEntries<E>,
    private val fallback: E,
    code: (E) -> Int,
) {
    private val codes = IntArray(entries.size) { code(entries[it]) }
    private val ordinalsByCode = IntArray(codes.max() + 1) { -1 }.also { table ->
        codes.forEachIndexed { ordinal, value ->
            require(table[value] == -1) { "Duplicate code $value for ${entries[ordinal]}" }
            table[value] = ordinal
        }
    }

    fun encode(value: E): Int = codes[value.ordinal]

    fun decode(code: Int): E {
        val ordinal = if (code in ordinalsByCode.indices) ordinalsByCode[code] else -1
        return if (ordinal >= 0) entries[ordinal] else fallback
    }
}

internal object Codes {
    val kind = EnumCodec(EntityKind.entries, EntityKind.TRACK) {
        when (it) {
            EntityKind.TRACK -> 0
            EntityKind.EMITTER -> 1
            EntityKind.ZONE -> 2
            EntityKind.ASSET -> 3
            EntityKind.REPORT -> 4
            EntityKind.SENSOR -> 5
            EntityKind.CLASS -> 6
        }
    }

    val affiliation = EnumCodec(Affiliation.entries, Affiliation.UNKNOWN) {
        when (it) {
            Affiliation.FRIEND -> 0
            Affiliation.HOSTILE -> 1
            Affiliation.UNKNOWN -> 2
            Affiliation.NEUTRAL -> 3
        }
    }

    val syncState = EnumCodec(SyncState.entries, SyncState.PENDING) {
        when (it) {
            SyncState.SYNCED -> 0
            SyncState.PENDING -> 1
            SyncState.CONFLICT -> 2
        }
    }

    val relation = EnumCodec(RelationType.entries, RelationType.INFERRED) {
        when (it) {
            RelationType.DETECTED_IN -> 0
            RelationType.NEAR -> 1
            RelationType.CORROBORATES -> 2
            RelationType.TASKED_TO -> 3
            RelationType.INFERRED -> 4
        }
    }

    val sourceFormat = EnumCodec(SourceFormat.entries, SourceFormat.SPOT_TEXT) {
        when (it) {
            SourceFormat.RF_PING -> 0
            SourceFormat.SPOT_TEXT -> 1
            SourceFormat.AIS_JSON -> 2
            SourceFormat.ADSB_JSON -> 3
        }
    }

    val missionStatus = EnumCodec(MissionStatus.entries, MissionStatus.DRAFT) {
        when (it) {
            MissionStatus.DRAFT -> 0
            MissionStatus.EN_ROUTE -> 1
            MissionStatus.LOITER -> 2
            MissionStatus.REPLANNING -> 3
            MissionStatus.RTB -> 4
            MissionStatus.COMPLETE -> 5
        }
    }

    val decisionKind = EnumCodec(DecisionKind.entries, DecisionKind.HOLD) {
        when (it) {
            DecisionKind.APPROVAL -> 0
            DecisionKind.REROUTE -> 1
            DecisionKind.CAPTURE -> 2
            DecisionKind.HOLD -> 3
            DecisionKind.REPLAN -> 4
            DecisionKind.RTB -> 5
            DecisionKind.THREAT -> 6
            DecisionKind.RESUME -> 7
        }
    }

    val coaProfile = EnumCodec(CoaProfile.entries, CoaProfile.DIRECT) {
        when (it) {
            CoaProfile.STEALTH -> 0
            CoaProfile.DIRECT -> 1
            CoaProfile.ENDURANCE -> 2
        }
    }

    val outboxOp = EnumCodec(OutboxOp.entries, OutboxOp.UPSERT) {
        when (it) {
            OutboxOp.INSERT -> 0
            OutboxOp.UPSERT -> 1
            OutboxOp.DELETE -> 2
        }
    }

    val outboxStatus = EnumCodec(OutboxStatus.entries, OutboxStatus.PENDING) {
        when (it) {
            OutboxStatus.PENDING -> 0
            OutboxStatus.IN_FLIGHT -> 1
            OutboxStatus.SYNCED -> 2
            OutboxStatus.CONFLICT -> 3
        }
    }
}

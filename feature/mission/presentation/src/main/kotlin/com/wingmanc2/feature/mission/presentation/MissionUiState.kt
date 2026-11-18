package com.wingmanc2.feature.mission.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.ui.StatusTone
import com.wingmanc2.core.ui.map.MapFraming
import com.wingmanc2.core.ui.map.MapLayers
import com.wingmanc2.core.ui.map.MapLiveAsset
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal sealed interface MissionUiState {
    val subtitle: String

    data object Loading : MissionUiState {
        override val subtitle = ""
    }

    data object NoMission : MissionUiState {
        override val subtitle = "No active mission"
    }

    @Immutable
    data class Active(
        override val subtitle: String,
        val status: String,
        val statusTone: StatusTone,
        val layers: MapLayers,
        val framing: MapFraming,
        val decisions: ImmutableList<DecisionUi>,
        val decisionsMeta: String,
        val holdLabel: String,
        val holding: Boolean,
        val canCommand: Boolean,
        val userMessage: String?,
    ) : MissionUiState
}

@Immutable
internal data class DecisionUi(
    val id: Long,
    val tone: StatusTone,
    val time: String,
    val title: String,
    val rationale: String,
)

@Immutable
internal data class TelemetryUi(
    val altitude: String,
    val climb: String,
    val speed: String,
    val heading: String,
    val battery: String,
    val batteryTone: StatusTone,
    val reserve: String,
    val link: String,
    val linkTone: StatusTone,
    val linkDetail: String,
)

@Immutable
internal data class MissionLiveUi(
    val telemetry: TelemetryUi,
    val clock: String?,
    val asset: MapLiveAsset?,
)

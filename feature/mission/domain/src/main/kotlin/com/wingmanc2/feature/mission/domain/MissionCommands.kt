package com.wingmanc2.feature.mission.domain

import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.model.MissionCommand
import javax.inject.Inject

class IssueMissionCommandUseCase @Inject constructor(private val missions: MissionRepository) {
    suspend operator fun invoke(command: MissionCommand) = missions.command(command)
}

class ReplanMissionUseCase @Inject constructor(
    private val missions: MissionRepository,
    private val focus: FocusRepository,
) {
    suspend operator fun invoke() {
        missions.activeMission.value?.let { focus.focus(it.targetId) }
        missions.command(MissionCommand.REPLAN)
    }
}

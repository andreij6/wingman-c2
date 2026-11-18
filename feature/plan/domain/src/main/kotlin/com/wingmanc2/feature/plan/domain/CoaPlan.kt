package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.OntologyEntity

data class CoaPlan(
    val intent: CommanderIntent,
    val target: OntologyEntity,
    val asset: OntologyEntity,
    val threats: List<OntologyEntity>,
    val courses: List<CourseOfAction>,
)

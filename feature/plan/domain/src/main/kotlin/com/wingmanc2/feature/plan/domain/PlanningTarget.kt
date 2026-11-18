package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEntity

const val DEFAULT_TARGET_KEY = "TRK-07"

internal val OntologyEntity.isPlanningTarget: Boolean
    get() = kind != EntityKind.ASSET && kind != EntityKind.CLASS && affiliation != Affiliation.FRIEND

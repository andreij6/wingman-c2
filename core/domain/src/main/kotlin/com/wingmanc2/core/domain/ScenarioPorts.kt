package com.wingmanc2.core.domain

import com.wingmanc2.core.model.ScenarioInject

interface ScenarioController {
    suspend fun inject(inject: ScenarioInject)
}

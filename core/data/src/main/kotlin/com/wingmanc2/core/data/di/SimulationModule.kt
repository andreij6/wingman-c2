package com.wingmanc2.core.data.di

import com.wingmanc2.core.model.ScenarioProfile
import com.wingmanc2.core.simulation.scenario.ScenarioKeys
import com.wingmanc2.core.simulation.scenario.ScenarioSeed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SimulationModule {
    @Provides
    @Singleton
    fun scenarioProfile(): ScenarioProfile = ScenarioProfile(
        primaryAssetKey = ScenarioKeys.UAS_01,
        home = ScenarioSeed.home,
        defaultFocusKey = ScenarioKeys.TRK_07,
        defaultIntent = ScenarioSeed.COMMANDER_INTENT,
    )
}

package com.wingmanc2.core.data.di

import android.content.Context
import com.wingmanc2.core.algorithms.fusion.EntityResolver
import com.wingmanc2.core.algorithms.planning.CoaPlanner
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.data.link.SimulatedLinkStateRepository
import com.wingmanc2.core.data.mission.MissionRuntime
import com.wingmanc2.core.data.mission.OnDeviceMissionPlanner
import com.wingmanc2.core.data.ontology.OfflineFirstOntologyRepository
import com.wingmanc2.core.data.ontology.SessionFocusRepository
import com.wingmanc2.core.data.ontology.StoreAssetRegistry
import com.wingmanc2.core.data.scenario.SimulatedScenarioController
import com.wingmanc2.core.data.sync.MirrorSyncRepository
import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.LinkStateRepository
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.domain.ScenarioController
import com.wingmanc2.core.domain.SyncRepository
import com.wingmanc2.core.domain.TelemetryStreamSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindingsModule {
    @Binds
    abstract fun ontologyRepository(impl: OfflineFirstOntologyRepository): OntologyRepository

    @Binds
    abstract fun focusRepository(impl: SessionFocusRepository): FocusRepository

    @Binds
    abstract fun missionPlannerService(impl: OnDeviceMissionPlanner): MissionPlannerService

    @Binds
    abstract fun missionRepository(impl: MissionRuntime): MissionRepository

    @Binds
    abstract fun telemetryStreamSource(impl: MissionRuntime): TelemetryStreamSource

    @Binds
    abstract fun linkStateRepository(impl: SimulatedLinkStateRepository): LinkStateRepository

    @Binds
    abstract fun syncRepository(impl: MirrorSyncRepository): SyncRepository

    @Binds
    abstract fun scenarioController(impl: SimulatedScenarioController): ScenarioController

    @Binds
    abstract fun assetRegistry(impl: StoreAssetRegistry): AssetRegistry
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidersModule {
    @Provides
    @Dispatcher(WingmanDispatcher.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(WingmanDispatcher.Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@Dispatcher(WingmanDispatcher.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        @Dispatcher(WingmanDispatcher.IO) io: CoroutineDispatcher,
    ): WingmanDatabase = WingmanDatabase(context, io)

    @Provides
    @Singleton
    fun entityResolver(): EntityResolver = EntityResolver()

    @Provides
    @Singleton
    fun coaPlanner(): CoaPlanner = CoaPlanner()
}

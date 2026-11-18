package com.wingmanc2.di

import android.util.Log
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {
    private const val SCOPE_TAG = "ApplicationScope"

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
        CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, failure -> Log.e(SCOPE_TAG, "Application job failed", failure) })

    @Provides
    fun clock(): Clock = Clock.System
}

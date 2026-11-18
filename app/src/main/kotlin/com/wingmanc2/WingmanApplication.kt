package com.wingmanc2

import android.app.Application
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.data.runtime.WingmanRuntime
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WingmanApplication : Application() {
    @Inject lateinit var runtime: Lazy<WingmanRuntime>

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope.launch { runtime.get().start() }
    }
}

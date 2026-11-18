package com.wingmanc2

import android.app.Application
import com.wingmanc2.core.data.runtime.WingmanRuntime
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WingmanApplication : Application() {
    @Inject lateinit var runtime: WingmanRuntime

    override fun onCreate() {
        super.onCreate()
        runtime.start()
    }
}

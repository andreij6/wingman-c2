package com.wingmanc2.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Dispatcher(val value: WingmanDispatcher)

enum class WingmanDispatcher { Default, IO }

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

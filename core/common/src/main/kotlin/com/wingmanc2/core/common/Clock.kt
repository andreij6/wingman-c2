package com.wingmanc2.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

fun Clock.ticks(periodMs: Long): Flow<Long> = flow {
    while (true) {
        emit(nowMs())
        delay(periodMs)
    }
}

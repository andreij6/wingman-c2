package com.wingmanc2.core.common

fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

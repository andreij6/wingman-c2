package com.wingmanc2.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

fun ViewModel.launchAction(onError: (Exception) -> Unit, block: suspend () -> Unit): Job = viewModelScope.launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onError(failure)
    }
}

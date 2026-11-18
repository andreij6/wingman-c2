package com.wingmanc2

import androidx.lifecycle.ViewModel
import com.wingmanc2.core.domain.ObserveLinkStateUseCase
import com.wingmanc2.core.model.LinkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(observeLinkState: ObserveLinkStateUseCase) : ViewModel() {
    val linkState: StateFlow<LinkState> = observeLinkState()
}

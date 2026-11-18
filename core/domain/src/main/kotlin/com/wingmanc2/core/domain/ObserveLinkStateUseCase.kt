package com.wingmanc2.core.domain

import com.wingmanc2.core.model.LinkState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLinkStateUseCase @Inject constructor(private val links: LinkStateRepository) {
    operator fun invoke(): StateFlow<LinkState> = links.linkState
}

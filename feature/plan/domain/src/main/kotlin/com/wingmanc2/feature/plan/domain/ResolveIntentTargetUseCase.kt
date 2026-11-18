package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject

class ResolveIntentTargetUseCase @Inject constructor(private val ontology: OntologyRepository) {
    suspend operator fun invoke(intent: CommanderIntent, currentKey: String): OntologyEntity? {
        val key = intent.targetKey?.takeIf { it != currentKey } ?: return null
        return ontology.findByKey(key)?.takeIf { it.isPlanningTarget }
    }
}

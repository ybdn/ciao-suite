package dev.ybdn.ciaocloud.domain.repository

import dev.ybdn.ciaocloud.domain.model.TriageDecision
import dev.ybdn.ciaocloud.domain.model.TriageState
import kotlinx.coroutines.flow.Flow

/** Décisions de tri (table `triage_state`), par clé stable (`phone:<id>` ou `ssd:<chemin>`). */
interface TriageRepository {
    fun observeAll(): Flow<List<TriageState>>

    suspend fun upsert(state: TriageState)

    suspend fun delete(keys: Collection<String>)

    /** Supprime toutes les décisions des types donnés. */
    suspend fun deleteByDecisions(decisions: Set<TriageDecision>)

    /** Déplace une décision vers une nouvelle clé (ex. `phone:` → `ssd:` après transfert) ; sans effet si elle n'existe pas. */
    suspend fun renameKey(oldKey: String, newKey: String)
}

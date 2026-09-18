package dev.ybdn.ciaocloud.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ybdn.ciaocloud.domain.model.TriageDecision
import dev.ybdn.ciaocloud.domain.model.TriageState

@Entity(tableName = "triage_state")
data class TriageStateEntity(
    /** Même format que la clé de favori : `phone:<mediaStoreId>` ou `ssd:<chemin relatif>` (`FavoriteKeys`). */
    @PrimaryKey val key: String,
    val decision: TriageDecision,
    val decidedAtEpochMillis: Long,
    /** Renseigné uniquement pour `SNOOZED` : date de réapparition dans la pile. */
    val snoozeUntilEpochMillis: Long?,
)

fun TriageStateEntity.toDomain() = TriageState(key, decision, decidedAtEpochMillis, snoozeUntilEpochMillis)

fun TriageState.toEntity() = TriageStateEntity(key, decision, decidedAtEpochMillis, snoozeUntilEpochMillis)

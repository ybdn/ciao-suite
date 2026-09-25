package dev.ybdn.ciao.galerie.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.ybdn.ciao.galerie.domain.model.TriageDecision
import kotlinx.coroutines.flow.Flow

@Dao
interface TriageStateDao {

    // Lecture en transaction, comme les favoris : pas de lecture partielle pendant une écriture concurrente.
    @Transaction
    @Query("SELECT * FROM triage_state")
    fun observeAll(): Flow<List<TriageStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TriageStateEntity)

    @Query("DELETE FROM triage_state WHERE `key` IN (:keys)")
    suspend fun deleteAll(keys: List<String>)

    @Query("DELETE FROM triage_state WHERE decision IN (:decisions)")
    suspend fun deleteByDecisions(decisions: List<TriageDecision>)

    /** Déplace une décision vers une nouvelle clé ; sans effet si l'ancienne clé n'a pas de décision. */
    @Query("UPDATE OR REPLACE triage_state SET `key` = :newKey WHERE `key` = :oldKey")
    suspend fun renameKey(oldKey: String, newKey: String)
}

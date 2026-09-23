package dev.ybdn.ciao.clavier.data.clipboard

import android.content.Context
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardItem
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardRetention
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Historique du presse-papiers (apps/clavier/docs/spec-v1.md §9), stocké sur l'appareil seulement. */
class ClipboardHistory(context: Context) {

    private val dao = ClavierDatabase.get(context).clipboardDao()

    val items: Flow<List<ClipboardItem>> = dao.observeAll().map { list -> list.map(ClipboardEntity::toDomain) }

    suspend fun add(text: String, now: Long, retention: ClipboardRetention) {
        dao.upsertText(text, now)
        prune(now, retention)
    }

    /** Supprime les éléments expirés et ceux au-delà de la limite. */
    suspend fun prune(now: Long, retention: ClipboardRetention) {
        val toRemove = ClipboardRules.toRemove(dao.getAll().map(ClipboardEntity::toDomain), now, retention)
        if (toRemove.isNotEmpty()) dao.delete(toRemove.map { it.id })
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(id: Long) = dao.delete(listOf(id))

    suspend fun clear() = dao.deleteAll()
}

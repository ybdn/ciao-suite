package dev.ybdn.ciao.galerie.domain.repository

import dev.ybdn.ciao.galerie.domain.model.SsdMedia
import kotlinx.coroutines.flow.Flow

/**
 * Index local (Room) des médias archivés sur le SSD : lister `DCIM/` via SAF est trop lent pour
 * chaque affichage, et l'index reste consultable SSD débranché.
 */
interface SsdMediaIndex {
    fun observeAll(): Flow<List<SsdMedia>>

    suspend fun get(relativePath: String): SsdMedia?

    suspend fun upsert(media: SsdMedia)

    suspend fun remove(relativePath: String)

    /** Remplace le contenu de l'index par [media] (réindexation complète), en une transaction. */
    suspend fun replaceAll(media: List<SsdMedia>)
}

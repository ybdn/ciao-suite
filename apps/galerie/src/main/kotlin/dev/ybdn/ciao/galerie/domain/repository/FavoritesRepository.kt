package dev.ybdn.ciao.galerie.domain.repository

import kotlinx.coroutines.flow.Flow

/** Favoris de la galerie, par clé stable (`phone:<id>` ou `ssd:<chemin>`, voir `FavoriteKeys`). */
interface FavoritesRepository {
    fun observeKeys(): Flow<Set<String>>

    suspend fun setFavorite(keys: Collection<String>, favorite: Boolean)

    /** Déplace un favori vers une nouvelle clé (ex. `phone:` → `ssd:` après transfert) ; sans effet s'il n'existe pas. */
    suspend fun renameKey(oldKey: String, newKey: String)
}

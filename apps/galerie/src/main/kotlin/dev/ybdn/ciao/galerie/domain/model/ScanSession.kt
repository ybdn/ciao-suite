package dev.ybdn.ciao.galerie.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Résultat du dernier scan, partagé entre l'écran d'accueil et le service de transfert : le
 * service transfère exactement la liste affichée à l'utilisateur, sans re-scanner.
 */
class ScanSession {

    private val _files = MutableStateFlow<List<MediaFile>?>(null)

    /** `null` tant qu'aucun scan n'a été fait (ou après un transfert), liste vide si rien à transférer. */
    val files: StateFlow<List<MediaFile>?> = _files.asStateFlow()

    fun update(files: List<MediaFile>) {
        _files.value = files
    }

    fun clear() {
        _files.value = null
    }
}

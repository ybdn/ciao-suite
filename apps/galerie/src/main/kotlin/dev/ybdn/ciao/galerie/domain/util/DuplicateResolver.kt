package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.repository.DestinationEntry

/** Issue du calcul du nom à destination pour un fichier source. */
sealed interface DestinationDecision {
    /** Un fichier au contenu identique existe déjà : aucune copie. [checksum] est le CRC32 de la source. */
    data class AlreadyPresent(val existingFileName: String, val checksum: String) : DestinationDecision

    /** Aucun doublon : copier sous [fileName], premier nom libre (suffixe `_n` si besoin). */
    data class CopyAs(val fileName: String) : DestinationDecision
}

/**
 * Évite de recopier un média déjà présent dans le dossier du jour (copie faite en dehors de l'app) :
 * le nom souhaité et ses variantes suffixées déjà présentes (`IMG.jpg`, `IMG_1.jpg`…) sont comparés
 * à la source, d'abord par taille puis par contenu. Les SSD (exFAT/FAT32) ignorant la casse, les
 * noms sont comparés sans en tenir compte.
 */
object DuplicateResolver {

    /**
     * @param identicalContentChecksum compare octet par octet la source au fichier existant nommé,
     * et renvoie le CRC32 de la source s'ils sont identiques, null sinon.
     */
    suspend fun resolve(
        desiredName: String,
        sourceSizeBytes: Long,
        existingEntries: Collection<DestinationEntry>,
        identicalContentChecksum: suspend (existingFileName: String) -> String?,
    ): DestinationDecision {
        val candidates = existingEntries
            .filterNot { it.isDirectory }
            .mapNotNull { entry -> collisionRank(desiredName, entry.name)?.let { rank -> rank to entry } }
            .sortedBy { it.first }
            .map { it.second }

        for (candidate in candidates) {
            if (candidate.sizeBytes != sourceSizeBytes) continue
            identicalContentChecksum(candidate.name)?.let { checksum ->
                return DestinationDecision.AlreadyPresent(candidate.name, checksum)
            }
        }

        val takenNames = existingEntries.mapTo(HashSet()) { it.name.lowercase() }
        return DestinationDecision.CopyAs(
            FileNameCollisionResolver.resolveAvailableName(desiredName) { it.lowercase() in takenNames },
        )
    }

    /**
     * 0 si [candidateName] est [desiredName], n s'il en est la variante suffixée `_n`, null sinon
     * (comparaison insensible à la casse).
     */
    fun collisionRank(desiredName: String, candidateName: String): Int? {
        if (candidateName.equals(desiredName, ignoreCase = true)) return 0
        val (base, extension) = FileNameCollisionResolver.splitBaseAndExtension(desiredName)
        val suffixed = Regex(
            Regex.escape(base) + "_([1-9][0-9]{0,8})" + if (extension.isEmpty()) "" else Regex.escape(".$extension"),
            RegexOption.IGNORE_CASE,
        )
        return suffixed.matchEntire(candidateName)?.groupValues?.get(1)?.toInt()
    }
}

package dev.ybdn.ciao.clavier.domain.emoji

/** Catégories du panneau (apps/clavier/docs/spec-v1.md §8), dans l'ordre des groupes Unicode. */
enum class EmojiGroup(val unicodeName: String) {
    Smileys("Smileys & Emotion"),
    People("People & Body"),
    Animals("Animals & Nature"),
    Food("Food & Drink"),
    Travel("Travel & Places"),
    Activities("Activities"),
    Objects("Objects"),
    Symbols("Symbols"),
    Flags("Flags"),
    ;

    companion object {
        fun fromUnicodeName(name: String): EmojiGroup? = entries.firstOrNull { it.unicodeName == name }
    }
}

/**
 * Un emoji du panneau. [skinTones] : ses cinq variantes de couleur de peau (claire à foncée), vide
 * s'il n'en a pas.
 */
data class Emoji(val value: String, val skinTones: List<String> = emptyList())

data class EmojiCategory(val group: EmojiGroup, val emojis: List<Emoji>)

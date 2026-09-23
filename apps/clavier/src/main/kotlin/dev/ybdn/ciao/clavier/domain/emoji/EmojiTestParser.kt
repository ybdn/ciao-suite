package dev.ybdn.ciao.clavier.domain.emoji

/**
 * Lit `emoji-test.txt` d'Unicode (apps/clavier/docs/spec-v1.md §8) : les emojis
 * « fully-qualified » de chaque groupe, sans le groupe « Component » (modificateurs seuls). Les
 * variantes de couleur de peau sont rattachées à leur emoji de base ; les combinaisons de deux
 * couleurs différentes (couples…) ne sont pas proposées.
 */
object EmojiTestParser {

    private const val GroupPrefix = "# group:"
    private const val FullyQualified = "fully-qualified"
    private const val VariationSelector = 0xFE0F
    private val SkinToneModifiers = 0x1F3FB..0x1F3FF

    fun parse(lines: Sequence<String>): List<EmojiCategory> {
        val bases = LinkedHashMap<EmojiGroup, MutableList<String>>()
        val tones = HashMap<String, MutableList<String>>()
        val baseKeys = HashSet<String>()
        var group: EmojiGroup? = null

        for (line in lines) {
            if (line.startsWith(GroupPrefix)) {
                group = EmojiGroup.fromUnicodeName(line.removePrefix(GroupPrefix).trim())
                continue
            }
            val currentGroup = group ?: continue
            if (line.isBlank() || line.startsWith("#")) continue
            val fields = line.substringBefore('#').split(';')
            if (fields.size < 2 || fields[1].trim() != FullyQualified) continue
            val codePoints = fields[0].trim().split(' ').filter { it.isNotEmpty() }.map { it.toInt(16) }
            val emoji = String(codePoints.toIntArray(), 0, codePoints.size)
            val modifiers = codePoints.filter { it in SkinToneModifiers }.distinct()
            val key = baseKey(codePoints)
            when {
                modifiers.isEmpty() -> {
                    bases.getOrPut(currentGroup) { mutableListOf() }.add(emoji)
                    baseKeys.add(key)
                }
                // Une seule couleur (éventuellement répétée) et un emoji de base connu : variante.
                modifiers.size == 1 && key in baseKeys -> tones.getOrPut(key) { mutableListOf() }.add(emoji)
                // Couleurs mélangées : pas proposées (cf. KDoc).
                else -> Unit
            }
        }

        return bases.map { (group, emojis) ->
            EmojiCategory(group, emojis.map { Emoji(it, tones[baseKey(it)].orEmpty()) })
        }
    }

    /** Clé commune à un emoji et à ses variantes : sans sélecteur de variante ni couleur de peau. */
    private fun baseKey(codePoints: List<Int>): String =
        codePoints.filter { it != VariationSelector && it !in SkinToneModifiers }.joinToString(" ")

    private fun baseKey(emoji: String): String = baseKey(emoji.codePoints().toArray().toList())
}

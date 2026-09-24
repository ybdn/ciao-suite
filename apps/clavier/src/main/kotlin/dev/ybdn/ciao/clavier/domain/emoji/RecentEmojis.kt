package dev.ybdn.ciao.clavier.domain.emoji

/** Emojis récents (apps/clavier/docs/spec-v1.md §8) : les plus récents d'abord, sans doublon. */
object RecentEmojis {
    const val Max = 30

    fun add(recents: List<String>, emoji: String): List<String> =
        (listOf(emoji) + recents.filter { it != emoji }).take(Max)
}

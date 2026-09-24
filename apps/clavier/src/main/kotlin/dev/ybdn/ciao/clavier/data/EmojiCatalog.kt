package dev.ybdn.ciao.clavier.data

import android.content.Context
import android.graphics.Paint
import dev.ybdn.ciao.clavier.domain.emoji.Emoji
import dev.ybdn.ciao.clavier.domain.emoji.EmojiCategory
import dev.ybdn.ciao.clavier.domain.emoji.EmojiTestParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Emojis du panneau, lus depuis `assets/emoji/emoji-test.txt` (Unicode, licence dans
 * `LICENSE-Unicode.txt` à côté) et filtrés selon ce que la police du téléphone sait dessiner
 * (`Paint.hasGlyph`) : un emoji trop récent pour l'appareil n'apparaît pas en carré vide. Filtrer ici,
 * sur l'appareil, suit les mises à jour de la police emoji sans nouvelle version de l'app.
 */
object EmojiCatalog {

    @Volatile
    private var cached: List<EmojiCategory>? = null

    suspend fun load(context: Context): List<EmojiCategory> = cached ?: withContext(Dispatchers.Default) {
        cached ?: parseAndFilter(context).also { cached = it }
    }

    private fun parseAndFilter(context: Context): List<EmojiCategory> {
        val categories = context.assets.open("emoji/emoji-test.txt").bufferedReader().useLines(EmojiTestParser::parse)
        val paint = Paint()
        return categories.map { category ->
            EmojiCategory(
                group = category.group,
                emojis = category.emojis.filter { paint.hasGlyph(it.value) }.map { emoji ->
                    val tones = emoji.skinTones.filter(paint::hasGlyph)
                    if (tones.size == emoji.skinTones.size) emoji else Emoji(emoji.value, tones)
                },
            )
        }
    }
}

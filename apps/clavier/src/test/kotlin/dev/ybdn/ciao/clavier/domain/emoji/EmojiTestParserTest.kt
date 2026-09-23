package dev.ybdn.ciao.clavier.domain.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiTestParserTest {

    private val sample = """
        # group: Smileys & Emotion
        # subgroup: face-smiling
        1F600                                                  ; fully-qualified     # 😀 E1.0 grinning face
        263A FE0F                                              ; fully-qualified     # ☺️ E0.6 smiling face
        263A                                                   ; unqualified         # ☺ E0.6 smiling face

        # group: People & Body
        1F44B                                                  ; fully-qualified     # 👋 E0.6 waving hand
        1F44B 1F3FB                                            ; fully-qualified     # 👋🏻 E1.0 waving hand: light skin tone
        1F44B 1F3FF                                            ; fully-qualified     # 👋🏿 E1.0 waving hand: dark skin tone
        26F9 FE0F                                              ; fully-qualified     # ⛹️ E0.7 person bouncing ball
        26F9 1F3FB                                             ; fully-qualified     # ⛹🏻 E2.0 person bouncing ball: light skin tone
        1F9D1 200D 1F91D 200D 1F9D1                            ; fully-qualified     # 🧑‍🤝‍🧑 E12.0 people holding hands
        1F9D1 1F3FB 200D 1F91D 200D 1F9D1 1F3FB                ; fully-qualified     # 🧑🏻‍🤝‍🧑🏻 E12.0 people holding hands: light skin tone
        1F9D1 1F3FB 200D 1F91D 200D 1F9D1 1F3FC                ; fully-qualified     # 🧑🏻‍🤝‍🧑🏼 E12.1 people holding hands: light skin tone, medium-light skin tone

        # group: Component
        1F3FB                                                  ; fully-qualified     # 🏻 E1.0 light skin tone
    """.trimIndent()

    private val categories = EmojiTestParser.parse(sample.lineSequence())

    @Test
    fun `keeps fully-qualified emojis by group, without components`() {
        assertEquals(listOf(EmojiGroup.Smileys, EmojiGroup.People), categories.map { it.group })
        assertEquals(listOf("😀", "☺️"), categories[0].emojis.map { it.value })
    }

    @Test
    fun `skin tones are attached to their base emoji`() {
        val people = categories[1].emojis
        assertEquals(listOf("👋", "⛹️", "🧑‍🤝‍🧑"), people.map { it.value })
        assertEquals(listOf("👋🏻", "👋🏿"), people[0].skinTones)
        // La base a un sélecteur de variante (FE0F) que la variante n'a pas.
        assertEquals(listOf("⛹🏻"), people[1].skinTones)
        // Deux fois la même couleur : proposée ; deux couleurs différentes : non.
        assertEquals(listOf("🧑🏻‍🤝‍🧑🏻"), people[2].skinTones)
    }

    @Test
    fun `the bundled Unicode file parses into the nine categories`() {
        val file = File("src/main/assets/emoji/emoji-test.txt")
        val parsed = file.useLines { EmojiTestParser.parse(it) }
        assertEquals(EmojiGroup.entries, parsed.map { it.group })
        val all = parsed.flatMap { it.emojis }
        assertTrue(all.size > 1_800)
        assertEquals(all.size, all.map { it.value }.toSet().size)
        assertTrue(all.single { it.value == "👍" }.skinTones.size == 5)
        assertFalse(all.any { it.value == "🏻" })
    }

    @Test
    fun `recents keep the thirty latest, most recent first, without duplicates`() {
        val recents = (1..30).map { "e$it" }
        val updated = RecentEmojis.add(recents, "e5")
        assertEquals("e5", updated.first())
        assertEquals(30, updated.size)
        assertEquals(1, updated.count { it == "e5" })
        val withNew = RecentEmojis.add(recents, "new")
        assertEquals(RecentEmojis.Max, withNew.size)
        assertFalse("e30" in withNew)
    }
}

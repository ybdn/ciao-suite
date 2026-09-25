package dev.ybdn.ciao.galerie.domain.util

import dev.ybdn.ciao.galerie.domain.repository.DestinationEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateResolverTest {

    private fun file(name: String, size: Long) = DestinationEntry(name, size, isDirectory = false)

    @Test
    fun `copies under the desired name when there is no candidate`() = runTest {
        val compared = mutableListOf<String>()

        val decision = DuplicateResolver.resolve("IMG_0001.jpg", 100, listOf(file("IMG_0002.jpg", 100))) {
            compared += it
            "crc"
        }

        assertEquals(DestinationDecision.CopyAs("IMG_0001.jpg"), decision)
        assertEquals(emptyList<String>(), compared)
    }

    @Test
    fun `does not compare content when sizes differ`() = runTest {
        val compared = mutableListOf<String>()

        val decision = DuplicateResolver.resolve("IMG_0001.jpg", 100, listOf(file("IMG_0001.jpg", 99))) {
            compared += it
            "crc"
        }

        assertEquals(DestinationDecision.CopyAs("IMG_0001_1.jpg"), decision)
        assertEquals(emptyList<String>(), compared)
    }

    @Test
    fun `copies with a suffix when size matches but content differs`() = runTest {
        val decision = DuplicateResolver.resolve("IMG_0001.jpg", 100, listOf(file("IMG_0001.jpg", 100))) { null }

        assertEquals(DestinationDecision.CopyAs("IMG_0001_1.jpg"), decision)
    }

    @Test
    fun `detects a duplicate stored under a suffixed variant`() = runTest {
        val existing = listOf(
            file("IMG_0001.jpg", 100),
            file("IMG_0001_1.jpg", 50),
            file("IMG_0001_2.jpg", 100),
        )
        val compared = mutableListOf<String>()

        val decision = DuplicateResolver.resolve("IMG_0001.jpg", 100, existing) { name ->
            compared += name
            if (name == "IMG_0001_2.jpg") "abcd1234" else null
        }

        assertEquals(DestinationDecision.AlreadyPresent("IMG_0001_2.jpg", "abcd1234"), decision)
        assertEquals(listOf("IMG_0001.jpg", "IMG_0001_2.jpg"), compared)
    }

    @Test
    fun `matches existing names regardless of case`() = runTest {
        val decision = DuplicateResolver.resolve("IMG_0001.jpg", 100, listOf(file("img_0001.JPG", 100))) { "crc" }

        assertEquals(DestinationDecision.AlreadyPresent("img_0001.JPG", "crc"), decision)
    }

    @Test
    fun `avoids a case-insensitive name collision when copying`() = runTest {
        val decision = DuplicateResolver.resolve("IMG_0001.jpg", 100, listOf(file("img_0001.JPG", 10))) { null }

        assertEquals(DestinationDecision.CopyAs("IMG_0001_1.jpg"), decision)
    }

    @Test
    fun `ignores directories and unrelated suffixes`() {
        assertEquals(null, DuplicateResolver.collisionRank("IMG.jpg", "IMG_a.jpg"))
        assertEquals(null, DuplicateResolver.collisionRank("IMG.jpg", "IMG_01.jpg"))
        assertEquals(null, DuplicateResolver.collisionRank("IMG.jpg", "IMG_1.png"))
        assertEquals(3, DuplicateResolver.collisionRank("IMG.jpg", "img_3.JPG"))
        assertEquals(0, DuplicateResolver.collisionRank("IMG.jpg", "img.jpg"))
    }
}

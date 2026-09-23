package dev.ybdn.ciao.clavier.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariantPopupGeometryTest {

    private fun geometry(key: Rect, count: Int = 5) = VariantPopupGeometry.of(
        key = key,
        count = count,
        cellWidth = 40f,
        height = 50f,
        gap = 4f,
        border = 3f,
        shadow = 8f,
        containerWidth = 400f,
    )

    @Test
    fun `first variant is centred over the key`() {
        val popup = geometry(Rect(left = 100f, top = 100f, right = 140f, bottom = 160f))
        assertEquals(100f - 3f, popup.left, 0.01f)
        assertEquals(100f - 4f - 50f, popup.top, 0.01f)
        assertEquals(0, popup.indexAt(Offset(120f, 130f)))
    }

    @Test
    fun `popup stays inside the keyboard`() {
        val right = geometry(Rect(left = 360f, top = 100f, right = 400f, bottom = 160f))
        assertEquals(400f - 8f - right.width, right.left, 0.01f)
        val left = geometry(Rect(left = 0f, top = 0f, right = 40f, bottom = 60f))
        assertEquals(0f, left.left, 0.01f)
        assertEquals(0f, left.top, 0.01f)
    }

    @Test
    fun `sliding selects the variant under the finger, clamped to the ends`() {
        val popup = geometry(Rect(left = 100f, top = 100f, right = 140f, bottom = 160f))
        assertEquals(2, popup.indexAt(Offset(popup.left + 3f + 2 * 40f + 5f, 120f)))
        assertEquals(4, popup.indexAt(Offset(399f, 120f)))
        assertEquals(0, popup.indexAt(Offset(0f, 120f)))
    }

    @Test
    fun `sliding well below the key cancels`() {
        val popup = geometry(Rect(left = 100f, top = 100f, right = 140f, bottom = 160f))
        assertNull(popup.indexAt(Offset(120f, 200f)))
    }
}

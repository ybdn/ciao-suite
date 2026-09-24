package dev.ybdn.ciao.clavier.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceCursorDragTest {

    @Test
    fun `small movements stay a tap`() {
        val drag = SpaceCursorDrag(startThreshold = 12f, step = 10f)
        assertEquals(0, drag.onMove(5f))
        assertEquals(0, drag.onMove(-11f))
        assertFalse(drag.isDragging)
    }

    @Test
    fun `crossing the threshold moves the cursor right away`() {
        val drag = SpaceCursorDrag(startThreshold = 12f, step = 10f)
        assertEquals(1, drag.onMove(12f))
        assertTrue(drag.isDragging)
        assertEquals(0, drag.onMove(21f))
        assertEquals(1, drag.onMove(22f))
        assertEquals(3, drag.onMove(52f))
    }

    @Test
    fun `dragging left moves backwards, and back again`() {
        val drag = SpaceCursorDrag(startThreshold = 12f, step = 10f)
        assertEquals(-1, drag.onMove(-12f))
        assertEquals(-2, drag.onMove(-32f))
        assertEquals(2, drag.onMove(-12f))
    }
}

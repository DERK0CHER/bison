package net.bison

import net.bison.domain.RowDrag
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the arithmetic of dragging a row.
 *
 * The gesture itself needs a device - the test harness here cannot drive a long press, which is
 * three attempts of evidence rather than a guess. What it can check is everything between the
 * finger having moved so many pixels and the row having moved so many places, which is where the
 * off-by-one lives.
 */
class RowDragTest {
    private val row = 100f

    @Test
    fun `less than a row moves nothing`() {
        val step = RowDrag.step(from = 1, travel = 99f, rowPx = row, rows = 4)

        assertEquals(1, step.to)
        assertEquals(99f, step.travel, 0.001f)
    }

    @Test
    fun `just over a row moves one place`() {
        val step = RowDrag.step(from = 1, travel = 120f, rowPx = row, rows = 4)

        assertEquals(2, step.to)
        // what is left keeps the finger and the row in step: the finger is twenty pixels into
        // the next row, and the row it is holding has to know that
        assertEquals(20f, step.travel, 0.001f)
    }

    @Test
    fun `a drag across several rows moves several`() {
        val step = RowDrag.step(from = 0, travel = 320f, rowPx = row, rows = 5)

        assertEquals(3, step.to)
        assertEquals(20f, step.travel, 0.001f)
    }

    @Test
    fun `dragging upwards works the same way`() {
        val step = RowDrag.step(from = 3, travel = -220f, rowPx = row, rows = 5)

        assertEquals(1, step.to)
        assertEquals(-20f, step.travel, 0.001f)
    }

    @Test
    fun `a row cannot be dragged past either end`() {
        assertEquals(0, RowDrag.step(from = 1, travel = -900f, rowPx = row, rows = 4).to)
        assertEquals(3, RowDrag.step(from = 1, travel = 900f, rowPx = row, rows = 4).to)
    }

    @Test
    fun `a list with nothing in it is left alone rather than divided by zero`() {
        assertEquals(0, RowDrag.step(from = 0, travel = 500f, rowPx = 0f, rows = 0).to)
    }
}

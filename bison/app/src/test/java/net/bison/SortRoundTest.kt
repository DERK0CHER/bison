package net.bison

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import net.bison.model.CodeTask
import net.bison.ui.SortRound
import net.bison.ui.theme.BisonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What can be checked about the sorting screen without a finger.
 *
 * The drag itself cannot, and the ignored test below is the evidence rather than an excuse: a
 * long press is timed on a clock the harness here does not run, and three ways of holding it -
 * advancing the main clock, advancing the event time, and both at once, with the drag broken
 * into frames - all left the rows exactly where they started, one row apart. That is what an
 * emulator is actually for.
 *
 * Writing it was still worth it. It found a real bug, which is fixed: every row keyed its
 * gesture on where it sat, so the first swap re-keyed the modifier and threw away the drag that
 * caused it.
 *
 * The rows are shuffled on every presentation, so nothing here names a row by its content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SortRoundTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val lines = listOf("erste();", "zweite();", "dritte();")

    private val task =
        CodeTask(
            prompt = "Bring die Zeilen in die richtige Reihenfolge",
            solution = lines.joinToString("\n"),
        )

    private fun yOf(line: String): Float = composeRule.onNodeWithText(line).fetchSemanticsNode().positionInRoot.y

    private fun heightOf(line: String): Float =
        composeRule
            .onNodeWithText(line)
            .fetchSemanticsNode()
            .size.height
            .toFloat()

    private fun show() {
        composeRule.setContent {
            BisonTheme { SortRound(task = task, round = "Runde 1 · 4× richtig", onSubmit = {}) }
        }
    }

    @Test
    @Ignore("the long press is not recognised here; the arithmetic is covered by RowDragTest")
    fun `a row dragged past the one under it changes places with it`() {
        show()
        val order = lines.sortedBy { yOf(it) }
        val top = order[0]
        val second = order[1]
        // far enough to cross one row, not so far as to depend on the gap between them
        val travel = heightOf(top) * 2f

        // The press held on both clocks it could be timed on: the main clock moved on, and then
        // an event sent with its own timestamp advanced by as much. Neither, nor both, gets the
        // gesture to start here.
        composeRule.onNodeWithText(top).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS)
        composeRule.onNodeWithText(top).performTouchInput {
            advanceEventTime(LONG_PRESS)
            moveBy(Offset(0f, 1f))
        }
        composeRule.mainClock.advanceTimeBy(50)

        // then the finger moves in steps, which is what a finger does: one jump the size of a
        // row is easy to mistake for a fling that never became a drag
        repeat(4) {
            composeRule.onNodeWithText(top).performTouchInput {
                advanceEventTime(16)
                moveBy(Offset(0f, travel / 4f))
            }
            composeRule.mainClock.advanceTimeBy(16)
        }
        composeRule.onNodeWithText(top).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500)

        assertTrue(
            "the row picked up did not move: top at ${yOf(top)}, the one under it at ${yOf(second)}",
            yOf(top) > yOf(second),
        )
    }

    @Test
    fun `a row that is only tapped stays where it is`() {
        show()
        val before = lines.sortedBy { yOf(it) }

        composeRule.onNodeWithText(before[0]).performClick()
        composeRule.mainClock.advanceTimeBy(500)

        // a tap is not a drag: a list that reordered itself on a stray touch would be unusable
        assertEquals(before, lines.sortedBy { yOf(it) })
    }

    @Test
    fun `checking the order gives a verdict and a way on`() {
        show()

        composeRule.onNodeWithText("Prüfen").performClick()

        // whichever way the shuffle fell, the round has to be finishable
        composeRule.onNodeWithText("Weiter").assertIsDisplayed()
    }

    private companion object {
        /** Comfortably past Android's long press timeout, which is half a second */
        const val LONG_PRESS = 1000L
    }
}

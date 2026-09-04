package net.bison

import android.graphics.Point
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.bison.data.DeckStore
import net.bison.model.Card
import net.bison.model.CodeTask
import net.bison.model.Deck
import net.bison.model.Subtopic
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The drag, on a real Android, because that is the only place it can be asked.
 *
 * The same gesture written against the JVM harness never starts: a long press is timed on a
 * clock that harness does not run, and holding the press on the test clock, on the event
 * timestamps, or on both left the rows exactly where they were. So this one goes the other way
 * round - the app is launched for real and driven from outside with touch events built by hand,
 * at real times, in the order and at the pace a finger would produce them. Nothing here supplies
 * its own clock, which is the entire point.
 *
 * The set is written straight into the app's own store first. One topic with one part and no
 * tags opens directly into the round, so there is no navigation to go wrong between the launch
 * and the thing being tested.
 */
@RunWith(AndroidJUnit4::class)
class SortDragTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val device = UiDevice.getInstance(instrumentation)

    private val lines = listOf("erste();", "zweite();", "dritte();")

    @Before
    fun putOneCodeCardInTheStore() {
        val task =
            CodeTask(
                prompt = "Bring die Zeilen in die richtige Reihenfolge",
                solution = lines.joinToString("\n"),
            )
        // the store is in the shared core now and knows nothing about a Context; the app decides
        // where an application's own files live, and here the test has to do the same
        DeckStore(File(instrumentation.targetContext.filesDir, DeckStore.FILE_NAME)).save(
            listOf(Deck("drag", "Sortieren", listOf(Subtopic("drag-0", "Teil", listOf(Card(task)))))),
        )
    }

    @Test
    fun aRowDraggedPastTheOneUnderItChangesPlacesWithIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openTheRound()

            // the rows are shuffled, so the two being watched are found by where they are
            val ordered = lines.sortedBy { centreOf(it).y }
            val top = ordered[0]
            val second = ordered[1]
            val from = centreOf(top)
            val gap = centreOf(second).y - from.y

            // one and a half rows: over the line that makes a swap, under the one that would
            // make two of them
            longPressDrag(from, Point(from.x, from.y + (gap * 1.5).toInt()))
            device.waitForIdle()

            val topNow = centreOf(top).y
            val secondNow = centreOf(second).y
            assertTrue(
                "the row picked up did not move: it is at $topNow, the one that was under it at $secondNow",
                topNow > secondNow,
            )
        }
    }

    @Test
    fun aTapDoesNotReorderAnything() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openTheRound()
            val before = lines.sortedBy { centreOf(it).y }

            device.findObject(By.text(before[0])).click()
            device.waitForIdle()

            // a list that reordered itself on a stray touch would be unusable
            assertTrue(before == lines.sortedBy { centreOf(it).y })
        }
    }

    /** The one topic opens straight into the round, so this is one tap and a wait */
    private fun openTheRound() {
        assertTrue(
            "the topic never appeared, so the app did not get as far as its own deck list",
            device.wait(Until.hasObject(By.text("Sortieren")), WAIT),
        )
        device.findObject(By.text("Sortieren")).click()
        assertTrue(
            "the sorting round never appeared",
            device.wait(Until.hasObject(By.text(lines[0])), WAIT),
        )
    }

    private fun centreOf(line: String): Point {
        val found = requireNotNull(device.findObject(By.text(line))) { "no row reading $line" }
        return Point(found.visibleBounds.centerX(), found.visibleBounds.centerY())
    }

    /**
     * A press, a hold, and then a drag - built out of the events themselves.
     *
     * The hold is a real sleep past the system's own long press timeout, because that timeout is
     * what the gesture is waiting for, and the moves are spaced a frame apart: one jump the size
     * of a row looks like a fling rather than a drag.
     */
    private fun longPressDrag(
        from: Point,
        to: Point,
    ) {
        val start = SystemClock.uptimeMillis()
        send(start, start, MotionEvent.ACTION_DOWN, from)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + HOLD_OVER)

        for (step in 1..STEPS) {
            val at =
                Point(
                    from.x + (to.x - from.x) * step / STEPS,
                    from.y + (to.y - from.y) * step / STEPS,
                )
            send(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, at)
            SystemClock.sleep(FRAME)
        }
        send(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, to)
    }

    private fun send(
        downAt: Long,
        at: Long,
        action: Int,
        where: Point,
    ) {
        val event = MotionEvent.obtain(downAt, at, action, where.x.toFloat(), where.y.toFloat(), 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.sendPointerSync(event)
        event.recycle()
    }

    private companion object {
        const val WAIT = 15_000L

        /** Comfortably past the long press, so a slow emulator does not decide the outcome */
        const val HOLD_OVER = 600L

        const val STEPS = 12

        const val FRAME = 20L
    }
}

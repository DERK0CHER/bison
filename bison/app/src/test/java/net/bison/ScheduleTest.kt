package net.bison

import net.bison.domain.Schedule
import net.bison.model.Card
import net.bison.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests when a card comes back.
 *
 * Dates are counted in days from 1970 here, so the numbers are small and nothing depends on what
 * day it happens to be when the tests run.
 */
class ScheduleTest {
    private fun card(
        box: Int = 0,
        due: Long = 0,
        interval: Int = 0,
        ease: Double = Card.EASE_START,
        lapses: Int = 0,
    ) = Card(
        task = Question("Frage", listOf("eins", "zwei"), 0),
        box = box,
        due = due,
        interval = interval,
        ease = ease,
        lapses = lapses,
    )

    @Test
    fun `a card still being learned is always worth asking`() {
        assertTrue(card(box = 0).isDue(100))
        assertTrue(card(box = Card.LEARNED_BOX - 1).isDue(100))
    }

    @Test
    fun `a finished card waits for its date`() {
        assertTrue(!card(box = Card.LEARNED_BOX, due = 105).isDue(100))
        assertTrue(card(box = Card.LEARNED_BOX, due = 100).isDue(100))
    }

    @Test
    fun `the first two intervals are a day and six days`() {
        val first = Schedule.reviewed(card(box = Card.LEARNED_BOX), today = 100, lapsed = false)
        assertEquals(1, first.interval)
        assertEquals(101L, first.due)

        val second = Schedule.reviewed(first, today = 101, lapsed = false)
        assertEquals(6, second.interval)
        assertEquals(107L, second.due)
    }

    @Test
    fun `after that the interval grows by the ease`() {
        val before = card(box = Card.LEARNED_BOX, interval = 6, ease = 2.5)

        val next = Schedule.reviewed(before, today = 0, lapsed = false)

        // remembering it lifts the ease first, so it is six times 2,6
        assertEquals(2.6, next.ease, 0.0001)
        assertEquals(16, next.interval)
    }

    @Test
    fun `a card that was lost goes back to a day and keeps most of its ease`() {
        val before = card(box = Card.LEARNED_BOX, interval = 40, ease = 2.5, lapses = 1)

        val next = Schedule.reviewed(before, today = 10, lapsed = true)

        assertEquals(1, next.interval)
        assertEquals(11L, next.due)
        assertEquals(2.3, next.ease, 0.0001)
        assertEquals(2, next.lapses)
    }

    @Test
    fun `the ease has a floor, because a card at one day for ever is not a schedule`() {
        var card = card(box = Card.LEARNED_BOX, ease = 1.4)

        repeat(5) { card = Schedule.reviewed(card, today = 0, lapsed = true) }

        assertEquals(1.3, card.ease, 0.0001)
    }

    @Test
    fun `a card lost five times is a leech, and a leech comes round twice as often`() {
        var card = card(box = Card.LEARNED_BOX)

        repeat(Card.LEECH_LAPSES) { card = Schedule.reviewed(card, today = 0, lapsed = true) }

        assertTrue(card.isLeech)
        assertTrue("a leech is flagged hard, which halves its gap inside a session", card.hard)
    }

    @Test
    fun `a review comes back one right answer short of the first round`() {
        val due = Schedule.due(listOf(card(box = Card.LEARNED_BOX, due = 5)), today = 10)

        assertEquals(Schedule.REVIEW_BOX, due.single().box)
    }

    @Test
    fun `what is not due is not asked`() {
        val cards = listOf(card(box = Card.LEARNED_BOX, due = 20), card(box = 3))

        val due = Schedule.due(cards, today = 10)

        assertEquals(1, due.size)
        assertEquals(3, due.single().box)
    }

    @Test
    fun `a set with nothing due says when it comes back`() {
        val resting = listOf(card(box = Card.LEARNED_BOX, due = 20), card(box = Card.LEARNED_BOX, due = 14))

        assertEquals(4L, Schedule.nextDue(resting, today = 10))
        // one card still being learned means there is something to do now, so there is no date
        assertNull(Schedule.nextDue(resting + card(box = 1), today = 10))
    }

    @Test
    fun `the leech list is studied whatever the calendar says`() {
        val leech = card(box = Card.LEARNED_BOX, due = 999, lapses = Card.LEECH_LAPSES)

        val forReview = Schedule.forReview(listOf(leech))

        assertEquals(Schedule.REVIEW_BOX, forReview.single().box)
    }
}

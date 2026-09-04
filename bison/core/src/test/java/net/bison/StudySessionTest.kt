package net.bison

import net.bison.domain.StudySession
import net.bison.model.Card
import net.bison.model.CardMode
import net.bison.model.CodeTask
import net.bison.model.Question
import net.bison.model.strengthOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [StudySession] */
class StudySessionTest {
    private fun deck(
        size: Int,
        box: Int = 0,
    ) = (1..size).map {
        Card(
            Question(prompt = "Frage $it", answers = listOf("eins", "zwei"), correctIndex = 0),
            box = box,
        )
    }

    @Test
    fun `eight correct answers in a row learn a question`() {
        val session = StudySession(deck(1))

        repeat(Card.LEARNED_BOX - 1) {
            session.answer(correct = true)
            assertEquals(0, session.learnedCount)
        }
        session.answer(correct = true)

        assertEquals(1, session.learnedCount)
        assertTrue(session.isFinished)
    }

    @Test
    fun `a wrong answer halves the progress rather than clearing it`() {
        val session = StudySession(deck(1))
        repeat(6) { session.answer(correct = true) }

        val card = session.answer(correct = false)

        assertEquals(3, card?.box)
    }

    @Test
    fun `halving rounds down, and one box away from nothing is nothing`() {
        val session = StudySession(deck(1))
        session.answer(correct = true)

        assertEquals(0, session.answer(correct = false)?.box)
    }

    @Test
    fun `a wrong answer near the end still leaves most of the work standing`() {
        val session = StudySession(deck(1, box = 7))

        assertEquals(3, session.answer(correct = false)?.box)
    }

    @Test
    fun `progress counts boxes, so every right answer moves it`() {
        val session = StudySession(deck(2))
        val before = session.progress

        session.answer(correct = true)

        assertTrue(session.progress > before)
        // one of two questions on its first box, averaged over both
        assertEquals(strengthOf(1) / 2f, session.progress, 0.0001f)
    }

    @Test
    fun `a question never comes back immediately`() {
        val session = StudySession(deck(8))
        val first = session.current()

        session.answer(correct = true)

        assertNotEquals(first?.task?.prompt, session.current()?.task?.prompt)
    }

    @Test
    fun `other questions come in between before one returns`() {
        val session = StudySession(deck(12))
        val first = requireNotNull(session.current()).task.prompt

        session.answer(correct = true)
        var seenBefore = 0
        while (requireNotNull(session.current()).task.prompt != first) {
            seenBefore++
            session.answer(correct = true)
        }

        assertEquals(StudySession.GAPS[1], seenBefore)
    }

    @Test
    fun `the wait grows as a question gets stronger`() {
        // a whole set on its first box, so every answer here re-queues rather than resting
        val session = StudySession(deck(StudySession.WORKING_SET, box = 1))
        val first = requireNotNull(session.current()).task.prompt

        session.answer(correct = true)
        var seenBefore = 0
        while (requireNotNull(session.current()).task.prompt != first) {
            seenBefore++
            session.answer(correct = true)
        }

        assertEquals(StudySession.GAPS[2], seenBefore)
    }

    @Test
    fun `the rotation caps how far back a question can go`() {
        // the ladder runs past fifty, but only twelve questions are in rotation, so anything
        // beyond that is simply the back of the queue rather than a longer wait
        assertTrue(StudySession.GAPS.last() > StudySession.WORKING_SET)
    }

    @Test
    fun `the wait grows with every box`() {
        assertEquals(Card.LEARNED_BOX, StudySession.GAPS.size)
        assertTrue(StudySession.GAPS.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `undo takes back the last answer`() {
        val session = StudySession(deck(3))
        repeat(2) { session.answer(correct = true) }
        val before = session.progress

        session.answer(correct = true)
        assertTrue(session.undo())

        assertEquals(before, session.progress, 0.0001f)
    }

    @Test
    fun `there is nothing to undo before the first answer`() {
        val session = StudySession(deck(3))

        assertTrue(!session.canUndo)
        assertTrue(!session.undo())
    }

    @Test
    fun `a deck of learned questions is finished from the start`() {
        val session = StudySession(deck(3, box = Card.LEARNED_BOX))

        assertTrue(session.isFinished)
        assertNull(session.current())
        assertEquals(3, session.learnedCount)
    }

    @Test
    fun `the snapshot keeps every question`() {
        val session = StudySession(deck(5))
        repeat(9) { session.answer(correct = true) }

        assertEquals(5, session.snapshot().size)
    }

    @Test
    fun `answering an empty session does nothing`() {
        val session = StudySession(emptyList())

        assertNull(session.answer(correct = true))
    }

    @Test
    fun `a new question comes in every few answers, not only when one is learned`() {
        // more questions than the rotation holds, so there is always something in reserve
        val session = StudySession(deck(60))
        repeat(StudySession.NEW_EVERY) { session.answer(correct = true) }

        // the rotation started as questions 1 to 12, so anything past that is newly mixed in
        val next = mutableSetOf<String>()
        repeat(4) {
            next += requireNotNull(session.current()).task.prompt
            session.answer(correct = true)
        }

        assertTrue("nothing new came in, only $next", next.any { it == "Frage ${StudySession.WORKING_SET + 1}" })
    }

    @Test
    fun `a large set does not stay stuck on the same handful`() {
        val session = StudySession(deck(60))
        val seen = mutableSetOf<String>()

        repeat(60) {
            seen += requireNotNull(session.current()).task.prompt
            session.answer(correct = true)
        }

        assertTrue("only ${seen.size} different questions in 60 answers", seen.size > StudySession.WORKING_SET)
    }

    @Test
    fun `nothing is lost while questions wait in reserve`() {
        val session = StudySession(deck(60))

        repeat(40) { session.answer(correct = true) }

        assertEquals(60, session.snapshot().size)
        assertEquals(60, session.remaining + session.learnedCount)
    }

    @Test
    fun `a question marked hard comes back in half the time`() {
        val session = StudySession(deck(StudySession.WORKING_SET, box = 1))
        session.flag(hard = true)
        val first = requireNotNull(session.current()).task.prompt

        session.answer(correct = true)
        var seenBefore = 0
        while (requireNotNull(session.current()).task.prompt != first) {
            seenBefore++
            session.answer(correct = true)
        }

        assertEquals(StudySession.GAPS[2] / 2, seenBefore)
    }

    @Test
    fun `the first round only asks for four in a row`() {
        val session = StudySession(deck(3))

        assertEquals(1, session.roundNumber)
        assertEquals(4, session.target)

        // take every question to four; the round should then be over, not the set
        repeat(3 * 4) { session.answer(correct = true) }

        assertEquals(2, session.roundNumber)
        assertEquals(6, session.target)
        assertTrue(!session.isFinished)
        assertEquals(0, session.learnedCount)
    }

    @Test
    fun `a whole set at four is already most of the way along`() {
        val session = StudySession(deck(3))

        repeat(3 * 4) { session.answer(correct = true) }

        // the point of the curve: broad beats deep, and the bar says so
        assertEquals(0.60f, session.progress, 0.02f)
    }

    @Test
    fun `no question runs ahead of the round it is in`() {
        val session = StudySession(deck(4))
        val boxes = mutableListOf<Int>()

        repeat(4 * 4) { boxes += requireNotNull(session.answer(correct = true)).box }

        assertEquals(4, boxes.max())
    }

    @Test
    fun `the last round is the one that finishes the set`() {
        val session = StudySession(deck(2))

        repeat(200) { session.answer(correct = true) }

        assertTrue(session.isFinished)
        assertEquals(2, session.learnedCount)
        assertEquals(1f, session.progress, 0.0001f)
    }

    @Test
    fun `a set picked up halfway starts in the round it left off in`() {
        val session = StudySession(deck(3, box = 5))

        assertEquals(2, session.roundNumber)
        assertEquals(6, session.target)
    }

    @Test
    fun `nothing is dropped when a round hands over to the next`() {
        val session = StudySession(deck(20))

        repeat(20 * 4) { session.answer(correct = true) }

        assertEquals(20, session.snapshot().size)
        assertEquals(2, session.roundNumber)
    }

    @Test
    fun `a code card is sorted first and only then written out`() {
        val cards = listOf(Card(CodeTask(prompt = "Schreibe es", solution = "a();\nb();")))
        val session = StudySession(cards)

        assertEquals(CardMode.Sort, requireNotNull(session.current()).mode)

        session.sorted(clean = true)
        session.answer(correct = true)
        session.sorted(clean = true)

        assertEquals(CardMode.Write, requireNotNull(session.current()).mode)
    }

    @Test
    fun `a muddled sort puts the promotion back to the start`() {
        val cards = listOf(Card(CodeTask(prompt = "Schreibe es", solution = "a();\nb();")))
        val session = StudySession(cards)

        session.sorted(clean = true)
        session.answer(correct = true)
        session.sorted(clean = false)

        assertEquals(0, requireNotNull(session.current()).sorted)
        assertEquals(CardMode.Sort, requireNotNull(session.current()).mode)
    }

    @Test
    fun `a question finished cleanly is dated further out than one that slipped`() {
        val clean = StudySession(deck(1), today = 100)
        repeat(Card.LEARNED_BOX) { clean.answer(correct = true) }

        val slipped = StudySession(deck(1), today = 100)
        slipped.answer(correct = false)
        while (slipped.current() != null) slipped.answer(correct = true)

        val cleanly = clean.snapshot().single()
        val badly = slipped.snapshot().single()
        assertEquals(0, cleanly.lapses)
        assertEquals(1, badly.lapses)
        assertTrue(cleanly.ease > badly.ease)
        assertEquals(101L, cleanly.due)
    }

    @Test
    fun `the slip that counts is anywhere in the session, not just the last answer`() {
        val session = StudySession(deck(1), today = 0)

        session.answer(correct = false)
        while (session.current() != null) session.answer(correct = true)

        // it ended on a run of right answers, and it is still a card that went wrong today
        assertEquals(1, session.snapshot().single().lapses)
    }

    @Test
    fun `the time on a question is added to it, and one question cannot add an hour`() {
        val session = StudySession(deck(1), today = 0)

        session.answer(correct = true, seconds = 30)
        session.answer(correct = true, seconds = 99_999)

        assertEquals(30 + StudySession.MAX_SECONDS, session.snapshot().single().seconds)
    }

    @Test
    fun `a one line answer is typed out rather than sorted`() {
        val card = Card(CodeTask(prompt = "Spaltenvektor anlegen", solution = "d = [3;6;2;5;9]"))

        // there is no order to put one line in, and nothing to mark line by line
        assertEquals(CardMode.Type, card.mode)
    }

    @Test
    fun `a multiple choice card is never asked to be sorted`() {
        val session = StudySession(deck(1))

        assertEquals(CardMode.Choose, requireNotNull(session.current()).mode)
    }
}

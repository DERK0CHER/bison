package net.bison.domain

import net.bison.model.Card
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * When a card comes back after it has been learned.
 *
 * Everything else in this app is a session: a set is taken to four right in a row, then six, then
 * eight, and the spacing inside that is measured in questions. That is the right unit for an
 * evening of work and the wrong unit for a term - what is learned tonight is gone in a fortnight
 * unless it is asked again, and asking it again tomorrow is a waste of the evening. So a card
 * that reaches the end of the ladder is given a date.
 *
 * This is SM-2, not FSRS. FSRS is better, and it is better because it fits seventeen parameters
 * to a review history that this app does not have and would take a term to collect; SM-2 needs
 * nothing but the card itself and is a dozen lines. If the history ever exists, the two hook in
 * at the same place - this object, and nowhere else.
 *
 * The unit graded is the session rather than the answer. SM-2 grades one review, and a review
 * here is not one answer: the session asks a card until it has come back right several times in
 * a row. So a card is graded on whether it got through that without a wrong answer.
 */
object Schedule {
    /** Today, as a day counted from 1970 */
    fun today(): Long = LocalDate.now().toEpochDay()

    /**
     * The cards worth studying now: everything still being learned, and the reviews that have
     * come round.
     *
     * A card that comes back does not start from nothing - it was known once - but it does not
     * start from finished either, or the session would never ask it. It comes back at
     * [REVIEW_BOX], which puts it in the first round with one right answer to go, so it is seen
     * early in the session and then carried through the rest of it like everything else.
     */
    fun due(
        cards: List<Card>,
        today: Long = today(),
    ): List<Card> = forReview(cards.filter { it.isDue(today) })

    /**
     * The same cards, with the finished ones brought back to where a review starts.
     *
     * Used on its own for the list of cards that keep going wrong, which is worth studying
     * whatever the calendar says.
     */
    fun forReview(cards: List<Card>): List<Card> = cards.map { if (it.isLearned) it.copy(box = REVIEW_BOX) else it }

    /**
     * A card that has just been finished in a session, with its next date worked out.
     *
     * The first interval is a day and the second six, which is SM-2's own answer and a good one:
     * the day after is where most of the forgetting happens. After that the interval is
     * multiplied by the card's ease, which grows a little every time it is remembered and drops
     * twice as fast every time it is not.
     *
     * A card that was lost goes back to a day. Not to nothing: the ease it has earned is worth
     * keeping, and the interval is where the punishment belongs.
     */
    fun reviewed(
        card: Card,
        today: Long = today(),
        lapsed: Boolean,
    ): Card {
        val ease =
            if (lapsed) {
                (card.ease - LAPSE_STEP).coerceAtLeast(MIN_EASE)
            } else {
                (card.ease + RIGHT_STEP).coerceAtMost(MAX_EASE)
            }
        val interval =
            when {
                lapsed -> FIRST
                card.interval < FIRST -> FIRST
                card.interval < SECOND -> SECOND
                else -> (card.interval * ease).roundToInt().coerceAtMost(MAX_INTERVAL)
            }
        val lapses = card.lapses + if (lapsed) 1 else 0
        return card.copy(
            ease = ease,
            interval = interval,
            due = today + interval,
            lapses = lapses,
            // a leech is flagged as hard as well, so it comes round twice as often inside a
            // session for as long as it is still in one
            hard = card.hard || lapses >= Card.LEECH_LAPSES,
        )
    }

    /** How far off the next card is, in days, or null when something is due now */
    fun nextDue(
        cards: List<Card>,
        today: Long = today(),
    ): Long? {
        if (cards.any { it.isDue(today) }) return null
        return cards.minOfOrNull { it.due - today }?.coerceAtLeast(0)
    }

    /** Where a review comes back in: one right answer short of the first round's target */
    const val REVIEW_BOX = 3

    /** The day after, which is where most of the forgetting happens */
    const val FIRST = 1

    /** SM-2's second interval, and the one place where a fixed number beats a multiplication */
    const val SECOND = 6

    private const val RIGHT_STEP = 0.1

    private const val LAPSE_STEP = 0.2

    private const val MIN_EASE = 1.3

    private const val MAX_EASE = 2.8

    /** Half a year. Past that the interval says more about the app than about the learner. */
    private const val MAX_INTERVAL = 180
}

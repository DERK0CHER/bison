package net.bison.domain

import net.bison.model.Attempt
import net.bison.model.Card
import net.bison.model.progressOf

/**
 * The study rotation.
 *
 * A set is learned in rounds. The first round takes every question to four right in a row, the
 * second to six, the last to eight - so the whole set gets roughly known before anything gets
 * made safe. Drilling one question to eight while forty others are untouched is the slowest way
 * to learn a set, and it is what happens without rounds.
 *
 * A wrong answer halves a question's count. What keeps the rounds bearable is the spacing: a
 * question that just came up is put back far enough down the queue that other questions are seen
 * before it returns, and the further along a question is, the further back it goes.
 *
 * Only [WORKING_SET] questions are in that rotation at a time. Drilling a whole set at once
 * teaches nothing - the gaps are meaningless when there are two hundred questions between every
 * repeat - so the rest wait in reserve and are mixed in as the session goes.
 *
 * This class holds no Android types, so the whole rotation is unit testable.
 */
class StudySession(
    cards: List<Card>,
    /** The day the session is happening on, which is what a finished card is dated from */
    private val today: Long = Schedule.today(),
) {
    /** In rotation now */
    private val queue: ArrayDeque<Card> = ArrayDeque()

    /** Below this round's target, waiting to be mixed in */
    private val reserve: ArrayDeque<Card> = ArrayDeque()

    /** At this round's target, waiting for the next round to ask for more */
    private val resting: MutableList<Card> = mutableListOf()

    private val learned: MutableList<Card> = mutableListOf()

    private var round = 0

    /** Answers given since the last new question came in */
    private var sinceNew = 0

    /**
     * The questions that have gone wrong at least once today, by identity.
     *
     * What a card's next date is worked out from. SM-2 grades a review, and a review here is the
     * whole session rather than one answer - so what counts is whether the card got all the way
     * back to the end of the ladder without a slip, not what happened on the last question.
     */
    private val slipped = mutableSetOf<String>()

    /** Everything as it was before the last answer, for [undo] */
    private var previous: State? = null

    private data class State(
        val queue: List<Card>,
        val reserve: List<Card>,
        val resting: List<Card>,
        val learned: List<Card>,
        val round: Int,
        val sinceNew: Int,
        val slipped: Set<String>,
    )

    init {
        val pending = mutableListOf<Card>()
        for (card in cards) {
            if (card.isLearned) learned += card else pending += card
        }
        // pick up where the set left off: the first round that still has something below it
        round = ROUNDS.indexOfFirst { target -> pending.any { it.box < target } }.coerceAtLeast(0)
        for (card in pending) {
            if (card.box < target) reserve.addLast(card) else resting += card
        }
        topUp()
    }

    /** What this round asks of every question: this many right answers in a row */
    val target: Int get() = ROUNDS[round]

    /** Which round is being worked, counting from one */
    val roundNumber: Int get() = round + 1

    /** Questions not yet learned at all, in any pile */
    val remaining: Int get() = queue.size + reserve.size + resting.size

    /** Questions this round still has to reach its target */
    val remainingThisRound: Int get() = queue.size + reserve.size

    val learnedCount: Int get() = learned.size

    /** The total this session started from, so progress can be shown as "done of total" */
    val total: Int = cards.size

    val isFinished: Boolean get() = queue.isEmpty() && reserve.isEmpty() && resting.isEmpty()

    /**
     * How much of the work is done, 0f..1f.
     *
     * Counts what each question is worth rather than how many are finished: a set of forty needs
     * three hundred and twenty right answers, so "questions learned" sits at zero for a long
     * while and tells the learner nothing. It also runs on a curve, so a whole set taken to four
     * in a row already reads as sixty per cent.
     */
    val progress: Float get() = if (total == 0) 1f else progressOf(snapshot())

    /** Whether the last answer can still be taken back */
    val canUndo: Boolean get() = previous != null

    /** The question to ask now, or `null` once everything is learned */
    fun current(): Card? = queue.firstOrNull()

    /**
     * Records an answer for the current question and moves it along.
     *
     * @return the card in its new state, or `null` if there was nothing to answer
     */
    fun answer(
        correct: Boolean,
        seconds: Long = 0,
        attempt: Attempt? = null,
    ): Card? {
        val card = queue.removeFirstOrNull() ?: return null
        previous = state().copy(queue = listOf(card) + queue.toList())
        if (!correct) slipped += card.task.identity
        val updated =
            card
                .answered(correct)
                .copy(
                    seconds = card.seconds + seconds.coerceIn(0, MAX_SECONDS),
                    // written down as it happened, in full: the history is the only record of
                    // how the studying went, and it is what the export hands back
                    history = attempt?.let { card.history + it } ?: card.history,
                )
        when {
            // finished for good: this is where it gets its next date, from how the whole
            // session went rather than from this one answer
            updated.isLearned -> learned += Schedule.reviewed(updated, today, lapsed = card.task.identity in slipped)
            // done for this round; the next one will ask it again for more
            updated.box >= target -> resting += updated
            else -> queue.add(gapFor(updated).coerceAtMost(queue.size), updated)
        }
        sinceNew++
        mixInNew()
        topUp()
        openNextRound()
        return updated
    }

    /**
     * Records how a code card's sort went, before the answer itself is recorded.
     *
     * A clean sort counts towards the promotion to writing the code out; a muddled one resets
     * the count, because the point of the promotion is that the order is genuinely known and not
     * that it was stumbled into twice.
     */
    fun sorted(clean: Boolean): Card? {
        val card = queue.removeFirstOrNull() ?: return null
        val updated = card.copy(sorted = if (clean) card.sorted + 1 else 0)
        queue.addFirst(updated)
        return updated
    }

    /** Marks the current question as one the learner keeps getting wrong, or unmarks it */
    fun flag(hard: Boolean): Card? {
        val card = queue.removeFirstOrNull() ?: return null
        val updated = card.copy(hard = hard)
        queue.addFirst(updated)
        return updated
    }

    /**
     * Takes back the last answer, for the mis-tap that every list of buttons produces.
     *
     * @return true if there was something to take back
     */
    fun undo(): Boolean {
        val before = previous ?: return false
        queue.clear()
        queue.addAll(before.queue)
        reserve.clear()
        reserve.addAll(before.reserve)
        resting.clear()
        resting.addAll(before.resting)
        learned.clear()
        learned.addAll(before.learned)
        round = before.round
        sinceNew = before.sinceNew
        slipped.clear()
        slipped.addAll(before.slipped)
        previous = null
        return true
    }

    /** Everything the session knows about, for writing back to storage */
    fun snapshot(): List<Card> = queue.toList() + reserve.toList() + resting + learned

    private fun state() =
        State(queue.toList(), reserve.toList(), resting.toList(), learned.toList(), round, sinceNew, slipped.toSet())

    /**
     * Starts the next round once this one has nothing left to ask.
     *
     * Everything resting that the new target has not reached goes back into the rotation, in the
     * order it settled - which is roughly weakest first, since a question that needed more
     * attempts arrived later.
     */
    private fun openNextRound() {
        while (queue.isEmpty() && reserve.isEmpty() && resting.isNotEmpty() && round < ROUNDS.lastIndex) {
            round++
            // partitioned rather than removeAll: that removes by equality, so two questions
            // that happen to match would both go and only one would come back
            val (asked, stay) = resting.partition { it.box < target }
            resting.clear()
            resting.addAll(stay)
            asked.forEach { reserve.addLast(it) }
            sinceNew = 0
            topUp()
        }
    }

    /** Keeps the rotation full while there is anything left in reserve */
    private fun topUp() {
        while (reserve.isNotEmpty() && queue.size < WORKING_SET) {
            queue.addLast(reserve.removeFirst())
        }
    }

    /**
     * Brings a new question in every so often, whether or not the rotation has room.
     *
     * Without this a session is the same handful of questions until one of them is finally
     * learned, which is what makes a large set feel like a short one on repeat.
     */
    private fun mixInNew() {
        if (reserve.isEmpty() || sinceNew < NEW_EVERY) return
        queue.add(NEW_POSITION.coerceAtMost(queue.size), reserve.removeFirst())
        sinceNew = 0
    }

    /**
     * How many other questions should come before this one returns.
     *
     * The gap grows with the box, so a question you are unsure about comes back soon and one you
     * have nearly learned waits a long time. One marked hard comes back twice as often as its
     * box would say. It is never zero: the same question twice running tests nothing but short
     * term memory.
     *
     * The rotation holds [WORKING_SET], so the caller caps anything longer at the back of the
     * queue. The far end of the ladder is therefore a statement of intent rather than a count.
     */
    private fun gapFor(card: Card): Int {
        val gap = GAPS.getOrElse(card.box) { GAPS.last() }
        return if (card.hard) (gap / 2).coerceAtLeast(GAPS.first()) else gap
    }

    companion object {
        /**
         * Questions to place in front of a returning question, indexed by its box.
         *
         * One entry per box, so the wait grows all the way to the last one. Four right in a row
         * is the point where a question stops being shaky, so from there it goes a full twenty
         * back rather than the eight it used to.
         */
        val GAPS = listOf(2, 4, 8, 13, 20, 28, 38, 50)

        /**
         * What each round asks for, as right answers in a row.
         *
         * Four is where a question stops being guesswork, six where it is reliable, eight where
         * it is safe. Taking the whole set to four first and only then coming back for six is
         * far faster than finishing questions one at a time, and it is what the progress curve
         * rewards: a set all at four is already sixty per cent learned.
         */
        val ROUNDS = listOf(4, 6, Card.LEARNED_BOX)

        /** Questions in the rotation at once. More than this and the gaps stop meaning anything. */
        const val WORKING_SET = 12

        /** Answers between one new question coming in and the next */
        const val NEW_EVERY = 4

        /** Where a new question lands: soon, but not under a finger that is already moving */
        const val NEW_POSITION = 1

        /**
         * The most one question may add to the clock.
         *
         * A card left open while the phone is put down would otherwise say a learner spent two
         * hours on one question, and the time per part is only worth showing if it means
         * something.
         */
        const val MAX_SECONDS = 300L
    }
}

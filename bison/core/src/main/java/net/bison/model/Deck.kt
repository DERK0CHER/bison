package net.bison.model

import kotlin.math.ln

/**
 * A question together with how well it is known.
 *
 * [box] counts correct answers in a row. A wrong answer halves it rather than clearing it:
 * getting one question wrong on the eighth pass should not throw away seven passes of work, and
 * a learner who is nearly there stays nearly there.
 */
data class Card(
    val task: Task,
    val box: Int = 0,
    /** Marked by the learner as one they keep getting wrong; it then comes back twice as often */
    val hard: Boolean = false,
    /**
     * How many times a code task's lines have been put in the right order with nothing out of
     * place. Sorting is the easier half of writing code, so a card starts there and moves up to
     * writing it out once it has been sorted cleanly [SORTS_TO_WRITE] times.
     */
    val sorted: Int = 0,
    /**
     * The day this card is worth asking again, counted from 1970. Zero means as soon as you
     * like, which is where every card starts and where one that is still being learned stays.
     */
    val due: Long = 0,
    /** Days between this card being finished and being asked again */
    val interval: Int = 0,
    /** How kindly the interval grows. Higher is easier; this is SM-2's E-factor. */
    val ease: Double = EASE_START,
    /** How often it has been learned and then got wrong again */
    val lapses: Int = 0,
    /** Seconds spent on this card, all sessions together */
    val seconds: Long = 0,
    /**
     * Every answer ever given to this card, oldest first.
     *
     * Kept whole rather than summarised: the counts can be worked out from it at any time, and
     * nothing can be worked out from the counts. It is what the export writes and what a
     * re-import puts back.
     */
    val history: List<Attempt> = emptyList(),
) {
    /** The times this card was answered, newest first, for the ones that are timed */
    val times: List<Long> get() = history.map { it.seconds }.reversed()

    /** The quickest it has ever been done */
    val best: Long? get() = history.filter { it.seconds > 0 }.minOfOrNull { it.seconds }

    /** How it went last */
    val last: Attempt? get() = history.lastOrNull()

    /** Where this card stands, in the words the export uses */
    val status: String get() = statusOf(history)

    /** How this card should be asked now */
    val mode: CardMode
        get() =
            when {
                task is StudyCard ->
                    when {
                        task.kind == CardKind.Zeit -> CardMode.Timed
                        task.isTyped -> CardMode.Answer
                        else -> CardMode.Flip
                    }

                task is GeneratedTask -> CardMode.Generate
                task is SketchTask -> CardMode.Reveal
                task !is CodeTask -> CardMode.Choose
                // one line has no order to put it in and nothing to mark line by line: the
                // editor, the diff and the three marks are all there to handle a function, and
                // none of them earns its place for `d = [3;6;2;5;9]`
                task.isOneLiner -> CardMode.Type
                sorted >= SORTS_TO_WRITE -> CardMode.Write
                else -> CardMode.Sort
            }

    /** Where this question sits on the run from not known to known, 0f..1f */
    val strength: Float get() = strengthOf(box)

    val isLearned: Boolean get() = box >= LEARNED_BOX

    /**
     * A card that keeps being learned and then lost again.
     *
     * Anki calls these leeches. The point of naming them is that they are not a scheduling
     * problem: a card that has gone five times is usually a badly written card, or two facts
     * pretending to be one, and no interval will fix either.
     */
    val isLeech: Boolean get() = lapses >= LEECH_LAPSES

    /** Whether this card is worth asking on [today], counted in days from 1970 */
    fun isDue(today: Long): Boolean = !isLearned || due <= today

    /** One box up on a right answer; half the way back on a wrong one */
    fun answered(correct: Boolean): Card = copy(box = if (correct) (box + 1).coerceAtMost(LEARNED_BOX) else box / 2)

    companion object {
        /** Correct answers in a row before a question drops out of the rotation */
        const val LEARNED_BOX = 8

        /** Clean sorts before a code task stops being sorted and has to be written out */
        const val SORTS_TO_WRITE = 2

        /** Where SM-2 starts a card's ease, and what a new card carries */
        const val EASE_START = 2.5

        /** Times a card may be lost again before it is called a leech */
        const val LEECH_LAPSES = 5
    }
}

/** How a card is being asked at the moment */
enum class CardMode {
    /** Pick one of several answers */
    Choose,

    /** Drag the model answer's lines into order */
    Sort,

    /** Type it out and mark it yourself */
    Write,

    /** Type the single line it is; right or wrong is decided by comparing the two */
    Type,

    /** Work out the sum the card just made up */
    Generate,

    /** Answer it on paper, then look at the answer and say whether you had it */
    Reveal,

    /** Turn it over: the reasoning first, the answer on the second tap */
    Flip,

    /** Write the answer out and have it compared */
    Answer,

    /** Solved on paper against a clock */
    Timed,
}

/**
 * How much one question is worth, 0f..1f, on a curve rather than a straight line.
 *
 * Counted straight, four right in a row is worth exactly half of eight - which is not how it
 * feels and not how it works. The first few passes are where a question goes from unknown to
 * roughly known; the last few only make it safe. So the curve is steep at the start and flat at
 * the end: four in a row reads as 60 %, six as 82 %, and the last two carry the remaining 18 %.
 *
 * That also means getting a whole set to four is worth far more than getting a handful to eight,
 * which is exactly the order the rounds ask for.
 */
fun strengthOf(box: Int): Float {
    val within = box.coerceIn(0, Card.LEARNED_BOX).toDouble() / Card.LEARNED_BOX
    return (ln(1 + CURVE * within) / ln(1 + CURVE)).toFloat()
}

/** How sharply the curve bends. Higher is steeper at the start; 1.25 puts four in a row at 60 %. */
private const val CURVE = 1.25

/**
 * How far a set of questions has come, 0f..1f.
 *
 * The average of what each question is worth, so it moves on every correct answer rather than
 * standing still until a question is finally done.
 */
fun progressOf(cards: List<Card>): Float {
    if (cards.isEmpty()) return 0f
    return cards.map { strengthOf(it.box) }.average().toFloat()
}

/**
 * One part of a topic, learned and tracked on its own.
 *
 * A driving theory paper is not one subject but a dozen - signs, right of way, first aid - and
 * knowing you are through the signs while the priority rules are still red is the whole point of
 * splitting them. Each part carries its own progress; the topic's is all of them together.
 */
data class Subtopic(
    val id: String,
    val name: String,
    val cards: List<Card>,
) {
    val learnedCount: Int get() = cards.count { it.isLearned }

    val progress: Float get() = progressOf(cards)

    /** How many of these are worth asking today */
    fun dueCount(today: Long): Int = cards.count { it.isDue(today) }

    /** Seconds spent in this part, which is the only honest measure of what it cost */
    val seconds: Long get() = cards.sumOf { it.seconds }
}

/** A named topic, made of one or more subtopics */
data class Deck(
    val id: String,
    val name: String,
    val subtopics: List<Subtopic>,
) {
    val cards: List<Card> get() = subtopics.flatMap { it.cards }

    val learnedCount: Int get() = cards.count { it.isLearned }

    /** How many questions this topic would ask today */
    fun dueCount(today: Long): Int = cards.count { it.isDue(today) }

    /** Seconds spent on this topic, all its parts together */
    val seconds: Long get() = cards.sumOf { it.seconds }

    /**
     * The cards that keep being learned and lost again.
     *
     * Worth a list of their own because they are usually not a scheduling problem: a card that
     * has gone five times is a badly written card, or two facts pretending to be one.
     */
    val leeches: List<Card> get() = cards.filter { it.isLeech }

    /**
     * Every label anything in this topic carries, in the order they first turn up.
     *
     * Not sorted: the order a set was written in says more than the alphabet does, and a term
     * that appears on the first card is the one to reach for first.
     */
    val tags: List<String> get() = cards.flatMap { it.task.tags }.distinct()

    /**
     * The cards carrying all of [tags], for studying one corner of a topic.
     *
     * Every one of them rather than any: the labels are of different kinds - the exam a question
     * came from, the sort of exercise it is - and picking one of each is how "the Node_Delete
     * variants from WS24" gets asked for. Picking two of the same kind narrows to nothing, which
     * the screen says by counting what is left before anything is started.
     */
    fun cardsTagged(tags: Set<String>): List<Card> {
        if (tags.isEmpty()) return cards
        return cards.filter { card -> tags.all { it in card.task.tags } }
    }

    /**
     * Whether there is anything to pick before studying: more than one part, or any tag.
     *
     * A topic with one part and no labels has nothing to ask, so it is opened and started rather
     * than shown a screen holding a single row and one button.
     */
    val hasChoices: Boolean get() = subtopics.size > 1 || tags.isNotEmpty()

    /**
     * The topic's progress, which is its subtopics' progress put together.
     *
     * Counted over every card rather than averaged over the subtopics, so a part with forty
     * questions weighs forty times as much as a part with one. Averaging the bars would let a
     * tiny finished part make a large unfinished one look done.
     */
    val progress: Float get() = progressOf(cards)

    /** Replaces the cards of one subtopic, leaving the rest of the topic alone */
    fun withCards(
        subtopicId: String,
        cards: List<Card>,
    ): Deck = copy(subtopics = subtopics.map { if (it.id == subtopicId) it.copy(cards = cards) else it })

    /**
     * Spreads a studied set back over the subtopics it came from.
     *
     * Studying a whole topic mixes every part together, so what comes back is one list; each card
     * is matched to its part by the question it carries.
     */
    fun withMixedCards(cards: List<Card>): Deck {
        // Each studied card is handed out once. Matching with a plain map keyed on the question
        // would keep only the last of any repeated one, so a question that appears in two parts
        // would get the same result written into both and one part's work would be lost.
        val waiting = cards.groupBy { it.task.identity }.mapValues { ArrayDeque(it.value) }
        return copy(
            subtopics =
                subtopics.map { subtopic ->
                    subtopic.copy(
                        cards = subtopic.cards.map { waiting[it.task.identity]?.removeFirstOrNull() ?: it },
                    )
                },
        )
    }
}

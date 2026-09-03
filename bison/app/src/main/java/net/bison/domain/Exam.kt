package net.bison.domain

import net.bison.model.Card
import net.bison.model.CodeTask
import net.bison.model.Deck
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.SketchTask
import kotlin.random.Random

/**
 * How a paper is put together: how many questions out of each part, and how long there is.
 *
 * The counts are the point of the whole mode. A real paper is not a fair sample of a topic - it
 * is twenty-five multiple choice, four bits of theory, five programming exercises and twenty
 * MATLAB - and sitting a mock that is weighted like the set rather than like the paper trains
 * the wrong thing.
 */
data class ExamPlan(
    val counts: Map<String, Int>,
    val minutes: Int = DEFAULT_MINUTES,
) {
    val total: Int get() = counts.values.sum()

    companion object {
        /** The length of the paper this was built for */
        const val DEFAULT_MINUTES = 120

        /** Every question there is, as a starting point to cut down from */
        fun everything(deck: Deck): ExamPlan = ExamPlan(deck.subtopics.associate { it.id to it.cards.size })
    }
}

/**
 * One question as it stands on the paper, with whatever was written for it.
 *
 * The shuffled order of a multiple choice question and the numbers a generated one came up with
 * are fixed here when the paper is drawn. Under a study session they are drawn afresh every
 * round, which is right there and wrong here: a paper you can leaf back through must say the
 * same thing the second time you look at it.
 */
data class ExamItem(
    val block: String,
    val card: Card,
    val order: List<Int> = emptyList(),
    val rolled: Rolled? = null,
    /** Which box was ticked, as a position in [order] */
    val picked: Int? = null,
    val written: String? = null,
    /** What the reader gave it while marking; null until they have */
    val awarded: Double? = null,
) {
    val task get() = card.task

    /**
     * What the question is worth.
     *
     * A written function is worth one point per line of the model answer, which is how the
     * marking already counts it. Everything else is worth one, which is a guess - a paper that
     * weights its multiple choice differently would need this to come from the plan.
     */
    val maxPoints: Double
        get() =
            when (val asked = task) {
                is CodeTask -> if (asked.isOneLiner) 1.0 else asked.solutionLines.size.toDouble()
                else -> 1.0
            }

    /** Whether the app can mark this one, or the reader has to */
    val marksItself: Boolean
        get() =
            when (val asked = task) {
                is Question -> true
                is GeneratedTask -> true
                is CodeTask -> asked.isOneLiner
                is SketchTask -> false
            }

    /** Whether anything was written or ticked at all */
    val attempted: Boolean get() = picked != null || !written.isNullOrBlank()

    /** The points this question has, or null while it is still waiting to be marked by hand */
    val points: Double?
        get() = if (marksItself) (if (right) maxPoints else 0.0) else awarded

    /** Whether an automatically marked question was answered correctly */
    private val right: Boolean
        get() =
            when (val asked = task) {
                is Question -> picked != null && order.getOrNull(picked) == asked.correctIndex
                is GeneratedTask -> rolled?.matches(written.orEmpty()) == true
                is CodeTask -> asked.accepted.any { LineDiff.sameLine(written.orEmpty(), it) }
                is SketchTask -> false
            }
}

/** What one part of the paper came to */
data class ExamBlock(
    val name: String,
    val questions: Int,
    val points: Double,
    val maxPoints: Double,
)

/**
 * A paper being sat.
 *
 * This deliberately goes around [StudySession] rather than through it. A session is a schedule -
 * boxes, rounds, spacing, a question coming back until it sticks - and an exam is the opposite of
 * all of that: a fixed draw, in order, once, with no idea whether anything was right until it is
 * handed in. Nothing here touches a card's box either. Sitting a mock is a measurement, and a
 * measurement that changes what it measures is worth less than no measurement.
 */
class Exam(
    items: List<ExamItem>,
    val minutes: Int,
) {
    private val paper = items.toMutableList()

    val size: Int get() = paper.size

    val items: List<ExamItem> get() = paper.toList()

    fun item(at: Int): ExamItem = paper[at]

    /** How many questions have been written on, for the count on the way to handing in */
    val attempted: Int get() = paper.count { it.attempted }

    fun pick(
        at: Int,
        position: Int,
    ) {
        paper[at] = paper[at].copy(picked = position)
    }

    fun write(
        at: Int,
        text: String,
    ) {
        paper[at] = paper[at].copy(written = text)
    }

    fun award(
        at: Int,
        points: Double,
    ) {
        paper[at] = paper[at].copy(awarded = points)
    }

    /** The questions the app cannot mark, in the order they were sat */
    val byHand: List<Int> get() = paper.indices.filter { !paper[it].marksItself }

    /** Those of them still waiting for the reader */
    val unmarked: List<Int> get() = byHand.filter { paper[it].awarded == null }

    val points: Double get() = paper.sumOf { it.points ?: 0.0 }

    val maxPoints: Double get() = paper.sumOf { it.maxPoints }

    /**
     * What each part came to, in the order the paper is in.
     *
     * The parts are what the reader can act on: a total says the paper went badly, and the
     * blocks say which twenty minutes of revision would have fixed it.
     */
    fun blocks(): List<ExamBlock> {
        val order = paper.map { it.block }.distinct()
        return order.map { name ->
            val mine = paper.filter { it.block == name }
            ExamBlock(
                name = name,
                questions = mine.size,
                points = mine.sumOf { it.points ?: 0.0 },
                maxPoints = mine.sumOf { it.maxPoints },
            )
        }
    }

    /** "37,5 / 54", the way a marked paper says it */
    fun asScore(): String = "${Marking.format(points)} / ${Marking.format(maxPoints)}"
}

/**
 * Draws a paper from a topic.
 *
 * The parts keep the order they have in the topic, and the questions inside a part are drawn at
 * random without repeats. A part that has fewer questions than the plan asks for simply gives
 * what it has - the alternative is refusing to start, which helps nobody the night before.
 */
object ExamDraw {
    fun draw(
        deck: Deck,
        plan: ExamPlan,
        random: Random = Random.Default,
    ): Exam {
        val items = mutableListOf<ExamItem>()
        for (subtopic in deck.subtopics) {
            val wanted = plan.counts[subtopic.id] ?: 0
            if (wanted <= 0) continue
            for (card in subtopic.cards.shuffled(random).take(wanted)) {
                items +=
                    ExamItem(
                        block = subtopic.name,
                        card = card,
                        order = (card.task as? Question)?.answers?.indices?.shuffled(random).orEmpty(),
                        rolled = (card.task as? GeneratedTask)?.let { Generator.roll(it, random) },
                    )
            }
        }
        return Exam(items, plan.minutes)
    }
}

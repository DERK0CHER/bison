package net.bison

import net.bison.domain.ExamDraw
import net.bison.domain.ExamPlan
import net.bison.model.Card
import net.bison.model.CodeTask
import net.bison.model.Deck
import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.Subtopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests the mock paper.
 *
 * The weighting is the whole point of it: a paper is twenty-five multiple choice, twenty MATLAB
 * and five programming exercises, and a mock weighted like the card set instead trains the wrong
 * thing. So most of what is checked here is that the draw does what the plan says.
 */
class ExamTest {
    private fun question(prompt: String) = Question(prompt, listOf("a", "b", "c"), correctIndex = 0)

    private fun deck() =
        Deck(
            id = "d",
            name = "Klausur",
            subtopics =
                listOf(
                    Subtopic("sc", "Single Choice", (1..30).map { Card(question("sc$it")) }),
                    Subtopic("mat", "MATLAB", (1..25).map { Card(CodeTask(prompt = "m$it", solution = "d = [$it]")) }),
                    Subtopic("prog", "Programmieren", (1..6).map { Card(CodeTask(prompt = "p$it", solution = "a();\nb();")) }),
                ),
        )

    @Test
    fun `the draw follows the plan, part by part`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("sc" to 25, "mat" to 20, "prog" to 5)), Random(1))

        assertEquals(50, exam.size)
        assertEquals(listOf("Single Choice", "MATLAB", "Programmieren"), exam.blocks().map { it.name })
        assertEquals(listOf(25, 20, 5), exam.blocks().map { it.questions })
    }

    @Test
    fun `a part is drawn without asking the same question twice`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("sc" to 30)), Random(2))

        assertEquals(30, exam.items.map { it.task.prompt }.distinct().size)
    }

    @Test
    fun `a part with fewer questions than the plan wants gives what it has`() {
        // refusing to start would help nobody the night before
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("prog" to 99)), Random(3))

        assertEquals(6, exam.size)
    }

    @Test
    fun `a part the plan does not name is not on the paper`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("mat" to 4)), Random(4))

        assertEquals(4, exam.size)
        assertEquals(listOf("MATLAB"), exam.blocks().map { it.name })
    }

    @Test
    fun `a ticked box is marked by the app`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("sc" to 1)), Random(5))
        // the options are shuffled once when the paper is drawn, so the right box is wherever
        // the first answer ended up
        val right = exam.item(0).order.indexOf(0)

        exam.pick(0, right)
        assertEquals(1.0, exam.points, 0.0001)

        exam.pick(0, (right + 1) % 3)
        assertEquals(0.0, exam.points, 0.0001)
    }

    @Test
    fun `a written function waits for the reader and is worth a point a line`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("prog" to 1)), Random(6))

        assertEquals(listOf(0), exam.byHand)
        assertEquals(listOf(0), exam.unmarked)
        assertEquals(2.0, exam.item(0).maxPoints, 0.0001)

        exam.award(0, 1.5)

        assertEquals(1.5, exam.points, 0.0001)
        assertTrue(exam.unmarked.isEmpty())
    }

    @Test
    fun `a one line answer is marked by the app, spacing and all`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("mat" to 1)), Random(7))
        val solution = (exam.item(0).task as CodeTask).solution

        exam.write(0, solution.replace(" ", ""))

        assertTrue(exam.byHand.isEmpty())
        assertEquals(1.0, exam.points, 0.0001)
    }

    @Test
    fun `a generated question keeps the numbers it was drawn with`() {
        val numbers =
            Deck(
                id = "g",
                name = "Zahlen",
                subtopics =
                    listOf(
                        Subtopic(
                            "z",
                            "Zahlensysteme",
                            listOf(Card(GeneratedTask(kind = GenKind.Convert, from = 2, to = 16, bits = 8))),
                        ),
                    ),
            )
        val exam = ExamDraw.draw(numbers, ExamPlan(mapOf("z" to 1)), Random(8))

        // a paper you can leaf back through has to say the same thing the second time
        val rolled = requireNotNull(exam.item(0).rolled)
        assertEquals(rolled, exam.item(0).rolled)

        exam.write(0, rolled.answer)
        assertEquals(1.0, exam.points, 0.0001)
    }

    @Test
    fun `an empty paper still adds up`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("sc" to 3)), Random(9))

        assertEquals(0, exam.attempted)
        assertEquals(0.0, exam.points, 0.0001)
        assertEquals(3.0, exam.maxPoints, 0.0001)
        assertEquals("0 / 3", exam.asScore())
    }

    @Test
    fun `the blocks say which part the marks were lost in`() {
        val exam = ExamDraw.draw(deck(), ExamPlan(mapOf("sc" to 2, "prog" to 1)), Random(10))

        exam.pick(0, exam.item(0).order.indexOf(0))
        exam.award(2, 2.0)

        val blocks = exam.blocks()
        assertEquals(1.0, blocks[0].points, 0.0001)
        assertEquals(2.0, blocks[0].maxPoints, 0.0001)
        assertEquals(2.0, blocks[1].points, 0.0001)
        assertEquals(2.0, blocks[1].maxPoints, 0.0001)
    }
}

package net.bison

import net.bison.domain.LineDiff
import net.bison.domain.LineMark
import net.bison.domain.Marking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the exam arithmetic on a marked attempt */
class MarkingTest {
    @Test
    fun `a slip of syntax costs a quarter and a wrong idea costs a half`() {
        val marking =
            Marking(
                marks = listOf(LineMark.Right, LineMark.Syntax, LineMark.Semantic, LineMark.Right),
                maxPoints = 4,
            )

        assertEquals(0.75, marking.deducted, 0.0001)
        assertEquals(3.25, marking.points, 0.0001)
        assertEquals("3,25 / 4", marking.asScore())
    }

    @Test
    fun `a clean attempt scores everything and reads as a whole number`() {
        val marking = Marking(marks = List(5) { LineMark.Right }, maxPoints = 5)

        assertTrue(marking.clean)
        assertEquals("5 / 5", marking.asScore())
    }

    @Test
    fun `one deduction anywhere is enough to not be clean`() {
        val marking = Marking(marks = List(9) { LineMark.Right } + LineMark.Syntax, maxPoints = 10)

        assertTrue(!marking.clean)
        assertEquals(9.75, marking.points, 0.0001)
    }

    @Test
    fun `points never fall below zero`() {
        val marking = Marking(marks = List(8) { LineMark.Semantic }, maxPoints = 2)

        assertEquals(0.0, marking.points, 0.0001)
    }

    @Test
    fun `a fresh marking passes the matching lines and questions the rest`() {
        val rows = LineDiff.compare(listOf("a();", "b();"), listOf("a();", "c();"))

        val marking = Marking.from(rows)

        // a();  matches, b(); is extra, c(); is missing
        assertEquals(
            listOf(LineMark.Right, LineMark.Semantic, LineMark.Semantic),
            marking.marks,
        )
        assertTrue(!marking.clean)
    }

    @Test
    fun `a line typed that is not in the model answer does not add a point to earn`() {
        // three rows, but only the two from the model answer are worth anything
        val rows = LineDiff.compare(listOf("a();", "b();"), listOf("a();", "c();"))

        assertEquals(2, Marking.from(rows).maxPoints)
    }

    @Test
    fun `a correct answer needs no tapping at all`() {
        val rows = LineDiff.compare(listOf("a();", "b();"), listOf("a();", "b();"))

        assertTrue(Marking.from(rows).clean)
        assertEquals("2 / 2", Marking.from(rows).asScore())
    }
}

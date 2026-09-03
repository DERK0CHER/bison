package net.bison

import net.bison.importer.CardFileParser
import net.bison.importer.CardImport
import net.bison.model.BitOp
import net.bison.model.CodeTask
import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.SketchTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the format cards are written in at a desk.
 *
 * Robolectric only for the dispatcher, which falls back to the JSON parser and therefore needs a
 * real org.json.
 */
@RunWith(RobolectricTestRunner::class)
class CardFileParserTest {
    private val nodeDelete =
        """
        type: code
        topic: Verkettete Listen
        tags: WS24, Node_Delete
        front:
        ```c
        void node_delete(node_t *n) {
        >>> Hier fehlt was
        }
        ```
        back:
        ```c
            free(n->data);
            free(n);
        ```
        """.trimIndent()

    @Test
    fun `a code card keeps its block exactly as written`() {
        val task = CardFileParser.parse(nodeDelete).tasks.single() as CodeTask

        // the front is a fenced block and nothing else, so it is all code and no prose
        assertEquals("", task.prompt)
        assertTrue(task.given.orEmpty().contains("void node_delete(node_t *n) {"))
        assertTrue(task.given.orEmpty().contains(CodeTask.GAP))
        assertEquals(listOf("    free(n->data);", "    free(n);"), task.solutionLines)
        assertEquals("Verkettete Listen", task.topic)
        assertEquals(listOf("WS24", "Node_Delete"), task.tags)
    }

    @Test
    fun `a line of prose over a block keeps the prose as the prompt and the block as code`() {
        val text =
            """
            type: code
            front: Vervollständige node_delete
            ```c
            void node_delete(node_t *n) {
            >>> Hier fehlt was
            }
            ```
            back: free(n);
            """.trimIndent()

        val task = CardFileParser.parse(text).tasks.single() as CodeTask

        assertEquals("Vervollständige node_delete", task.prompt)
        assertEquals("void node_delete(node_t *n) {\n${CodeTask.GAP}\n}", task.given)
        assertEquals(listOf("free(n);"), task.solutionLines)
    }

    @Test
    fun `given may be spelled out as its own field`() {
        val text =
            """
            type: code
            front: Was fehlt?
            given: int main(void) {
            back: return 0;
            """.trimIndent()

        val task = CardFileParser.parse(text).tasks.single() as CodeTask

        assertEquals("Was fehlt?", task.prompt)
        assertEquals("int main(void) {", task.given)
    }

    @Test
    fun `a trace question keeps its program apart from its options`() {
        val text =
            """
            type: choice
            front:
            ```c
            int a = 1 << 3;
            printf("%d", a);
            ```
            - 4
            - *8
            - 16
            """.trimIndent()

        val question = CardFileParser.parse(text).tasks.single() as Question

        assertEquals("", question.prompt)
        assertTrue(question.given.orEmpty().contains("""printf("%d", a);"""))
        assertEquals(3, question.answers.size)
        assertEquals("8", question.correctAnswer)
    }

    @Test
    fun `indentation inside a block survives`() {
        val task = CardFileParser.parse(nodeDelete).tasks.single() as CodeTask

        assertTrue("the leading spaces were eaten", task.solutionLines.first().startsWith("    "))
    }

    @Test
    fun `a choice card marks its right answer with a star`() {
        val text =
            """
            type: choice
            front: Was ergibt 1 << 3?
            - 4
            - *8
            - 16
            """.trimIndent()

        val question = CardFileParser.parse(text).tasks.single() as Question

        assertEquals("Was ergibt 1 << 3?", question.prompt)
        assertEquals(listOf("4", "8", "16"), question.answers)
        assertEquals("8", question.correctAnswer)
    }

    @Test
    fun `cards are separated by a lone three dashes`() {
        val text =
            """
            front: Erste?
            - a
            - *b
            ---
            front: Zweite?
            - *c
            - d
            """.trimIndent()

        assertEquals(2, CardFileParser.parse(text).tasks.size)
    }

    @Test
    fun `three dashes inside a block do not split the card`() {
        val text =
            """
            type: code
            front: Trennlinie zeichnen
            back:
            ```c
            printf("---");
            ```
            """.trimIndent()

        val task = CardFileParser.parse(text).tasks.single() as CodeTask
        assertEquals(listOf("""printf("---");"""), task.solutionLines)
    }

    @Test
    fun `alt may be given more than once, for the same thing written differently`() {
        val text =
            """
            type: code
            front: Spaltenvektor anlegen
            back: d = [3 6 2 5 9]'
            alt: d = [3;6;2;5;9]
            alt: d = transpose([3 6 2 5 9])
            """.trimIndent()

        val task = CardFileParser.parse(text).tasks.single() as CodeTask

        assertEquals("d = [3 6 2 5 9]'", task.solution)
        assertEquals(listOf("d = [3;6;2;5;9]", "d = transpose([3 6 2 5 9])"), task.alternatives)
        assertEquals(3, task.accepted.size)
    }

    @Test
    fun `the type may be left out and is worked out from the fields`() {
        val code = CardFileParser.parse("front: Schreibe es\nback: return 0;").tasks.single()
        val choice = CardFileParser.parse("front: Was denn?\n- a\n- *b").tasks.single()

        assertTrue(code is CodeTask)
        assertTrue(choice is Question)
    }

    @Test
    fun `a generated card names the exercise rather than an instance of it`() {
        val text =
            """
            type: gen
            topic: Zahlensysteme
            tags: WS24
            kind: bits
            op: ^
            from: 2
            to: 16
            bits: 8
            """.trimIndent()

        val task = CardFileParser.parse(text).tasks.single() as GeneratedTask

        assertEquals(GenKind.Bits, task.kind)
        assertEquals(BitOp.Xor, task.op)
        assertEquals(2, task.from)
        assertEquals(16, task.to)
        assertEquals(8, task.bits)
        assertEquals("Zahlensysteme", task.topic)
        // it writes its own wording, because there is no one question to write
        assertTrue(task.prompt.isNotBlank())
    }

    @Test
    fun `an operator may be spelled out as well as written`() {
        val task = CardFileParser.parse("type: gen\nkind: bits\nop: xor").tasks.single() as GeneratedTask

        assertEquals(BitOp.Xor, task.op)
    }

    @Test
    fun `a generated card that could not work out its own answer is skipped`() {
        // %s against a number throws rather than printing something odd, so the card never gets
        // as far as the study screen
        val printf = CardFileParser.parse("type: gen\nkind: printf\nop: *\nformat: %s")
        val nameless = CardFileParser.parse("type: gen\nkind: was auch immer")
        val opless = CardFileParser.parse("type: gen\nkind: bits")

        assertEquals(0, printf.tasks.size + nameless.tasks.size + opless.tasks.size)
        assertEquals(3, printf.skipped + nameless.skipped + opless.skipped)
    }

    @Test
    fun `a card whose answer is a picture is one to answer on paper`() {
        val text =
            """
            type: sketch
            topic: UML
            front: Zeichne das Activity Chart zu node_delete
            answerimage: node-delete.png
            """.trimIndent()

        val task = CardFileParser.parse(text).tasks.single() as SketchTask

        assertEquals("node-delete.png", task.answerImage)
        assertEquals("UML", task.topic)
        assertTrue(task.hasAnswer)
    }

    @Test
    fun `the type is worked out from an answer that is a picture or a paragraph`() {
        val drawn = CardFileParser.parse("front: Zeichne es\nanswerimage: x.png").tasks.single()
        val prose = CardFileParser.parse("front: Was ist ein Semaphor?\nanswer: Ein Zähler mit ...").tasks.single()

        assertTrue(drawn is SketchTask)
        assertTrue(prose is SketchTask)
    }

    @Test
    fun `a picture on the front is a field like any other`() {
        val text =
            """
            front: Was tut dieses Diagramm?
            image: chart.png
            - Es löscht einen Knoten
            - *Es fügt einen Knoten ein
            """.trimIndent()

        val question = CardFileParser.parse(text).tasks.single() as Question

        assertEquals("chart.png", question.image)
        assertEquals(2, question.answers.size)
    }

    @Test
    fun `a card that would be turned over onto nothing is skipped`() {
        val found = CardFileParser.parse("type: sketch\nfront: Und dann?")

        assertEquals(0, found.tasks.size)
        assertEquals(1, found.skipped)
    }

    @Test
    fun `a card that is nothing but a picture still has a front`() {
        val task = CardFileParser.parse("image: chart.png\nanswer: Ein Aktivitätsdiagramm").tasks.single()

        assertEquals("chart.png", task.image)
        // the list has to call it something, and the picture is all it has
        assertEquals("chart.png", task.label)
    }

    @Test
    fun `a card without a front is skipped and counted`() {
        val text =
            """
            front: Gut
            - a
            - *b
            ---
            back: einsam
            """.trimIndent()

        val found = CardFileParser.parse(text)

        assertEquals(1, found.tasks.size)
        assertEquals(1, found.skipped)
    }

    @Test
    fun `a choice card with no right answer marked is skipped`() {
        val found = CardFileParser.parse("front: Was?\n- a\n- b")

        assertEquals(0, found.tasks.size)
        assertEquals(1, found.skipped)
    }

    @Test
    fun `the dispatcher recognises a card file and leaves JSON to the other parser`() {
        val cards = CardImport.parse(nodeDelete)
        val json = CardImport.parse("""[{"question": "Q?", "answers": ["a", "b"], "correct": 0}]""")

        assertEquals(CardImport.Format.CardFile, cards.format)
        assertEquals(CardImport.Format.Questions, json.format)
        assertEquals(1, json.tasks.size)
    }

    @Test
    fun `something that looks like a card file but is not falls through to JSON`() {
        // "type:" appears, but there is no card in it - the JSON underneath must still be read
        val text = """Der type: ist egal. [{"question": "Q?", "answers": ["a", "b"], "correct": 1}]"""

        val result = CardImport.parse(text)

        assertEquals(1, result.tasks.size)
        assertEquals(CardImport.Format.Questions, result.format)
    }
}

package net.bison

import net.bison.importer.QuestionParser
import net.bison.importer.QuestionParser.ImportResult
import net.bison.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests [QuestionParser] against the shapes a language model actually produces.
 */
class QuestionParserTest {
    /**
     * The parser hands back the wider [net.bison.model.Task], because a card can also be code.
     * This one only ever makes choice cards, so the answers are reached through a cast.
     */
    private val ImportResult.choices: List<Question> get() = questions.map { it as Question }

    // region JSON, the format the prompt asks for

    @Test
    fun `json with the index of the right answer`() {
        val result =
            QuestionParser.parse(
                """
                [
                  {"question": "Hauptstadt von Frankreich?",
                   "answers": ["Berlin", "Paris", "Rom"],
                   "correct": 1}
                ]
                """.trimIndent(),
            )

        assertEquals(1, result.questions.size)
        assertEquals(0, result.skipped)
        val question = result.choices.single()
        assertEquals("Hauptstadt von Frankreich?", question.prompt)
        assertEquals(listOf("Berlin", "Paris", "Rom"), question.answers)
        assertEquals("Paris", question.correctAnswer)
    }

    @Test
    fun `json where correct holds the answer itself`() {
        val result =
            QuestionParser.parse(
                """[{"question": "Q?", "answers": ["eins", "zwei"], "correct": "zwei"}]""",
            )

        assertEquals(1, result.choices.single().correctIndex)
    }

    @Test
    fun `json wrapped in a code fence`() {
        val result =
            QuestionParser.parse(
                "Hier sind deine Fragen:\n\n```json\n" +
                    """[{"question": "Q?", "answers": ["a", "b"], "correct": 0}]""" +
                    "\n```\nViel Erfolg!",
            )

        assertEquals(1, result.questions.size)
    }

    @Test
    fun `json wrapped in an object`() {
        val result =
            QuestionParser.parse(
                """{"questions": [{"question": "Q?", "answers": ["a", "b"], "correct": 0}]}""",
            )

        assertEquals(1, result.questions.size)
    }

    @Test
    fun `json with prose around it`() {
        val result =
            QuestionParser.parse(
                """Gerne! [{"question": "Q?", "answers": ["a", "b"], "correct": 1}] Sag Bescheid.""",
            )

        assertEquals(1, result.choices.single().correctIndex)
    }

    @Test
    fun `a json entry with an out of range index is skipped, the rest kept`() {
        val result =
            QuestionParser.parse(
                """
                [
                  {"question": "gut", "answers": ["a", "b"], "correct": 0},
                  {"question": "kaputt", "answers": ["a", "b"], "correct": 7}
                ]
                """.trimIndent(),
            )

        assertEquals(1, result.questions.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `more answers than there are pills is skipped`() {
        val result =
            QuestionParser.parse(
                """[{"question": "Q?", "answers": ["a", "b", "c", "d", "e"], "correct": 0}]""",
            )

        assertEquals(0, result.questions.size)
        assertEquals(1, result.skipped)
    }

    // endregion

    // region prose, still accepted when a model ignores the instruction

    @Test
    fun `lettered options with a lettered solution`() {
        val result =
            QuestionParser.parse(
                """
                Was bedeutet ein durchgezogener Mittelstreifen?
                A) Überholen erlaubt
                B) Er darf nicht überfahren werden
                C) Baustelle
                Lösung: B
                """.trimIndent(),
            )

        val question = result.choices.single()
        assertEquals("Was bedeutet ein durchgezogener Mittelstreifen?", question.prompt)
        assertEquals("Er darf nicht überfahren werden", question.correctAnswer)
    }

    @Test
    fun `numbered options and a numbered solution`() {
        val result =
            QuestionParser.parse(
                """
                Frage: Wie hoch ist die Regelgeschwindigkeit innerorts?
                1. 30 km/h
                2. 50 km/h
                3. 60 km/h
                Antwort: 2
                """.trimIndent(),
            )

        assertEquals("50 km/h", result.choices.single().correctAnswer)
    }

    @Test
    fun `several questions separated by blank lines`() {
        val result =
            QuestionParser.parse(
                """
                Erste Frage?
                A) eins
                B) zwei
                Lösung: A

                Zweite Frage?
                A) drei
                B) vier
                Lösung: B
                """.trimIndent(),
            )

        assertEquals(2, result.questions.size)
        assertEquals(0, result.choices[0].correctIndex)
        assertEquals(1, result.choices[1].correctIndex)
    }

    @Test
    fun `a numbered question is not mistaken for an option`() {
        val result =
            QuestionParser.parse(
                """
                1. Was gilt an dieser Kreuzung?
                A) rechts vor links
                B) Vorfahrt achten
                Lösung: A
                """.trimIndent(),
            )

        val question = result.choices.single()
        assertEquals("Was gilt an dieser Kreuzung?", question.prompt)
        assertEquals(2, question.answers.size)
    }

    @Test
    fun `a block without a solution is skipped rather than failing the paste`() {
        val result =
            QuestionParser.parse(
                """
                Gute Frage?
                A) eins
                B) zwei

                Zweite Frage?
                A) drei
                B) vier
                Lösung: B
                """.trimIndent(),
            )

        assertEquals(1, result.questions.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `empty input yields nothing`() {
        val result = QuestionParser.parse("   \n\n  ")
        assertEquals(0, result.questions.size)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `prose without options is skipped`() {
        val result = QuestionParser.parse("Hier ist deine Fragensammlung, viel Erfolg!")
        assertEquals(0, result.questions.size)
        assertEquals(1, result.skipped)
    }

    // endregion

    @Test
    fun `a question carries the part it names`() {
        val result =
            QuestionParser.parse(
                """
                [
                  {"topic": "Vorfahrt", "question": "Wer darf zuerst?",
                   "answers": ["Der rechte", "Der linke"], "correct": 0}
                ]
                """.trimIndent(),
            )

        assertEquals("Vorfahrt", result.questions.single().topic)
    }

    @Test
    fun `a question without a part carries none`() {
        val result =
            QuestionParser.parse(
                """[{"question": "Wer darf zuerst?", "answers": ["A", "B"], "correct": 0}]""",
            )

        assertNull(result.questions.single().topic)
    }
}

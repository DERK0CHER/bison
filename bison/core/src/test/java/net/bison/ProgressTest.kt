package net.bison

import net.bison.model.Attempt
import net.bison.model.Card
import net.bison.model.CardKind
import net.bison.model.Deck
import net.bison.model.Rating
import net.bison.model.StudyCard
import net.bison.model.Subtopic
import net.bison.progress.CardProgress
import net.bison.progress.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the file the progress travels in.
 *
 * The same file is the export the brief asks for and the payload the two machines send each
 * other, so everything here is asked twice over: once as "can this be written and read back",
 * and once as "do two machines that both studied end up with both evenings".
 */
class ProgressTest {
    private val syntax =
        StudyCard(
            kind = CardKind.Syntax,
            prompt = "Lege einen Spaltenvektor mit 3, 6 und 2 an.",
            back = "d = [3;6;2];",
            topic = "MATLAB / Syntax",
        )

    private val timed =
        StudyCard(
            kind = CardKind.Zeit,
            prompt = "Definiere Node fuer eine doppelt verkettete Liste.",
            back = "typedef struct node { struct node *next; } Node;",
            target = 180,
            topic = "C / Zeit",
        )

    /** One set holding these cards, in the shape everything here takes and hands back */
    private fun decks(vararg cards: Card) =
        listOf(
            Deck(
                id = "swt",
                name = "Softwarewerkzeuge",
                subtopics = listOf(Subtopic(id = "swt-1", name = "Alles", cards = cards.toList())),
            ),
        )

    private fun at(
        millis: Long,
        rating: Rating = Rating.Right,
        seconds: Long = 0,
        typed: String? = null,
        rolled: Map<String, String> = emptyMap(),
    ) = Attempt(at = millis, rating = rating, seconds = seconds, typed = typed, rolled = rolled)

    @Test
    fun `a card keeps its name across the file`() {
        val written = Progress.json(Progress.of(decks(Card(task = syntax, history = listOf(at(1_700_000_000_000))))))

        assertEquals(syntax.cardId, Progress.read(written).single().id)
    }

    @Test
    fun `everything about an attempt survives being written and read`() {
        val attempt =
            at(
                millis = 1_757_000_123_456,
                rating = Rating.Syntax,
                seconds = 142,
                // quotation marks, a backslash and a newline, which is what a C answer looks like
                typed = "printf(\"%d\\n\", x);\nreturn 0;",
                rolled = mapOf("A" to "R", "z" to "7"),
            )

        val back = Progress.read(Progress.json(Progress.of(decks(Card(task = syntax, history = listOf(attempt))))))

        assertEquals(listOf(attempt), back.single().attempts)
    }

    @Test
    fun `the target of a timed card travels with it`() {
        val back = Progress.read(Progress.json(Progress.of(decks(Card(task = timed)))))

        assertEquals(180, back.single().target)
        assertEquals("zeit", back.single().type)
        assertEquals("C / Zeit", back.single().subsection)
    }

    @Test
    fun `two evenings on two machines make one history, not the later of the two`() {
        // the point of the whole format: ten cards on the train and five at the desk is fifteen
        val onThePhone = listOf(CardProgress(syntax.cardId, "MATLAB / Syntax", "syntax", "x", attempts = listOf(at(100), at(200))))
        val atTheDesk = listOf(CardProgress(syntax.cardId, "MATLAB / Syntax", "syntax", "x", attempts = listOf(at(150), at(300))))

        val merged = Progress.merge(onThePhone, atTheDesk).single()

        assertEquals(listOf(100L, 150L, 200L, 300L), merged.attempts.map { it.at })
    }

    @Test
    fun `an attempt that is already there is not counted twice`() {
        val same = at(500, Rating.Logic)
        val mine = listOf(CardProgress(syntax.cardId, null, "syntax", "x", attempts = listOf(same, at(600))))

        val merged = Progress.merge(mine, mine).single()

        assertEquals(2, merged.attempts.size)
    }

    @Test
    fun `a machine that has the history but not the card takes the wording from the one that has`() {
        val blank = CardProgress(syntax.cardId, null, "", "", attempts = listOf(at(100)))
        val known = CardProgress(syntax.cardId, "MATLAB / Syntax", "syntax", "d = ...", attempts = listOf(at(200)))

        val merged = Progress.merge(listOf(blank), listOf(known)).single()

        assertEquals("MATLAB / Syntax", merged.subsection)
        assertEquals("syntax", merged.type)
        assertEquals("d = ...", merged.front)
    }

    @Test
    fun `reading progress in works out the box again rather than trusting a sent one`() {
        val here = decks(Card(task = syntax, box = 7, history = listOf(at(100))))
        val there = listOf(CardProgress(syntax.cardId, null, "syntax", "x", attempts = listOf(at(200), at(300))))

        val card = Progress.applyTo(here, there).single().cards.single()

        // three right answers, replayed from nothing: the seven that was sitting here was one
        // machine's opinion of a history it had only half of
        assertEquals(3, card.box)
        assertEquals(listOf(100L, 200L, 300L), card.history.map { it.at })
    }

    @Test
    fun `a wrong answer halves the box on the way back in, exactly as it would live`() {
        val here = decks(Card(task = syntax))
        val there =
            listOf(
                CardProgress(
                    syntax.cardId,
                    null,
                    "syntax",
                    "x",
                    attempts = listOf(at(1), at(2), at(3), at(4), at(5, Rating.Logic)),
                ),
            )

        assertEquals(2, Progress.applyTo(here, there).single().cards.single().box)
    }

    @Test
    fun `the seconds are added up again from the attempts`() {
        val here = decks(Card(task = timed))
        val there =
            listOf(
                CardProgress(timed.cardId, null, "zeit", "x", 180, listOf(at(1, seconds = 200), at(2, seconds = 90))),
            )

        assertEquals(290L, Progress.applyTo(here, there).single().cards.single().seconds)
    }

    @Test
    fun `progress for a card this machine does not have leaves its cards alone`() {
        val here = decks(Card(task = syntax, box = 4))
        val stranger = listOf(CardProgress("0000", null, "syntax", "x", attempts = listOf(at(1))))

        assertEquals(4, Progress.applyTo(here, stranger).single().cards.single().box)
    }

    @Test
    fun `a file that is not a progress file changes nothing`() {
        assertEquals(emptyList<CardProgress>(), Progress.read("# Kartenset\n\ntype: syntax\nfront: was?"))
        assertEquals(emptyList<CardProgress>(), Progress.read(""))
        assertEquals(emptyList<CardProgress>(), Progress.read("{\"decks\":[]}"))
    }

    @Test
    fun `a semicolon in a card does not shift the columns`() {
        // half this set is C and MATLAB, so a front full of semicolons is the normal case rather
        // than the awkward one: unquoted, this single card would be four columns instead of one
        // and every column after it would be wrong without anything looking broken
        val card = StudyCard(CardKind.Syntax, "Was tut `d = [3;6;2];`?", "Spaltenvektor", topic = "MATLAB / Syntax")

        val rows = Progress.csv(Progress.of(decks(Card(task = card)))).lines()

        assertEquals(13, rows[0].split(";").size)
        assertTrue(rows[1], rows[1].contains("\"Was tut `d = [3;6;2];`?\""))
        assertEquals(13, fields(rows[1]).size)
        assertEquals("MATLAB / Syntax", fields(rows[1])[0])
    }

    /** Splits a row the way a spreadsheet does, so a quoted semicolon stays inside its cell */
    private fun fields(row: String): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var at = 0
        while (at < row.length) {
            val c = row[at]
            when {
                quoted && c == '"' && row.getOrNull(at + 1) == '"' -> {
                    cell.append('"')
                    at++
                }

                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> {
                    out += cell.toString()
                    cell.clear()
                }

                else -> cell.append(c)
            }
            at++
        }
        out += cell.toString()
        return out
    }

    @Test
    fun `a quotation mark in a card is doubled, as a spreadsheet expects`() {
        val quoted =
            StudyCard(
                kind = CardKind.Trace,
                prompt = "Was gibt printf(\"%d; %d\", a, b) aus?",
                back = "1; 2",
                topic = "C",
            )

        val row = Progress.csv(Progress.of(decks(Card(task = quoted)))).lines()[1]

        assertTrue(row, row.contains("\"Was gibt printf(\"\"%d; %d\"\", a, b) aus?\""))
    }

    @Test
    fun `the table puts what is left to revise at the top`() {
        val open = Card(task = syntax, history = listOf(at(1, Rating.Logic)))
        val done = Card(task = timed, history = listOf(at(1), at(2)))
        val fresh =
            Card(
                task = StudyCard(CardKind.Logik, "Ungefragt", "back", topic = "C / Zeit"),
            )

        val rows = Progress.csv(Progress.of(decks(open, done, fresh))).lines()

        // sorted by part first, so MATLAB's one open card comes after C's two
        assertEquals("C / Zeit", rows[1].split(";")[0])
        assertEquals("neu", rows[1].split(";").last())
        assertEquals("ok", rows[2].split(";").last())
        assertEquals("offen", rows[3].split(";").last())
    }

    @Test
    fun `the table counts the kinds of mistake apart`() {
        val card =
            Card(
                task = syntax,
                history = listOf(at(1), at(2, Rating.Syntax), at(3, Rating.Logic), at(4, Rating.Syntax)),
            )

        val cells = Progress.csv(Progress.of(decks(card))).lines()[1].split(";")

        assertEquals("4", cells[3])
        assertEquals("1", cells[4])
        assertEquals("2", cells[5])
        assertEquals("1", cells[6])
    }

    @Test
    fun `the same question in two parts of the set is two cards`() {
        val here = StudyCard(CardKind.Syntax, "Was ist x?", "1", topic = "MATLAB")
        val there = here.copy(topic = "C")

        assertNotEquals(here.cardId, there.cardId)
    }

    @Test
    fun `correcting a typo in the answer does not start the history over`() {
        // filed on the front, never on the back: fixing a model answer must not cost the history
        assertEquals(syntax.cardId, syntax.copy(back = "d = [3; 6; 2];").cardId)
    }
}

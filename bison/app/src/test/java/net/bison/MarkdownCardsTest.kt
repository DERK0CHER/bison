package net.bison

import net.bison.importer.MarkdownCards
import net.bison.model.CardKind
import net.bison.model.Question
import net.bison.model.StudyCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the written set.
 *
 * Every block here is copied out of the real file rather than invented, because the job was to
 * import exactly that file and a test against a tidied-up version of it would prove nothing.
 */
class MarkdownCardsTest {
    private val file =
        """
        # Kartenset Softwarewerkzeuge und Softwaretechnik

        Format: eine Karte pro Block, Blöcke getrennt durch `---`. Felder:
        - `type`: logik | syntax | param | sc | trace | fehler
        - `front`: Aufgabe (bei sc: Frage plus drei Optionen a/b/c)

        ## Subsection: MATLAB / Logik

        ---
        type: logik
        front: Welche drei Klammerarten gibt es in MATLAB und was tut jede?
        back: `[ ]` baut oder konkateniert. `( )` indiziert oder ruft auf.
        tags: matlab, logik, klammern

        ## Subsection: C / Single Choice

        ---
        type: sc
        front: Mit welchem Übergabemechanismus wird `void square(long *v){ *v *= *v; }` aufgerufen?
        a) Call by Address  b) Call by Reference  c) Call by Value
        logik: Parameter mit Stern, Adresse wird übergeben.
        back: b. Call by Reference; die Funktion verändert das Original über die Adresse.
        tags: c, sc, pointer
        """.trimIndent()

    @Test
    fun `the set is named after its title`() {
        assertEquals("Kartenset Softwarewerkzeuge und Softwaretechnik", MarkdownCards.parse(file).name)
    }

    @Test
    fun `what stands above the first heading is the file explaining itself, not a card`() {
        val found = MarkdownCards.parse(file)

        assertEquals(2, found.cards.size)
        assertEquals(0, found.skipped)
    }

    @Test
    fun `the parts come from the headings, with the word Subsection taken off`() {
        assertEquals(
            listOf("MATLAB / Logik", "C / Single Choice"),
            MarkdownCards.parse(file).cards.map { it.topic },
        )
    }

    @Test
    fun `a single choice card becomes the question this app already had`() {
        // the screens are not taught the file's shape; the importer speaks the model's
        val card = MarkdownCards.parse(file).cards[1] as Question

        assertEquals(listOf("Call by Address", "Call by Reference", "Call by Value"), card.answers)
        assertEquals("Call by Reference", card.correctAnswer)
        // the options line is taken out of the question, so nothing is said twice
        assertTrue(card.prompt, !card.prompt.contains("a) Call by Address"))
        // and the braces of the C in it survived, which they would not if any card went through
        // the placeholder substitution
        assertTrue(card.prompt.contains("{ *v *= *v; }"))
        assertTrue(card.reason.orEmpty().startsWith("Call by Reference;"))
        assertEquals("Parameter mit Stern, Adresse wird übergeben.", card.logic)
    }

    @Test
    fun `a card that turns over is a study card with no reasoning of its own`() {
        val card = MarkdownCards.parse(file).cards[0] as StudyCard

        assertEquals(CardKind.Logik, card.kind)
        assertNull(card.logic)
        assertEquals(listOf("matlab", "logik", "klammern"), card.tags)
    }

    @Test
    fun `a value runs on over several lines until the next field`() {
        val trace =
            """
            ## Subsection: MATLAB / Trace

            ---
            type: trace
            front: Was ist x nach diesem Code?
            for k = 1:10
                x(1,k) = k;
                x(2,k) = k^2;
            end
            logik: Zuweisung an (Zeile 1, Spalte k) baut die Matrix spaltenweise auf.
            back: Matrix 2x10. Erste Zeile 1 bis 10, zweite Zeile die Quadratzahlen.
            tags: matlab, trace
            """.trimIndent()

        val card = MarkdownCards.parse(trace).cards.single() as StudyCard

        assertEquals(CardKind.Trace, card.kind)
        assertTrue(card.prompt.contains("for k = 1:10"))
        assertTrue("the indentation inside the code was eaten", card.prompt.contains("    x(1,k) = k;"))
        assertTrue(card.prompt.contains("end"))
        // the code stopped where the next field began
        assertTrue(!card.prompt.contains("Zuweisung"))
        assertTrue(card.back.startsWith("Matrix 2x10"))
    }

    @Test
    fun `alternatives are separated by a pipe, parameters by a semicolon`() {
        val param =
            """
            ## Subsection: MATLAB / Param

            ---
            type: param
            front: Nur den Eintrag in der {z}. Zeile, {s}. Spalte mit dem {f}-fachen ersetzen.
            logik: Links und rechts derselbe Index.
            back: {A}({z},{s}) = {f}*{A}({z},{s});
            alt: {A}({z},{s}) = {A}({z},{s})*{f}; | {A}({z},{s})={f}*{A}({z},{s});
            params: A=R,G,H,X,Z ; z=2..25 ; s=2..25 ; f=2..9
            tags: matlab, param
            """.trimIndent()

        val card = MarkdownCards.parse(param).cards.single() as StudyCard

        assertEquals(CardKind.Param, card.kind)
        assertEquals(2, card.alternatives.size)
        assertEquals(3, card.accepted.size)
        assertEquals("A=R,G,H,X,Z ; z=2..25 ; s=2..25 ; f=2..9", card.params)
        assertTrue(card.isTyped)
    }

    @Test
    fun `the dispatcher sends the written set to the reader that knows it`() {
        val result = net.bison.importer.CardImport.parse(file)

        // read by the older card reader instead, every card would come out as an unknown type
        // and be skipped - which is what "the file import does not work" looks like from outside
        assertEquals(net.bison.importer.CardImport.Format.WrittenSet, result.format)
        assertEquals(2, result.tasks.size)
    }

    @Test
    fun `a block missing the parts a card needs is counted rather than guessed at`() {
        val broken =
            """
            ## Subsection: Irgendwas

            ---
            type: syntax
            front: Ohne Lösung
            tags: kaputt

            ---
            front: Ohne Typ
            back: irgendwas
            """.trimIndent()

        val found = MarkdownCards.parse(broken)

        assertEquals(0, found.cards.size)
        assertEquals(2, found.skipped)
    }
}

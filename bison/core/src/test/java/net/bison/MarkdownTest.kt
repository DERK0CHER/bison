package net.bison

import net.bison.text.Markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading half of a card's Markdown, which both interfaces now share.
 *
 * Whatever else changes here, nothing may go missing: a card whose text is silently shortened on
 * the way to the screen is worse than one that shows its backticks.
 */
class MarkdownTest {
    @Test
    fun `a fenced block is separated from the prose around it`() {
        val pieces =
            Markdown.pieces(
                """
                Was gibt das aus?

                ```c
                printf("%d\n", 3);
                ```

                Achte auf den Zeilenumbruch.
                """.trimIndent(),
            )

        assertEquals(3, pieces.size)
        assertTrue(pieces[0] is Markdown.Piece.Prose)
        assertEquals(Markdown.Piece.Code("printf(\"%d\\n\", 3);"), pieces[1])
        assertTrue(pieces[2] is Markdown.Piece.Prose)
    }

    @Test
    fun `a fence that was never closed takes the rest with it rather than being dropped`() {
        val pieces = Markdown.pieces("Schreibe:\n\n```\nd = [3;6;2];\nx = 4;")

        assertEquals(2, pieces.size)
        assertEquals(Markdown.Piece.Code("d = [3;6;2];\nx = 4;"), pieces[1])
    }

    @Test
    fun `text with no fence stays one piece, exactly as written`() {
        val plain = "Nebeneinander braucht gleiche Zeilenzahl."

        assertEquals(listOf(Markdown.Piece.Prose(plain)), Markdown.pieces(plain))
    }

    @Test
    fun `backticks and asterisks mark their runs and then get out of the way`() {
        val spans = Markdown.spans("Falsche Klammer: `zeros[8,1]` ist **falsch**.")

        assertEquals("Falsche Klammer: zeros[8,1] ist falsch.", spans.joinToString("") { it.text })
        assertEquals(listOf("zeros[8,1]"), spans.filter { it.code }.map { it.text })
        assertEquals(listOf("falsch"), spans.filter { it.bold }.map { it.text })
    }

    @Test
    fun `an unclosed backtick is left standing rather than eating the rest`() {
        val spans = Markdown.spans("Ein ` ohne Ende")

        assertEquals("Ein ` ohne Ende", spans.joinToString("") { it.text })
        assertTrue(spans.none { it.code })
    }

    @Test
    fun `nothing of the text is lost, whatever the markup does`() {
        // every shape that turns up in the set, and one that is only a mistake
        val awkward =
            listOf(
                "a `b` c",
                "`a`",
                "``",
                "**",
                "*einzeln*",
                "a ** b",
                "`a` und `b` und `c`",
                "Was tut `d = [3;6;2];`?",
            )

        for (text in awkward) {
            val back = Markdown.spans(text).joinToString("") { it.text }
            // the markers themselves may go; nothing between them may
            assertTrue("$text -> $back", back.length >= text.count { it != '`' && it != '*' })
        }
    }
}

package net.bison

import androidx.compose.ui.graphics.Color
import net.bison.ui.inline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The little Markdown a card uses.
 *
 * What is checked is the text that comes out and where the styled runs sit: a backtick pair is
 * a piece of code inside a sentence, and both the backticks going away and the run being marked
 * matter. A card that showed its backticks would be saying "this bit is code" with the one
 * character that is not.
 */
class CardTextTest {
    private val ink = Color.White

    @Test
    fun `backticks go away and what stood between them is marked`() {
        val text = inline("Falsche Klammer: `zeros[8,1]` ist falsch.", ink)

        assertEquals("Falsche Klammer: zeros[8,1] ist falsch.", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals("zeros[8,1]", text.text.substring(text.spanStyles[0].start, text.spanStyles[0].end))
    }

    @Test
    fun `several pieces of code in one sentence`() {
        val text = inline("`[ ]` baut, `( )` indiziert, `' '` ist ein String.", ink)

        assertEquals("[ ] baut, ( ) indiziert, ' ' ist ein String.", text.text)
        assertEquals(3, text.spanStyles.size)
    }

    @Test
    fun `bold is read as well`() {
        val text = inline("Das ist **wichtig** hier.", ink)

        assertEquals("Das ist wichtig hier.", text.text)
        assertEquals(1, text.spanStyles.size)
    }

    @Test
    fun `an unclosed backtick is left standing rather than eating the rest`() {
        val text = inline("Ein ` ohne Ende", ink)

        // whatever else happens, no text may go missing on the way to the screen
        assertTrue(text.text, text.text.contains("ohne Ende"))
    }

    @Test
    fun `a sentence with no markup comes through untouched`() {
        val plain = "Nebeneinander braucht gleiche Zeilenzahl."

        assertEquals(plain, inline(plain, ink).text)
        assertTrue(inline(plain, ink).spanStyles.isEmpty())
    }
}

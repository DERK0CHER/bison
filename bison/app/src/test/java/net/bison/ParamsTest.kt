package net.bison

import net.bison.domain.Params
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The numbers a parametrised card makes up, and the sums written on it.
 *
 * Every `params` line here is copied out of the real set. What is checked is not that a
 * particular number comes out - the point of the card is that it does not - but that whatever
 * comes out is consistent: the bits and the decimal are the same number, the width is the width
 * the file asked for, and the answer to `and(a,b)` really is the two operands anded together.
 */
class ParamsTest {
    private fun roll(
        spec: String,
        seed: Int = 1,
    ) = Params.roll(spec, Random(seed))

    @Test
    fun `a decimal to binary card shows eight bits and means the same number`() {
        repeat(20) { seed ->
            val values = roll("n=1..255 ; width=8", seed)
            val bits = Params.fill("{bin(n)}", values)

            assertEquals("$bits is not eight wide", 8, bits.length)
            assertTrue(bits.all { it == '0' || it == '1' })
            assertEquals(values.getValue("n").number, bits.toLong(2))
        }
    }

    @Test
    fun `a binary to decimal card rolls the bits and asks for the number`() {
        repeat(20) { seed ->
            val values = roll("b=bin(1..255) ; width=8", seed)

            val shown = Params.fill("{b}", values)
            val answer = Params.fill("{dec(b)}", values)

            assertEquals(8, shown.length)
            assertEquals(shown.toLong(2), answer.toLong())
        }
    }

    @Test
    fun `hexadecimal comes out in capitals, four digits wide for sixteen bits`() {
        val values = roll("b=bin(1..65535) ; width=16")

        val hex = Params.fill("{hex(b)}", values)

        assertEquals(4, hex.length)
        assertEquals(hex, hex.uppercase())
        assertEquals(Params.fill("{b}", values).toLong(2), hex.toLong(16))
    }

    @Test
    fun `a card with no width is not padded`() {
        // n=8..511 in the set, where 12 is the answer and 012 would be marked wrong
        repeat(20) { seed ->
            val values = roll("n=8..511", seed)
            val octal = Params.fill("{oct(n)}", values)

            assertTrue("$octal was padded", !octal.startsWith("0"))
            assertEquals(values.getValue("n").number, octal.toLong(8))
        }
    }

    @Test
    fun `the bit operations work on the operands and come back at the full width`() {
        repeat(20) { seed ->
            val values = roll("a=bin(0..255) ; b=bin(0..255) ; width=8", seed)
            val left = Params.fill("{a}", values).toLong(2)
            val right = Params.fill("{b}", values).toLong(2)

            assertEquals(left and right, Params.fill("{and(a,b)}", values).toLong(2))
            assertEquals(left or right, Params.fill("{or(a,b)}", values).toLong(2))
            assertEquals(left xor right, Params.fill("{xor(a,b)}", values).toLong(2))
            assertEquals(8, Params.fill("{and(a,b)}", values).length)
        }
    }

    @Test
    fun `a shift keeps the width and drops the bits that fall off the end`() {
        repeat(20) { seed ->
            val values = roll("a=bin(0..255) ; k=1..4 ; width=8", seed)
            val before = Params.fill("{a}", values).toLong(2)
            val by = values.getValue("k").number!!.toInt()

            val after = Params.fill("{shr(a,k)}", values)

            assertEquals(8, after.length)
            assertEquals(before shr by, after.toLong(2))
        }
    }

    @Test
    fun `a derived parameter is worked out from the ones above it`() {
        val values = roll("m=3..8 ; c1=1..4 ; c2=2..5 ; n=(c1+c2) ; w1=2..9")

        assertEquals(
            values.getValue("c1").number!! + values.getValue("c2").number!!,
            values.getValue("n").number,
        )
    }

    @Test
    fun `a choice is one of the ones written down`() {
        repeat(30) { seed ->
            val values = roll("A=R,G,H,X,Z ; z=2..25", seed)

            assertTrue(values.getValue("A").text in listOf("R", "G", "H", "X", "Z"))
            assertTrue(values.getValue("z").number!! in 2..25)
        }
    }

    @Test
    fun `arithmetic and the two comparisons are worked out`() {
        val values = roll("x1=-20..20 ; x2=-20..20 ; v=1..50 ; n=1..9")
        val x1 = values.getValue("x1").number!!
        val x2 = values.getValue("x2").number!!

        assertEquals(minOf(x1, x2).toString(), Params.fill("{min(x1,x2)}", values))
        assertEquals(maxOf(x1, x2).toString(), Params.fill("{max(x1,x2)}", values))
        assertEquals((values.getValue("v").number!! * 2).toString(), Params.fill("{v*2}", values))
        assertEquals((values.getValue("n").number!! + 1).toString(), Params.fill("{n+1}", values))
    }

    @Test
    fun `a whole card is filled in, and the braces of C are left alone`() {
        val values = roll("A=R,G,H,X,Z ; z=2..25 ; s=2..25 ; v=1..50")

        val back = Params.fill("{A}({z},{s}) = {v};", values)

        assertTrue(back, back.matches(Regex("""[RGHXZ]\(\d+,\d+\) = \d+;""")))
    }

    @Test
    fun `something that cannot be read is left standing where it can be seen`() {
        val values = roll("z=2..25")

        // a card that quietly showed 0 here would be marked against a wrong answer all term
        assertEquals("{min(x1,x9)}", Params.fill("{min(x1,x9)}", values))
        assertEquals("{nonsense(", Params.fill("{nonsense(", values))
    }

    @Test
    fun `the same seed rolls the same card twice`() {
        assertEquals(roll("a=bin(0..255) ; b=bin(0..255) ; width=8", 7), roll("a=bin(0..255) ; b=bin(0..255) ; width=8", 7))
    }
}

package net.bison

import net.bison.domain.Generator
import net.bison.domain.Rolled
import net.bison.model.BitOp
import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * Tests the cards that make their own numbers up.
 *
 * The point of these cards is that nobody wrote the answer down, so the answer has to be checked
 * against the question that was actually rolled rather than against a table of expected values.
 */
class GeneratorTest {
    private val byteToHex = GeneratedTask(kind = GenKind.Convert, from = 2, to = 16, bits = 8)

    @Test
    fun `the same seed rolls the same sum`() {
        // the screen redraws constantly, so a round that rolled again on every frame would ask
        // one question and mark another
        assertEquals(Generator.roll(byteToHex, Random(7)), Generator.roll(byteToHex, Random(7)))
    }

    @Test
    fun `a conversion is the number that was shown, written another way`() {
        repeat(50) { seed ->
            val rolled = Generator.roll(byteToHex, Random(seed))
            val shown = rolled.question.removePrefix("0b").filterNot { it.isWhitespace() }

            assertEquals(shown.toLong(2), rolled.answer.toLong(16))
        }
    }

    @Test
    fun `binary is padded to the width of the card and set in fours`() {
        val rolled = Generator.roll(byteToHex, Random(3))

        // eight digits and the one space that splits them into two nibbles
        assertEquals(9, rolled.question.removePrefix("0b").length)
        assertTrue(rolled.question.startsWith("0b"))
    }

    @Test
    fun `an operator is applied to the two numbers on the card`() {
        val task = GeneratedTask(kind = GenKind.Bits, from = 16, to = 16, bits = 8, op = BitOp.Xor)

        repeat(20) { seed ->
            val rolled = Generator.roll(task, Random(seed))
            val (a, b) = rolled.question.split(" ^ ").map { it.trim().removePrefix("0x").toLong(16) }

            assertEquals(a xor b, rolled.answer.toLong(16))
        }
    }

    @Test
    fun `a shift stays inside the width and never shifts everything out`() {
        val task = GeneratedTask(kind = GenKind.Bits, from = 2, to = 2, bits = 8, op = BitOp.ShiftLeft)

        repeat(50) { seed ->
            val rolled = Generator.roll(task, Random(seed))
            val distance = rolled.question.substringAfter(" << ").trim().toInt()

            assertTrue("shifted by $distance", distance in 1 until 8)
            assertTrue(rolled.answer.toLong(2) <= task.mask)
        }
    }

    @Test
    fun `printf prints what C would print`() {
        val task = GeneratedTask(kind = GenKind.Printf, op = BitOp.Times, format = "%4x")

        val rolled = Generator.roll(task, Random(5))
        val numbers = requireNotNull(Regex("""(\d+) \* (\d+)""").find(rolled.question)).groupValues
        val product = numbers[1].toLong() * numbers[2].toLong()

        assertEquals(String.format(Locale.ROOT, "%4x", product), rolled.answer)
    }

    @Test
    fun `a format that would throw is not one a card may carry`() {
        assertTrue(GeneratedTask.formatIsSound("%x"))
        assertTrue(GeneratedTask.formatIsSound("%04X"))
        assertTrue(GeneratedTask.formatIsSound("%-8d"))
        // %s against a number, and %n, are the two that throw rather than print something odd
        assertTrue(!GeneratedTask.formatIsSound("%s"))
        assertTrue(!GeneratedTask.formatIsSound("%n"))
        assertTrue(!GeneratedTask.formatIsSound("was auch immer"))
    }

    @Test
    fun `an answer is read as a number, however it is spelled`() {
        val rolled = Rolled(question = "0b0000 1111", answer = "f", base = 16)

        assertTrue(rolled.matches("f"))
        assertTrue(rolled.matches("0F"))
        assertTrue(rolled.matches("0x0f"))
        assertTrue(rolled.matches("  f "))
        assertTrue(!rolled.matches("e"))
    }

    @Test
    fun `binary written in groups of four is still the same answer`() {
        val rolled = Rolled(question = "0x2f", answer = "00101111", base = 2)

        assertTrue(rolled.matches("0010 1111"))
        assertTrue(rolled.matches("101111"))
        assertTrue(!rolled.matches("0010 1110"))
    }

    @Test
    fun `what printf prints is compared as text, apart from the ends`() {
        val rolled = Rolled(question = """printf("%04x", 3 * 5);""", answer = "000f", base = null)

        assertTrue(rolled.matches("000f"))
        assertTrue(rolled.matches(" 000f "))
        // the zeros are the exercise, so dropping them is not the same answer
        assertTrue(!rolled.matches("f"))
    }

    @Test
    fun `a card that would ask for an impossible base is pulled back to one that works`() {
        val task = GeneratedTask(kind = GenKind.Convert, from = 99, to = 0, bits = 400)

        assertEquals(36, task.fromBase)
        assertEquals(2, task.toBase)
        assertEquals(32, task.width)
        // and it still rolls rather than throwing
        assertTrue(Generator.roll(task, Random(1)).answer.isNotEmpty())
    }
}

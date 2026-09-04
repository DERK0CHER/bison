package net.bison.domain

import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import java.util.Locale
import kotlin.random.Random

/**
 * One instance of a generated card: the numbers as they came out, and the answer to them.
 *
 * @param question the line the card shows, set as code
 * @param answer what the app worked out
 * @param base the base the answer is written in, or null when the answer is text that printf
 *   produced
 */
data class Rolled(
    val question: String,
    val answer: String,
    val base: Int?,
) {
    /**
     * Whether a typed answer is this one.
     *
     * A number is compared as a number whenever it reads as one, so `0f`, `f`, `0x0F` and
     * `0000 1111` are all the same hexadecimal answer. The exercise is the conversion and not
     * the spelling, and writing binary in groups of four is a habit worth keeping rather than
     * one to fail somebody over.
     *
     * What printf prints is compared as text, because there the padding is the exercise - except
     * at the ends, where a width pads with spaces that are invisible in a text field and
     * miserable to type on a phone. `%04x` still has to be answered with its zeros.
     */
    fun matches(typed: String): Boolean {
        if (base == null) return typed.trim() == answer.trim()
        val mine =
            typed
                .trim()
                .lowercase()
                .filterNot { it.isWhitespace() }
                .removePrefix("0x")
                .removePrefix("0b")
        val value = mine.toLongOrNull(base) ?: return mine == answer.lowercase()
        return value == answer.toLong(base)
    }
}

/**
 * Rolls the numbers for a generated card and works out the answer.
 *
 * Kept away from the screen and given its own [Random] so that a round can be repeated exactly:
 * the tests need that, and so do the rendered screenshots, which would otherwise differ on every
 * run and stop being comparable.
 */
object Generator {
    fun roll(
        task: GeneratedTask,
        random: Random,
    ): Rolled =
        when (task.kind) {
            GenKind.Convert -> convert(task, random)
            GenKind.Bits -> bits(task, random)
            GenKind.Printf -> printf(task, random)
        }

    private fun convert(
        task: GeneratedTask,
        random: Random,
    ): Rolled {
        val value = random.nextLong(1, task.mask + 1)
        return Rolled(
            question = show(value, task.fromBase, task.width),
            answer = value.toString(task.toBase),
            base = task.toBase,
        )
    }

    private fun bits(
        task: GeneratedTask,
        random: Random,
    ): Rolled {
        val a = random.nextLong(1, task.mask + 1)
        // a shift is by a distance rather than by a second number, and a distance as wide as the
        // number itself would only ever shift everything out
        val b = if (task.op.shifts) random.nextLong(1, task.width.toLong()) else random.nextLong(1, task.mask + 1)
        val result = task.op.apply(a, b) and task.mask
        val right = if (task.op.shifts) b.toString() else show(b, task.fromBase, task.width)
        return Rolled(
            question = "${show(a, task.fromBase, task.width)} ${task.op.symbol} $right",
            answer = result.toString(task.toBase),
            base = task.toBase,
        )
    }

    private fun printf(
        task: GeneratedTask,
        random: Random,
    ): Rolled {
        val a = random.nextLong(2, 16)
        val b = if (task.op.shifts) random.nextLong(1, 5) else random.nextLong(2, 16)
        val value = task.op.apply(a, b)
        return Rolled(
            question = """printf("${task.format}", $a ${task.op.symbol} $b);""",
            answer = String.format(Locale.ROOT, task.format, value),
            base = null,
        )
    }

    /**
     * A number as it would be written in that base.
     *
     * Binary is padded to the card's width and set in groups of four, because that is how a
     * byte is read; a run of eight digits is not read at all, it is counted.
     */
    private fun show(
        value: Long,
        base: Int,
        width: Int,
    ): String {
        val digits = value.toString(base)
        if (base != 2) return GeneratedTask.prefixOf(base) + digits
        return GeneratedTask.prefixOf(2) + inFours(digits.padStart(width, '0'))
    }

    private fun inFours(digits: String): String = digits.reversed().chunked(4).joinToString(" ").reversed()
}

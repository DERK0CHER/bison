package net.bison.domain

import kotlin.random.Random

/**
 * One value a parametrised card is working with.
 *
 * Both halves are kept: the text is what gets written onto the card, the number is what
 * arithmetic is done on. `01101001` is the text and 105 is the number, and a card needs
 * whichever the place it is put into asks for - `{b}` wants the bits, `{dec(b)}` wants the 105.
 *
 * A choice like `R` has no number, and using it in a sum is an error rather than a zero.
 */
data class Value(
    val text: String,
    val number: Long?,
) {
    companion object {
        fun of(number: Long) = Value(number.toString(), number)
    }
}

/**
 * The numbers a parametrised card makes up, and the sums written on it.
 *
 * Such a card holds an exercise rather than an instance: `params` says what to roll, and the
 * front, the back and the alternatives are written with `{...}` in them. Every showing rolls
 * again, so the card cannot be learned as a fact - which is the entire reason to ask a
 * conversion rather than to state one.
 *
 * Two things this deliberately is not. It is not a general expression language: it does what the
 * card format asks for and refuses the rest, because a card that quietly evaluates to something
 * unintended is worse than one that says it cannot be read. And it is not floating point -
 * everything it describes is a whole number.
 */
object Params {
    /**
     * Rolls one set of values from a `params` line.
     *
     * Entries are separated by `;` because a choice is separated by `,` - `A=R,G,H ; z=2..25` is
     * two parameters, not six. They are rolled in the order they are written, so a derived one
     * may use anything above it, which is what `n=(c1+c2)` is for.
     *
     * `width` is read first wherever it stands, because the file writes `b=bin(1..255) ; width=8`
     * and the thing that needs the width comes before it.
     */
    fun roll(
        spec: String,
        random: Random,
    ): Map<String, Value> {
        val entries = spec.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val width = entries.firstOrNull { it.startsWith("$WIDTH=") }?.substringAfter('=')?.trim()?.toIntOrNull()
        val values = LinkedHashMap<String, Value>()
        width?.let { values[WIDTH] = Value.of(it.toLong()) }
        for (entry in entries) {
            val name = entry.substringBefore('=').trim()
            val body = entry.substringAfter('=', "").trim()
            if (name.isEmpty() || body.isEmpty() || name == WIDTH) continue
            values[name] = rollOne(body, width, values, random)
        }
        return values
    }

    /**
     * Writes the values into a card's text, evaluating whatever stands inside braces.
     *
     * Anything that cannot be read is left exactly as it was written. A card showing
     * `{min(x1,x9)}` is visibly broken; one quietly showing `0` is not, and would be marked
     * against a wrong answer for the rest of the term.
     *
     * Only parametrised cards go through here. Braces are ordinary characters in C - `void
     * square(long *v){ *v *= *v; }` is a card front - and substituting inside those would
     * destroy them.
     */
    fun fill(
        template: String,
        values: Map<String, Value>,
    ): String =
        BRACES.replace(template) { match ->
            runCatching { evaluate(match.groupValues[1], values).text }.getOrDefault(match.value)
        }

    /** Evaluates one expression against the rolled values */
    fun evaluate(
        expression: String,
        values: Map<String, Value>,
    ): Value = Parser(expression, values, widthOf(values)).parse()

    private fun widthOf(values: Map<String, Value>): Int? = values[WIDTH]?.number?.toInt()

    private fun rollOne(
        body: String,
        width: Int?,
        sofar: Map<String, Value>,
        random: Random,
    ): Value {
        // b=bin(1..255), h=hex(256..65535): a number out of the range, written in that base
        val based = BASED.matchEntire(body)
        if (based != null) {
            val rolled = between(based.groupValues[2].toLong(), based.groupValues[3].toLong(), random)
            return Value(render(rolled, based.groupValues[1], width), rolled)
        }

        // z=2..20: a number out of the range
        val range = RANGE.matchEntire(body)
        if (range != null) {
            return Value.of(between(range.groupValues[1].toLong(), range.groupValues[2].toLong(), random))
        }

        // n=(c1+c2): worked out from what has been rolled already
        if (body.startsWith("(") && body.endsWith(")")) {
            return Parser(body.substring(1, body.length - 1), sofar, width).parse()
        }

        // A=R,G,H: one of them
        val choices = body.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (choices.size > 1) {
            val picked = choices[random.nextInt(choices.size)]
            return Value(picked, picked.toLongOrNull())
        }

        return Value(body, body.toLongOrNull())
    }

    private fun between(
        from: Long,
        to: Long,
        random: Random,
    ): Long {
        val low = minOf(from, to)
        val high = maxOf(from, to)
        return if (low == high) low else random.nextLong(low, high + 1)
    }

    /**
     * A number written in a base.
     *
     * Padded to the card's width when it has one, and left at its natural length when it has
     * not: the file writes `n=1..255 ; width=8` for the byte conversions, where `00001010` is
     * the answer, and plain `n=8..511` for the octal one, where `12` is the answer and `012`
     * would be marked wrong.
     *
     * Hexadecimal comes out in capitals, which is how the cards teach it - the reasoning on them
     * reads `1010 = A, 1011 = B`. The two printf cards want lower case and will not match; both
     * of them carry prose in the answer as well, so they are self-marked either way.
     */
    internal fun render(
        value: Long,
        base: String,
        width: Int?,
    ): String {
        val digits =
            when (base) {
                "bin" -> value.toString(2)
                "hex" -> value.toString(16).uppercase()
                "oct" -> value.toString(8)
                else -> value.toString()
            }
        val places =
            when {
                width == null -> 0
                base == "bin" -> width
                // as many places as hold the same number of bits: a byte is two hex digits
                base == "hex" -> (width + 3) / 4
                base == "oct" -> (width + 2) / 3
                else -> 0
            }
        return digits.padStart(places, '0')
    }

    private const val WIDTH = "width"

    private val BRACES = Regex("""\{([^{}]*)\}""")

    private val RANGE = Regex("""^(-?\d+)\s*\.\.\s*(-?\d+)$""")

    private val BASED = Regex("""^(bin|hex|oct)\s*\(\s*(-?\d+)\s*\.\.\s*(-?\d+)\s*\)$""")

    /**
     * Reads one expression: numbers, names, the four operations, and the handful of functions
     * the cards use. Written by hand because it is a hundred lines, and a dependency is not.
     */
    private class Parser(
        private val source: String,
        private val values: Map<String, Value>,
        private val width: Int?,
    ) {
        private var at = 0

        fun parse(): Value {
            val result = sum()
            skipSpace()
            require(at >= source.length) { "left over at $at in $source" }
            return result
        }

        private fun sum(): Value {
            var left = product()
            while (true) {
                skipSpace()
                val operator = peek() ?: return left
                if (operator != '+' && operator != '-') return left
                at++
                val right = product()
                left = Value.of(if (operator == '+') number(left) + number(right) else number(left) - number(right))
            }
        }

        private fun product(): Value {
            var left = atom()
            while (true) {
                skipSpace()
                val operator = peek() ?: return left
                if (operator != '*' && operator != '/' && operator != '%') return left
                at++
                val right = number(atom())
                // a card that divides by zero is a card with a mistake in it, and saying so
                // leaves the braces standing where they can be seen
                if (right == 0L && operator != '*') throw IllegalArgumentException("divided by zero in $source")
                left =
                    Value.of(
                        when (operator) {
                            '*' -> number(left) * right
                            '/' -> number(left) / right
                            else -> number(left) % right
                        },
                    )
            }
        }

        private fun atom(): Value {
            skipSpace()
            val start = peek() ?: throw IllegalArgumentException("nothing to read in $source")

            if (start == '(') {
                at++
                val inner = sum()
                skipSpace()
                require(peek() == ')') { "no closing bracket in $source" }
                at++
                return inner
            }
            if (start == '-') {
                at++
                return Value.of(-number(atom()))
            }
            if (start.isDigit()) {
                val from = at
                while (at < source.length && source[at].isDigit()) at++
                return Value.of(source.substring(from, at).toLong())
            }

            require(start.isLetter() || start == '_') { "cannot read $start in $source" }
            val from = at
            while (at < source.length && (source[at].isLetterOrDigit() || source[at] == '_')) at++
            val name = source.substring(from, at)

            skipSpace()
            if (peek() != '(') return values[name] ?: throw IllegalArgumentException("no value called $name")

            at++
            val arguments = mutableListOf<Value>()
            skipSpace()
            if (peek() != ')') {
                arguments += sum()
                skipSpace()
                while (peek() == ',') {
                    at++
                    arguments += sum()
                    skipSpace()
                }
            }
            require(peek() == ')') { "no closing bracket after $name in $source" }
            at++
            return call(name, arguments)
        }

        /**
         * The functions a card may use.
         *
         * The bitwise ones give back a string of bits at the card's width, because that is what
         * they are for: the answer to `and(a,b)` on a byte is eight characters, and eight
         * characters is what has to be typed.
         */
        private fun call(
            name: String,
            arguments: List<Value>,
        ): Value {
            fun first() = number(arguments.getOrNull(0) ?: fail(name))
            fun second() = number(arguments.getOrNull(1) ?: fail(name))
            return when (name) {
                "bin", "hex", "oct" -> Value(render(first(), name, width), first())
                "dec" -> Value.of(first())
                "min" -> Value.of(minOf(first(), second()))
                "max" -> Value.of(maxOf(first(), second()))
                "abs" -> Value.of(kotlin.math.abs(first()))
                "and" -> bits(first() and second())
                "or" -> bits(first() or second())
                "xor" -> bits(first() xor second())
                "not" -> bits(first().inv())
                "shr" -> bits(first() shr second().toInt())
                "shl" -> bits(first() shl second().toInt())
                else -> throw IllegalArgumentException("there is no function called $name")
            }
        }

        /** Kept inside the width, so a shift cannot walk off the end of the number */
        private fun bits(value: Long): Value {
            val places = (width ?: DEFAULT_BITS).coerceIn(1, 62)
            val masked = value and ((1L shl places) - 1)
            return Value(masked.toString(2).padStart(places, '0'), masked)
        }

        private fun fail(name: String): Nothing = throw IllegalArgumentException("$name wants more arguments")

        /**
         * The number behind a value.
         *
         * A string of bits counts as one - `{dec(b)}` on `1010` is ten - and a choice like `R`
         * does not, because there is no sensible answer and inventing one would put a wrong
         * number on a card.
         */
        private fun number(value: Value): Long =
            value.number
                ?: value.text.toLongOrNull(2)
                ?: throw IllegalArgumentException("${value.text} is not a number")

        private fun peek(): Char? = source.getOrNull(at)

        private fun skipSpace() {
            while (at < source.length && source[at].isWhitespace()) at++
        }

        private companion object {
            /** What a bit operation falls back to when the card never said how wide it works */
            const val DEFAULT_BITS = 8
        }
    }
}

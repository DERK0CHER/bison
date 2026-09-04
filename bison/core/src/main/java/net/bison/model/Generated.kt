package net.bison.model

/** What a generated card asks for */
enum class GenKind {
    /** The same number written in another base */
    Convert,

    /** Two numbers put together with one operator, the result in some base */
    Bits,

    /** What a printf with a width and a conversion prints */
    Printf,
}

/**
 * The operators a generated card uses, written the way C writes them.
 *
 * C is what the exam is in, so `&` rather than AND: the paper does not spell them out either,
 * and reading the symbol is part of the exercise.
 */
enum class BitOp(
    val symbol: String,
) {
    And("&"),
    Or("|"),
    Xor("^"),
    ShiftLeft("<<"),
    ShiftRight(">>"),
    Times("*"),
    Plus("+"),
    Minus("-"),
    ;

    fun apply(
        a: Long,
        b: Long,
    ): Long =
        when (this) {
            And -> a and b
            Or -> a or b
            Xor -> a xor b
            ShiftLeft -> a shl b.toInt()
            ShiftRight -> a shr b.toInt()
            Times -> a * b
            Plus -> a + b
            Minus -> a - b
        }

    /** Whether the right hand side is a distance to shift by rather than a second number */
    val shifts: Boolean get() = this == ShiftLeft || this == ShiftRight
}

/**
 * A card that makes up its own numbers every time it is asked.
 *
 * Converting between bases is not knowledge, it is a skill, and a card holding one fixed sum is
 * learned as a fact after four passes: the answer to `0b1011 0110` comes back before the working
 * does. So this card holds the exercise rather than an instance of it - which bases, how wide,
 * which operator - and rolls fresh numbers every time it comes round. The app can mark it
 * because it worked the answer out itself.
 *
 * What follows for the schedule: the progress belongs to the card, not to the numbers. The box
 * counts how often this kind of sum has been done, which is what is being learned.
 *
 * @param from the base the numbers are shown in
 * @param to the base the answer is written in
 * @param bits how wide the numbers are, which also caps what a shift keeps
 * @param title what the card says instead of the wording it would write itself
 */
data class GeneratedTask(
    val kind: GenKind,
    val from: Int = 2,
    val to: Int = 16,
    val bits: Int = 8,
    val op: BitOp = BitOp.And,
    val format: String = "%d",
    val title: String? = null,
    override val topic: String? = null,
    override val tags: List<String> = emptyList(),
) : Task {
    override val prompt: String get() = title ?: described

    // the exercise is what tells two of these apart; the numbers are different every time and
    // would make an identity that never matches itself
    override val identity: String get() = "gen:$kind:$from:$to:$bits:$op:$format"

    /** The wording the card writes for itself when it was not given one */
    private val described: String
        get() =
            when (kind) {
                GenKind.Convert -> "Schreibe die Zahl in ${baseName(toBase)}"
                GenKind.Bits -> "Rechne aus, Ergebnis in ${baseName(toBase)}"
                GenKind.Printf -> "Was gibt printf aus?"
            }

    /** How wide the numbers are, kept to what a card can sensibly ask and a Long can hold */
    val width: Int get() = bits.coerceIn(1, 32)

    // The bases are held as they were written and read back only through these, because a base
    // outside two to thirty-six has no digits to write a number in and would throw on the way
    // out. A card file is written by hand and a backup can be edited, so neither is trusted.
    val fromBase: Int get() = from.coerceIn(2, 36)

    val toBase: Int get() = to.coerceIn(2, 36)

    /** The widest number this card rolls */
    val mask: Long get() = (1L shl width) - 1

    /**
     * Whether answers to this card are written with the hexadecimal letters.
     *
     * Asked of the card and not of the numbers that came up, so the extra keys appearing never
     * tell the reader that this particular answer has a letter in it.
     */
    val wantsHex: Boolean
        get() =
            when (kind) {
                GenKind.Printf -> format.any { it == 'x' || it == 'X' }
                else -> toBase > 10
            }

    companion object {
        /** The bases a card may be written in. Ten needs no prefix; the others have one in C. */
        fun baseName(base: Int): String =
            when (base) {
                2 -> "Binär"
                8 -> "Oktal"
                16 -> "Hexadezimal"
                else -> "Basis $base"
            }

        /** What C puts in front of a number in this base, so a rolled number reads as one */
        fun prefixOf(base: Int): String =
            when (base) {
                2 -> "0b"
                8 -> "0"
                16 -> "0x"
                else -> ""
            }

        /** The conversions a format may use. Anything else would throw when it is formatted. */
        val FORMAT_CONVERSIONS = setOf('d', 'x', 'X', 'o')

        /** Whether a format string is one this card can safely hand to the formatter */
        fun formatIsSound(format: String): Boolean {
            val match = FORMAT.matchEntire(format.trim()) ?: return false
            return match.groupValues[1].single() in FORMAT_CONVERSIONS
        }

        /** `%x`, `%4x`, `%04X`, `%-8d`: flags and a width, then one of the conversions */
        private val FORMAT = Regex("""^%[-+ 0#]?\d{0,3}([a-zA-Z])$""")
    }
}

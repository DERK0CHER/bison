package net.bison.model

/**
 * How one attempt went.
 *
 * The exam's own three, plus the one a picked box produces. They are kept apart rather than
 * folded into right and wrong because the difference is the whole point of the training: a
 * syntax slip costs a quarter of a mark and a wrong idea costs a half, and a set where the
 * mistakes are nearly all syntax needs a different evening from one where they are not.
 */
enum class Rating {
    Right,
    Syntax,
    Logic,

    /** A single choice question answered with the wrong letter: no further diagnosis to make */
    Wrong,
    ;

    /** Whether this counts as knowing it, which is the only thing the schedule asks */
    val correct: Boolean get() = this == Right

    /** The word the export writes, which is the word the brief asked for */
    val written: String
        get() =
            when (this) {
                Right -> "richtig"
                Syntax -> "syntaxfehler"
                Logic -> "logikfehler"
                Wrong -> "falsch"
            }

    companion object {
        fun of(written: String): Rating? = entries.firstOrNull { it.written == written.trim().lowercase() }
    }
}

/**
 * One answer, kept for good.
 *
 * The history is unbounded on purpose: it is the only record of how the studying actually went,
 * a few hundred bytes a card, and throwing away the early attempts would take away exactly the
 * part that shows whether anything improved.
 *
 * @param at when it was answered, in milliseconds since 1970
 * @param seconds how long the card was on screen
 * @param typed what was written, word for word, where something was
 * @param rolled the numbers a parametrised card came up with, so an old attempt can be read back
 */
data class Attempt(
    val at: Long,
    val rating: Rating,
    val seconds: Long,
    val typed: String? = null,
    val rolled: Map<String, String> = emptyMap(),
)

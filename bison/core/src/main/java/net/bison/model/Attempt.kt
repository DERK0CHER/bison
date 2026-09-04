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

/**
 * Where a card stands, in the words the export uses.
 *
 * One right answer is not "ok": the brief asks for two in a row, and a card answered correctly
 * once is as likely to have been guessed as known. That leaves a gap in the wording - answered
 * once, correctly, is neither "last attempt wrong" nor "twice in a row" - and it is filed as
 * still open, because the point of the status is to say what is left to revise.
 *
 * It lives beside the attempts rather than on the card, because the export works out the same
 * answer from the same list without ever building a card, and two copies of this rule would be
 * two rules the first time one of them was edited.
 */
fun statusOf(history: List<Attempt>): String {
    val wrong = history.count { !it.rating.correct }
    val lastTwo = history.takeLast(2)
    return when {
        history.isEmpty() -> NEW
        wrong >= Card.LEECH_LAPSES -> LEECH
        lastTwo.size == 2 && lastTwo.all { it.rating.correct } -> DONE
        else -> OPEN
    }
}

/** Never answered */
const val NEW = "neu"

/** Answered wrongly often enough that the card itself is probably the problem */
const val LEECH = "leech"

/** Twice right in a row */
const val DONE = "ok"

/** Started and not yet safe */
const val OPEN = "offen"

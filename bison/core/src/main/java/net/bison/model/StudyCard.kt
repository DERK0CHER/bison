package net.bison.model

/**
 * What a card out of the written set asks, and therefore how it is asked.
 *
 * The kind is the card's own word for itself, taken straight from the file rather than worked
 * out here: the person writing the set knows whether a question wants a typed answer or a
 * turned-over card, and the app has no better way of guessing.
 */
enum class CardKind {
    /** A rule or a decision pattern. Turned over, nothing typed. */
    Logik,

    /** Write the line. Compared exactly, with the whitespace normalised. */
    Syntax,

    /** Write the line, with the numbers made up fresh every time it is asked. */
    Param,

    /** Three options, a, b and c. */
    Sc,

    /** Say what a piece of code does or leaves behind. Typed and compared. */
    Trace,

    /** Find the mistake and correct it. Turned over, because the answer is an argument. */
    Fehler,

    /**
     * Solved on paper against a clock.
     *
     * The exam gives a programming exercise a certain number of minutes, and the thing being
     * trained is doing it inside them. So this one is timed rather than compared: the clock runs
     * while it is worked on, and what gets kept is how long it took.
     */
    Zeit,
    ;

    companion object {
        fun of(word: String): CardKind? = entries.firstOrNull { it.name.equals(word.trim(), ignoreCase = true) }
    }
}

/**
 * One card of the written set.
 *
 * Three fields rather than two, which is the point of it: [prompt] is the task, [logic] is the
 * reasoning that gets to the answer, and [back] is the answer itself. Turning a card over shows
 * the reasoning first and the answer only on the second tap - because reading the answer teaches
 * nothing about how to reach it, and the reasoning is the part that transfers to the next
 * question.
 *
 * @param alternatives other answers that count, written in the file after a `|`
 * @param params for a [CardKind.Param] card, the ranges its numbers are rolled from
 */
data class StudyCard(
    val kind: CardKind,
    override val prompt: String,
    val back: String,
    val logic: String? = null,
    val alternatives: List<String> = emptyList(),
    val params: String? = null,
    /** What this one should take, in seconds, for a card that is being timed */
    val target: Int? = null,
    override val topic: String? = null,
    override val tags: List<String> = emptyList(),
) : Task {
    /** Whether the answer is typed out and compared, rather than turned over or picked */
    val isTyped: Boolean get() = kind == CardKind.Syntax || kind == CardKind.Param || kind == CardKind.Trace

    /** The word the file used for this card, which is the word the export writes back */
    override val type: String get() = kind.name.lowercase()

    /** Everything that counts as the answer, the main one first */
    val accepted: List<String> get() = listOf(back) + alternatives


    override val identity: String get() = "$kind\n$prompt\n$back"

    companion object {
        /** What a single choice card offers, in the order the file writes them */
        val OPTIONS = listOf('a', 'b', 'c')
    }
}

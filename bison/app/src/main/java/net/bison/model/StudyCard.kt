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
    /**
     * The three options of a single choice card, in the order they are written.
     *
     * Never shuffled: the answer names a letter - `b. Call by Reference` - so moving the options
     * would make the card contradict itself. It is also how the exam prints them.
     */
    val options: List<String> = emptyList(),
    /** What this one should take, in seconds, for a card that is being timed */
    val target: Int? = null,
    override val topic: String? = null,
    override val tags: List<String> = emptyList(),
) : Task {
    /** Whether the answer is typed out and compared, rather than turned over or picked */
    val isTyped: Boolean get() = kind == CardKind.Syntax || kind == CardKind.Param || kind == CardKind.Trace

    /**
     * What this card is filed under, so its history survives the set being imported again.
     *
     * A card is the same card when it asks the same thing in the same part of the set. Nothing
     * else could serve: the file has no ids in it, the order changes as the set is edited, and
     * the answer is the very thing that gets corrected. Rewording a question therefore starts
     * its history over, which was the stated trade.
     *
     * The front is taken as it is written, with the placeholders still in it, so a parametrised
     * card keeps one history rather than one per roll.
     */
    val cardId: String get() = sha1(topic.orEmpty() + "\n" + prompt)

    /** Everything that counts as the answer, the main one first */
    val accepted: List<String> get() = listOf(back) + alternatives

    /**
     * The letter of the right option.
     *
     * The file writes the answer as `b. Call by Reference; ...`, so the letter is the first
     * character of the answer and there is nowhere else it could be kept.
     */
    val correctOption: Char?
        get() =
            back
                .trimStart()
                .firstOrNull()
                ?.lowercaseChar()
                ?.takeIf { it in OPTIONS }

    override val identity: String get() = "$kind\n$prompt\n$back"

    companion object {
        /** What a single choice card offers, in the order the file writes them */
        val OPTIONS = listOf('a', 'b', 'c')

        /**
         * A card's own name for itself.
         *
         * SHA-1 because it is a name, not a secret: what is wanted is that the same question in
         * the same part comes out as the same forty characters on every device and every import.
         */
        fun sha1(text: String): String =
            java.security.MessageDigest
                .getInstance("SHA-1")
                .digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

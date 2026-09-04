package net.bison.model

/**
 * What a card asks. Multiple choice was the only kind; writing code is the other one that
 * carries marks in an exam, so the card no longer assumes it holds a question with options.
 *
 * The front is two things rather than one: the task in prose, and whatever code is already
 * written on the card. They are kept apart because they are read differently - prose in the
 * text face, code in a monospaced one, where a `*` is a pointer and the indentation means
 * something.
 */
sealed interface Task {
    /** The task text shown on the front, in prose. May be empty when the card is only code. */
    val prompt: String

    /**
     * The code already on the front: a signature to fill in, or a program whose output is
     * asked for. Null when the card has none.
     */
    val given: String? get() = null

    /**
     * The name of a picture shown on the front, or null when there is none.
     *
     * A name rather than a path: the picture lives in the app's own store, because the file
     * dialog a card file arrives through gives no way back to the directory it came from.
     */
    val image: String? get() = null

    /** Which part of the set this belongs to */
    val topic: String?

    /** Free labels: the exam it came from, the kind of exercise, whatever is worth filtering on */
    val tags: List<String>

    /**
     * What tells this card apart from the others.
     *
     * The prompt did that job alone until a card was allowed to be nothing but code: such a
     * card has no prompt, and two of them would look like the same card to anything matching on
     * it - which is how a finished session would write one card's progress into another.
     */
    val identity: String get() = prompt + "\n" + given.orEmpty() + "\n" + image.orEmpty()

    /**
     * What the card is filed under, so its history survives the set being imported again.
     *
     * A card is the same card when it asks the same thing in the same part of the set. Nothing
     * else could serve: the file has no ids in it, the order changes as the set is edited, and
     * the answer is the very thing that gets corrected - filing on the answer would throw the
     * history away the moment a typo in it was fixed. Rewording a question therefore starts its
     * history over, which is the stated trade.
     *
     * The front is taken as it is written, with the placeholders still in it, so a parametrised
     * card keeps one history rather than one per roll.
     */
    val filedAs: String get() = prompt

    /**
     * The card's own name: the same forty characters on every device and every import.
     *
     * This is what the export writes, what a re-import matches on, and what two machines agree
     * about when they sync - so it has to follow from the card alone, and never be handed out
     * by whichever of them happened to see the card first.
     */
    val cardId: String get() = sha1(topic.orEmpty() + "\n" + filedAs)

    /** The kind of card this is, in the word the file and the export use for it */
    val type: String

    /** One line naming the card, for a list or a preview */
    val label: String
        get() =
            firstLine(prompt)
                .ifEmpty { firstLine(given.orEmpty()) }
                .ifEmpty { image.orEmpty() }
}

/**
 * A name, not a secret.
 *
 * SHA-1 is chosen for exactly that: what is wanted is that the same question in the same part of
 * the set comes out as the same forty characters wherever it is read, on the phone and at the
 * desk, this term and next.
 */
fun sha1(text: String): String =
    java.security.MessageDigest
        .getInstance("SHA-1")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

/** The first line with anything on it, trimmed; empty when there is none */
private fun firstLine(text: String): String = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

/**
 * A multiple choice question.
 *
 * The answers are plain strings. There is no A/B/C/D label: the answer itself is written on the
 * box the reader taps, so a letter beside it would name something already in front of them.
 *
 * @param given a program whose output the answers offer, for the trace questions an exam asks
 *   with exactly three options
 * @param correctIndex index into [answers] of the single right one
 */
data class Question(
    override val prompt: String,
    val answers: List<String>,
    val correctIndex: Int,
    override val given: String? = null,
    override val image: String? = null,
    /**
     * The pattern that gets to the answer, shown once one has been picked.
     *
     * A written set carries this on every card, and it is the half that transfers: knowing that
     * `b` was right teaches nothing about the next question, knowing why does. It is shown after
     * the pick rather than before it, so it never gives the answer away.
     */
    val logic: String? = null,
    /** Why the right answer is the right one, as the set words it */
    val reason: String? = null,
    override val topic: String? = null,
    override val tags: List<String> = emptyList(),
) : Task {
    override val type: String get() = "sc"

    val correctAnswer: String get() = answers[correctIndex]
}

/**
 * A card answered away from the phone, and marked by the reader.
 *
 * The exam asks for an activity chart drawn on paper, and no app is going to compare a drawing.
 * So this card shows its front, waits to be asked for the answer, and then shows it: a picture,
 * or a few lines of prose. Whether that matches what was drawn is the reader's own call - the
 * same call the marking of a written answer already leaves to them.
 *
 * It is also the plain flashcard the app did not have: a front, a back, and two buttons.
 *
 * @param answerImage the picture of the answer
 * @param answer the answer in prose, for a card whose answer is not a picture
 */
data class SketchTask(
    override val prompt: String,
    override val given: String? = null,
    override val image: String? = null,
    val answerImage: String? = null,
    val answer: String? = null,
    override val topic: String? = null,
    override val tags: List<String> = emptyList(),
) : Task {
    override val type: String get() = "sketch"

    override val identity: String
        get() = prompt + "\n" + given.orEmpty() + "\n" + image.orEmpty() + "\n" + answerImage.orEmpty()

    // the answer is left out, as everywhere: a card whose answer picture is replaced by a better
    // one is the same card, and should keep what it knows about itself
    override val filedAs: String get() = prompt + "\n" + given.orEmpty() + "\n" + image.orEmpty()

    /** Whether there is anything to show when the answer is asked for */
    val hasAnswer: Boolean get() = answerImage != null || !answer.isNullOrBlank()
}

/**
 * Write the code yourself.
 *
 * The front is the task and usually a signature or a body with [GAP] where the work goes, held
 * in [given]. The back is the model answer, kept as text with its line breaks: the lines are the
 * unit every mode works in - dragged into order in the easiest one, typed against on one line in
 * the shortest, compared line by line in the hardest.
 *
 * @param alternatives other model answers that count as right, for the same thing written
 *   differently
 */
data class CodeTask(
    override val prompt: String,
    val solution: String,
    override val given: String? = null,
    override val image: String? = null,
    val alternatives: List<String> = emptyList(),
    override val topic: String? = null,
    override val tags: List<String> = emptyList(),
) : Task {
    override val type: String get() = "code"

    // the answer is deliberately not in here, although it is in the identity: a model answer
    // that gets a typo fixed is the same exercise, and its history should survive the fix
    override val filedAs: String get() = prompt + "
" + given.orEmpty()

    /** The model answer as lines, with blank lines at either end dropped */
    val solutionLines: List<String> get() = lines(solution)

    /** Every accepted answer, the main one first */
    val accepted: List<String> get() = listOf(solution) + alternatives

    /** One line to write out, so the whole editor and its marking would be overkill */
    val isOneLiner: Boolean get() = solutionLines.size == 1

    // the solution belongs to the identity as well: two cards may ask the same thing of two
    // different functions, and the front alone would not tell them apart
    override val identity: String get() = prompt + "\n" + given.orEmpty() + "\n" + image.orEmpty() + "\n" + solution

    companion object {
        /** What a gap in the front looks like, so the task can say where the work goes */
        const val GAP = ">>> Hier fehlt was"

        fun lines(text: String): List<String> =
            text
                .replace("\r\n", "\n")
                .split("\n")
                .dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
    }
}

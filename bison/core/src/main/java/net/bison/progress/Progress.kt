package net.bison.progress

import net.bison.domain.StudySession
import net.bison.model.Attempt
import net.bison.model.Card
import net.bison.model.Deck
import net.bison.model.Rating
import net.bison.model.StudyCard
import net.bison.model.statusOf
import org.json.JSONObject
import java.time.Instant

/**
 * What has been learned about one card, wherever it was learned.
 *
 * This is the unit that travels: between an export and a re-import, and between the phone and
 * the desktop. It is deliberately not a [Card] - a card carries a schedule and a box, which are
 * one machine's opinion of the same history, and two opinions cannot be merged into one. The
 * attempts can: they are things that happened, at a time, and two lists of them have a union.
 *
 * @param id the card's own name, worked out from the card and identical on every machine
 * @param front as it was written, with any placeholders still in it
 * @param target what this card should take in seconds, for a timed one
 */
data class CardProgress(
    val id: String,
    val subsection: String?,
    val type: String,
    val front: String,
    val target: Int? = null,
    val attempts: List<Attempt> = emptyList(),
) {
    val tries: Int get() = attempts.size

    fun count(rating: Rating): Int = attempts.count { it.rating == rating }

    /** The quickest it has ever been done, ignoring the attempts that were not timed */
    val best: Long? get() = attempts.filter { it.seconds > 0 }.minOfOrNull { it.seconds }

    val last: Attempt? get() = attempts.lastOrNull()

    /** Where it stands, by the same rule the app itself uses */
    val status: String get() = statusOf(attempts)

    /** The two of them, with every attempt either has and nothing twice */
    fun mergedWith(other: CardProgress): CardProgress =
        copy(
            // whichever of the two actually knows what the card says. A machine that has only
            // ever received this card's history, and never imported the set it belongs to, has
            // the attempts and blanks for the rest.
            subsection = subsection ?: other.subsection,
            front = front.ifEmpty { other.front },
            type = type.ifEmpty { other.type },
            target = target ?: other.target,
            attempts = union(attempts, other.attempts),
        )
}

/**
 * The learned-so-far, as a file and as something two machines can agree on.
 *
 * The export the brief asks for and the payload the sync sends are the same thing, and that is
 * not a shortcut - it is the reason the sync can be trusted. An export that could be read back
 * without losing anything is exactly a payload that can be sent without losing anything, and
 * writing the two separately would mean testing one of them and hoping about the other.
 *
 * Every attempt is a fact with a timestamp on it, so merging is a union rather than a decision.
 * Nothing here has to work out which machine is "newer", which is the question every naive sync
 * gets wrong: study ten cards on the train and five at the desk and both are true, and the
 * answer is fifteen rather than whichever device happened to connect last.
 */
object Progress {
    /** What this format is called in the file, so a later change can be told apart */
    const val VERSION = 1

    /** Everything the decks know, in the travelling form */
    fun of(decks: List<Deck>): List<CardProgress> =
        decks.flatMap { deck ->
            deck.subtopics.flatMap { subtopic ->
                subtopic.cards.map { card ->
                    CardProgress(
                        id = card.task.cardId,
                        subsection = card.task.topic ?: subtopic.name,
                        type = card.task.type,
                        front = card.task.filedAs,
                        target = target(card),
                        attempts = card.history,
                    )
                }
            }
        }

    /**
     * Two sets of progress, with everything either of them has.
     *
     * Cards are matched on their id, attempts on when they happened. Two attempts to the same
     * card in the same millisecond on two machines is not a thing that occurs; if it somehow
     * did, one of them would be dropped, and one flashcard answer is not worth a more careful
     * rule than that.
     */
    fun merge(
        mine: List<CardProgress>,
        theirs: List<CardProgress>,
    ): List<CardProgress> {
        val merged = LinkedHashMap<String, CardProgress>()
        for (card in mine) merged[card.id] = card
        for (card in theirs) {
            val already = merged[card.id]
            merged[card.id] = already?.mergedWith(card) ?: card
        }
        return merged.values.toList()
    }

    /**
     * Writes the progress into the decks, and works out again everything that follows from it.
     *
     * The box and the seconds are not carried across and not merged: they are conclusions drawn
     * from the attempts, so they are drawn again here from the merged list. That is what makes
     * two devices safe to use in the same evening - a box that was sent would have to be picked
     * between, and picking would throw away one of the two evenings.
     *
     * The review date, the ease and the lapses stay as they are on this machine. They are the
     * spaced repetition part, which the brief put outside Phase 1 altogether; carrying them
     * would mean deciding what a review means when it happened on the other device, and there is
     * nothing to gain from deciding that now.
     */
    fun applyTo(
        decks: List<Deck>,
        progress: List<CardProgress>,
    ): List<Deck> {
        if (progress.isEmpty()) return decks
        val byId = progress.associateBy { it.id }
        return decks.map { deck ->
            deck.copy(
                subtopics =
                    deck.subtopics.map { subtopic ->
                        subtopic.copy(cards = subtopic.cards.map { card -> apply(card, byId[card.task.cardId]) })
                    },
            )
        }
    }

    private fun apply(
        card: Card,
        progress: CardProgress?,
    ): Card {
        if (progress == null) return card
        val history = union(card.history, progress.attempts)
        if (history.size == card.history.size) return card
        val replayed = history.fold(card.copy(box = 0)) { so, attempt -> so.answered(attempt.rating.correct) }
        return replayed.copy(
            history = history,
            seconds = history.sumOf { it.seconds.coerceIn(0, StudySession.MAX_SECONDS) },
        )
    }

    // region the file

    /**
     * The progress as JSON, written out by hand rather than by a library.
     *
     * Two reasons, and the second is the one that matters. It is a file somebody may well open,
     * so the keys should be in the order a person would read them; and the two JSON libraries
     * this runs against - Android's own and the one from Maven - hold their keys in different
     * kinds of map, so the same progress would come out as different bytes on the phone and at
     * the desk. Nothing would break, but a file that differs from itself for no reason is a file
     * nobody can diff.
     */
    fun json(progress: List<CardProgress>): String {
        val out = StringBuilder()
        out.append("{\n")
        out.append("  \"format\": \"bison-fortschritt\",\n")
        out.append("  \"version\": ").append(VERSION).append(",\n")
        out.append("  \"geschrieben\": ").append(quote(Instant.now().toString())).append(",\n")
        out.append("  \"karten\": [\n")
        for ((at, card) in progress.withIndex()) {
            out.append("    {\n")
            out.append("      \"id\": ").append(quote(card.id)).append(",\n")
            out.append("      \"subsection\": ").append(quote(card.subsection)).append(",\n")
            out.append("      \"type\": ").append(quote(card.type)).append(",\n")
            out.append("      \"front\": ").append(quote(card.front)).append(",\n")
            if (card.target != null) out.append("      \"ziel\": ").append(card.target).append(",\n")
            out.append("      \"versuche\": [")
            if (card.attempts.isEmpty()) {
                out.append("]\n")
            } else {
                out.append("\n")
                for ((n, attempt) in card.attempts.withIndex()) {
                    out.append("        ").append(attemptJson(attempt))
                    out.append(if (n == card.attempts.lastIndex) "\n" else ",\n")
                }
                out.append("      ]\n")
            }
            out.append(if (at == progress.lastIndex) "    }\n" else "    },\n")
        }
        out.append("  ]\n")
        out.append("}\n")
        return out.toString()
    }

    private fun attemptJson(attempt: Attempt): String {
        val out = StringBuilder("{")
        out.append("\"zeitpunkt\": ").append(quote(Instant.ofEpochMilli(attempt.at).toString()))
        out.append(", \"ergebnis\": ").append(quote(attempt.rating.written))
        out.append(", \"sekunden\": ").append(attempt.seconds)
        // written word for word, because "what did I actually type" is the whole question when a
        // card was marked wrong and the reader disagrees with it
        if (attempt.typed != null) out.append(", \"eingabe\": ").append(quote(attempt.typed))
        if (attempt.rolled.isNotEmpty()) {
            out.append(", \"werte\": {")
            out.append(
                attempt.rolled.entries.joinToString(", ") { (key, value) -> quote(key) + ": " + quote(value) },
            )
            out.append("}")
        }
        out.append("}")
        return out.toString()
    }

    /**
     * Reads a progress file back.
     *
     * Anything it cannot make sense of is left out rather than guessed at, and a file that is not
     * one of these at all reads as nothing - so a mis-picked file changes nothing, which is the
     * only safe answer when the alternative is overwriting a term's work.
     */
    fun read(text: String): List<CardProgress> {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val cards = root.optJSONArray("karten") ?: return emptyList()
        val found = mutableListOf<CardProgress>()
        for (i in 0 until cards.length()) {
            val json = cards.optJSONObject(i) ?: continue
            val id = json.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val attempts = json.optJSONArray("versuche")
            val read = mutableListOf<Attempt>()
            for (n in 0 until (attempts?.length() ?: 0)) {
                val one = attempts?.optJSONObject(n) ?: continue
                val at = millis(one.optString("zeitpunkt")) ?: continue
                val rolled = one.optJSONObject("werte")
                read +=
                    Attempt(
                        at = at,
                        rating = Rating.of(one.optString("ergebnis")) ?: Rating.Wrong,
                        seconds = one.optLong("sekunden", 0),
                        typed = one.optString("eingabe").takeIf { it.isNotEmpty() },
                        rolled =
                            rolled
                                ?.keys()
                                ?.asSequence()
                                ?.associateWith { rolled.optString(it) }
                                .orEmpty(),
                    )
            }
            found +=
                CardProgress(
                    id = id,
                    subsection = json.optString("subsection").takeIf { it.isNotEmpty() },
                    type = json.optString("type"),
                    front = json.optString("front"),
                    target = json.optInt("ziel", 0).takeIf { it > 0 },
                    attempts = read.sortedBy { it.at },
                )
        }
        return found
    }

    /**
     * A timestamp, from the ISO form the file writes.
     *
     * A plain number of milliseconds is also read. Nothing this app writes produces one, but a
     * progress file is a text file that somebody may well have edited, and refusing to read a
     * timestamp that is perfectly clear would be a poor way to repay that.
     */
    private fun millis(written: String): Long? {
        val text = written.trim().takeIf { it.isNotEmpty() } ?: return null
        runCatching { return Instant.parse(text).toEpochMilli() }
        return text.toLongOrNull()?.takeIf { it > 0 }
    }

    // endregion

    // region the table

    /**
     * The same progress as a table, one row per card.
     *
     * Semicolons rather than commas: this gets opened in a spreadsheet on a German machine,
     * where the comma is the decimal point and a comma-separated file lands in one column.
     *
     * Sorted by part, and inside a part by how much work is left: the ones still open first, the
     * untouched ones next, the finished ones last. A revision list is read from the top, so what
     * is left has to be at the top.
     */
    fun csv(progress: List<CardProgress>): String {
        val out = StringBuilder()
        out.append(COLUMNS.joinToString(";")).append("\n")
        val rows =
            progress.sortedWith(
                compareBy<CardProgress> { it.subsection.orEmpty() }
                    .thenBy { ORDER.indexOf(it.status).takeIf { at -> at >= 0 } ?: ORDER.size }
                    .thenBy { it.front },
            )
        for (card in rows) {
            val last = card.last
            out.append(
                listOf(
                    card.subsection.orEmpty(),
                    card.type,
                    shorten(card.front),
                    card.tries.toString(),
                    card.count(Rating.Right).toString(),
                    card.count(Rating.Syntax).toString(),
                    card.count(Rating.Logic).toString(),
                    last?.rating?.written.orEmpty(),
                    last?.let { Instant.ofEpochMilli(it.at).toString() }.orEmpty(),
                    card.best?.toString().orEmpty(),
                    last?.seconds?.takeIf { it > 0 }?.toString().orEmpty(),
                    card.target?.toString().orEmpty(),
                    card.status,
                ).joinToString(";") { cell(it) },
            )
            out.append("\n")
        }
        return out.toString()
    }

    /**
     * One cell, quoted only where it has to be.
     *
     * The front of a card is prose with semicolons and quotation marks in it, and half of it is
     * C, which is nothing but semicolons. Unquoted, one such card would shift every column after
     * it by one and the table would be silently wrong rather than visibly broken.
     */
    private fun cell(text: String): String {
        if (text.none { it == ';' || it == '"' || it == '\n' || it == '\r' }) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

    /** Enough of the front to recognise the card by, on one line */
    private fun shorten(front: String): String {
        val flat = front.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= FRONT_WIDTH) flat else flat.take(FRONT_WIDTH - 1).trimEnd() + "…"
    }

    private val COLUMNS =
        listOf(
            "subsection",
            "type",
            "front",
            "versuche",
            "richtig",
            "syntaxfehler",
            "logikfehler",
            "letzte_bewertung",
            "letzter_versuch",
            "bestzeit_s",
            "letzte_zeit_s",
            "ziel_s",
            "status",
        )

    /** Worst first: the ones being lost again, then the open ones, the new ones, the done ones */
    private val ORDER = listOf("leech", "offen", "neu", "ok")

    private const val FRONT_WIDTH = 60

    // endregion

    /** What a timed card is meant to take, which only that kind of card has */
    private fun target(card: Card): Int? = (card.task as? StudyCard)?.target
}

/**
 * Two lists of attempts, with everything either has and nothing twice.
 *
 * The time is the key. An attempt is a thing that happened at a moment, so the same moment is
 * the same attempt; anything else - the same rating, the same input - is two people writing the
 * same answer twice, which is two attempts and should count as two.
 */
internal fun union(
    mine: List<Attempt>,
    theirs: List<Attempt>,
): List<Attempt> {
    if (theirs.isEmpty()) return mine
    if (mine.isEmpty()) return theirs
    val byTime = LinkedHashMap<Long, Attempt>()
    for (attempt in mine) byTime[attempt.at] = attempt
    for (attempt in theirs) byTime.putIfAbsent(attempt.at, attempt)
    return byTime.values.sortedBy { it.at }
}

/**
 * A JSON string, escaped, or the word null where there is nothing.
 *
 * The card fronts run to several lines and are full of quotation marks and backslashes, this
 * being a set about C and MATLAB; a writer that did not escape them would produce a file that
 * cannot be read back, and the first anybody would hear of it is a lost history.
 */
private fun quote(text: String?): String {
    if (text == null) return "null"
    val out = StringBuilder("\"")
    for (c in text) {
        when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' -> out.append("\\u%04x".format(c.code))
            else -> out.append(c)
        }
    }
    return out.append('"').toString()
}

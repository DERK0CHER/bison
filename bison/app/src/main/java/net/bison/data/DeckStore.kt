package net.bison.data

import android.content.Context
import net.bison.model.BitOp
import net.bison.model.Card
import net.bison.model.CardKind
import net.bison.model.CodeTask
import net.bison.model.Deck
import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.SketchTask
import net.bison.model.StudyCard
import net.bison.model.Subtopic
import net.bison.model.Task
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Decks on disk, as one JSON file.
 *
 * A question set is a few hundred kilobytes at most and is written once per session, so a plain
 * file is enough; it keeps the app free of a database and the build free of another plugin.
 */
class DeckStore(
    private val file: File,
) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    fun load(): List<Deck> {
        if (!file.exists()) return emptyList()
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            // a corrupt file must not make the app unusable: start over rather than crash
            android.util.Log.w(TAG, "could not read decks, starting empty", e)
            emptyList()
        }
    }

    fun save(decks: List<Deck>) {
        try {
            file.writeText(encode(decks))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not write decks", e)
        }
    }

    /** The whole state as JSON, for writing to a file the learner keeps */
    fun export(decks: List<Deck>): String = encode(decks)

    /**
     * Reads a backup back in.
     *
     * A topic already here is replaced by its backed up self and anything not in the backup is
     * left alone, so restoring on a device that has since gained a topic does not throw it away.
     * A file that reads as nothing at all changes nothing.
     */
    fun restore(
        current: List<Deck>,
        text: String,
    ): List<Deck> {
        val loaded = runCatching { decode(text) }.getOrNull().orEmpty()
        if (loaded.isEmpty()) return current
        val byId = loaded.associateBy { it.id }
        val kept = current.map { byId[it.id] ?: it }
        val added = loaded.filterNot { backup -> current.any { it.id == backup.id } }
        return kept + added
    }

    private fun encode(decks: List<Deck>): String {
        val array = JSONArray()
        for (deck in decks) {
            val subtopics = JSONArray()
            for (subtopic in deck.subtopics) {
                subtopics.put(
                    JSONObject()
                        .put("id", subtopic.id)
                        .put("name", subtopic.name)
                        .put("cards", encodeCards(subtopic.cards)),
                )
            }
            array.put(
                JSONObject()
                    .put("id", deck.id)
                    .put("name", deck.name)
                    .put("subtopics", subtopics),
            )
        }
        return JSONObject().put("version", VERSION).put("decks", array).toString()
    }

    private fun encodeCards(cards: List<Card>): JSONArray {
        val array = JSONArray()
        for (card in cards) {
            val json =
                JSONObject()
                    .put("prompt", card.task.prompt)
                    .put("box", card.box)
                    .put("hard", card.hard)
                    .put("tags", JSONArray().also { tags -> card.task.tags.forEach(tags::put) })
            // the schedule, written only when there is one: a card that has never been finished
            // carries the defaults and there is no sense filling the file with them
            if (card.due > 0) json.put("due", card.due)
            if (card.interval > 0) json.put("interval", card.interval)
            if (card.ease != Card.EASE_START) json.put("ease", card.ease)
            if (card.lapses > 0) json.put("lapses", card.lapses)
            if (card.seconds > 0) json.put("seconds", card.seconds)
            card.task.topic?.let { json.put("topic", it) }
            card.task.given?.let { json.put("given", it) }
            card.task.image?.let { json.put("image", it) }
            when (val task = card.task) {
                is Question -> {
                    val answers = JSONArray()
                    task.answers.forEach(answers::put)
                    json
                        .put("type", CHOICE)
                        .put("answers", answers)
                        .put("correctIndex", task.correctIndex)
                }

                is CodeTask ->
                    json
                        .put("type", CODE)
                        .put("solution", task.solution)
                        .put("alt", JSONArray().also { alt -> task.alternatives.forEach(alt::put) })
                        .put("sorted", card.sorted)

                is StudyCard -> {
                    json
                        .put("type", STUDY)
                        .put("kind", task.kind.name)
                        .put("back", task.back)
                        .put("alt", JSONArray().also { alt -> task.alternatives.forEach(alt::put) })
                    task.logic?.let { json.put("logik", it) }
                    task.params?.let { json.put("params", it) }
                }

                is SketchTask -> {
                    json.put("type", SKETCH)
                    task.answerImage?.let { json.put("answerImage", it) }
                    task.answer?.let { json.put("answer", it) }
                }

                is GeneratedTask -> {
                    json
                        .put("type", GEN)
                        .put("kind", task.kind.name)
                        .put("from", task.from)
                        .put("to", task.to)
                        .put("bits", task.bits)
                        .put("op", task.op.name)
                        .put("format", task.format)
                    // the wording it would write for itself is in "prompt" already; only one
                    // given to it by hand has to survive
                    task.title?.let { json.put("title", it) }
                }
            }
            array.put(json)
        }
        return array
    }

    private fun decode(text: String): List<Deck> {
        val decks = mutableListOf<Deck>()
        val array = JSONObject(text).optJSONArray("decks") ?: return decks
        for (i in 0 until array.length()) {
            val deckJson = array.optJSONObject(i) ?: continue
            val id = deckJson.optString("id")
            val name = deckJson.optString("name")
            val subtopicsJson = deckJson.optJSONArray("subtopics")
            val subtopics =
                if (subtopicsJson != null) {
                    decodeSubtopics(subtopicsJson)
                } else {
                    // written before topics existed: the whole deck was one flat list of cards
                    listOf(Subtopic(id = "$id-all", name = name, cards = decodeCards(deckJson.optJSONArray("cards"))))
                }
            decks += Deck(id = id, name = name, subtopics = subtopics)
        }
        return decks
    }

    private fun decodeSubtopics(array: JSONArray): List<Subtopic> {
        val subtopics = mutableListOf<Subtopic>()
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            subtopics +=
                Subtopic(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    cards = decodeCards(json.optJSONArray("cards")),
                )
        }
        return subtopics
    }

    private fun decodeCards(array: JSONArray?): List<Card> {
        if (array == null) return emptyList()
        val cards = mutableListOf<Card>()
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val task = decodeTask(json) ?: continue
            cards +=
                Card(
                    task = task,
                    box = json.optInt("box", 0),
                    hard = json.optBoolean("hard", false),
                    sorted = json.optInt("sorted", 0),
                    due = json.optLong("due", 0),
                    interval = json.optInt("interval", 0),
                    ease = json.optDouble("ease", Card.EASE_START),
                    lapses = json.optInt("lapses", 0),
                    seconds = json.optLong("seconds", 0),
                )
        }
        return cards
    }

    /** A card written before there were card types has no "type" and is a question */
    private fun decodeTask(json: JSONObject): Task? {
        val given = json.optString("given").takeIf { it.isNotEmpty() }
        val image = json.optString("image").takeIf { it.isNotEmpty() }
        val prompt = json.optString("prompt")
        val type = json.optString("type").ifEmpty { CHOICE }
        // a card that is nothing but code or a picture has no prompt, and that is not a broken
        // card; a generated one has none of its own at all
        if (type != GEN && prompt.isEmpty() && given == null && image == null) return null
        val topic = json.optString("topic").takeIf { it.isNotEmpty() }
        val tags = decodeStrings(json.optJSONArray("tags"))
        return when (type) {
            CODE -> {
                val solution = json.optString("solution").takeIf { it.isNotEmpty() } ?: return null
                CodeTask(
                    prompt = prompt,
                    solution = solution,
                    given = given,
                    image = image,
                    alternatives = decodeStrings(json.optJSONArray("alt")),
                    topic = topic,
                    tags = tags,
                )
            }

            STUDY -> {
                val kind = CardKind.entries.firstOrNull { it.name == json.optString("kind") } ?: return null
                val back = json.optString("back").takeIf { it.isNotEmpty() } ?: return null
                StudyCard(
                    kind = kind,
                    prompt = prompt,
                    back = back,
                    logic = json.optString("logik").takeIf { it.isNotEmpty() },
                    alternatives = decodeStrings(json.optJSONArray("alt")),
                    params = json.optString("params").takeIf { it.isNotEmpty() },
                    topic = topic,
                    tags = tags,
                )
            }

            SKETCH ->
                SketchTask(
                    prompt = prompt,
                    given = given,
                    image = image,
                    answerImage = json.optString("answerImage").takeIf { it.isNotEmpty() },
                    answer = json.optString("answer").takeIf { it.isNotEmpty() },
                    topic = topic,
                    tags = tags,
                ).takeIf { it.hasAnswer }

            CHOICE -> {
                val answers = decodeStrings(json.optJSONArray("answers"))
                val correctIndex = json.optInt("correctIndex", -1)
                if (answers.size < 2 || correctIndex !in answers.indices) return null
                Question(
                    prompt = prompt,
                    answers = answers,
                    correctIndex = correctIndex,
                    given = given,
                    image = image,
                    topic = topic,
                    tags = tags,
                )
            }

            GEN -> {
                // an unknown kind or operator means a file from a newer version or an edited
                // one; the card is dropped rather than guessed at
                val kind = GenKind.entries.firstOrNull { it.name == json.optString("kind") } ?: return null
                val op = BitOp.entries.firstOrNull { it.name == json.optString("op") } ?: return null
                GeneratedTask(
                    kind = kind,
                    from = json.optInt("from", 2),
                    to = json.optInt("to", 16),
                    bits = json.optInt("bits", 8),
                    op = op,
                    format = json.optString("format").ifEmpty { "%d" },
                    title = json.optString("title").takeIf { it.isNotEmpty() },
                    topic = topic,
                    tags = tags,
                )
            }

            else -> null
        }
    }

    private fun decodeStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it) }
    }

    companion object {
        private const val FILE_NAME = "decks.json"
        private const val TAG = "DeckStore"

        /**
         * 1 was a flat list of cards per deck, 2 groups them into subtopics, 3 gives every card
         * a type so a card can hold code to write rather than answers to pick, 4 keeps the code
         * on a card's front apart from its prose so it can be set as code, 5 adds the card that
         * makes its own numbers up, 6 the pictures and the card that is answered on paper, 7 the
         * date a finished card comes back on.
         *
         * A card saved by 3 has all of its front in the prompt, which still reads and still
         * works; it is set as prose until the file it came from is imported again. One saved by
         * 6 has no date, which reads as due now - so an old file simply asks everything again,
         * which is the right answer for a set that has been sitting there since before there
         * were dates at all.
         */
        private const val VERSION = 8

        private const val CHOICE = "choice"
        private const val CODE = "code"
        private const val GEN = "gen"
        private const val SKETCH = "sketch"
        private const val STUDY = "study"
    }
}

package net.bison.importer

import net.bison.model.BitOp
import net.bison.model.CodeTask
import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.SketchTask
import net.bison.model.Task

/**
 * Reads cards written at a desk rather than on a phone.
 *
 * Code does not survive being hand-written into JSON: every brace has to be escaped and every
 * line break becomes a `\n`, which is unreadable and unwritable. So the format is plain text with
 * fenced blocks, which is what code already looks like everywhere else.
 *
 * ```
 * type: code
 * topic: Verkettete Listen
 * tags: WS24, Node_Delete
 * front:
 * ```c
 * void node_delete(node_t *n) {
 * >>> Hier fehlt was
 * }
 * ```
 * back:
 * ```c
 *     free(n->data);
 *     free(n);
 * ```
 * ---
 * type: choice
 * front: Was ergibt 1 << 3?
 * - 4
 * - *8
 * - 16
 * ```
 *
 * A field's value is the rest of its line, or - when that is empty - the fenced block that
 * follows. Cards are separated by a line holding nothing but `---`. `alt:` may appear more than
 * once, for the same answer written differently. A block that cannot be read is skipped and
 * counted rather than failing the whole file.
 *
 * A fenced block under `front:` is the card's code rather than its prose, whether or not the
 * `front:` line itself said anything, and it is shown in a monospaced face. Writing `given:`
 * spells the same thing out. So a card may be all prose, all code, or a line of prose over a
 * block of code.
 */
object CardFileParser {
    data class Found(
        val tasks: List<Task>,
        val skipped: Int,
    )

    fun parse(text: String): Found {
        val tasks = mutableListOf<Task>()
        var skipped = 0
        for (block in split(text)) {
            val task = readCard(block)
            if (task != null) tasks += task else skipped++
        }
        return Found(tasks, skipped)
    }

    /** Splits on a lone `---`, but never inside a fenced block */
    private fun split(text: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var fenced = false
        for (raw in text.replace("\r\n", "\n").split("\n")) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith(FENCE)) fenced = !fenced
            if (!fenced && line.trim() == SEPARATOR) {
                if (current.any { it.isNotBlank() }) blocks += current
                current = mutableListOf()
            } else {
                current += line
            }
        }
        if (current.any { it.isNotBlank() }) blocks += current
        return blocks
    }

    private fun readCard(lines: List<String>): Task? {
        val fields = mutableMapOf<String, String>()
        val alternatives = mutableListOf<String>()
        val options = mutableListOf<String>()
        var correct = -1

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val option = OPTION.find(line)
            if (option != null) {
                val body = option.groupValues[1].trim()
                if (body.startsWith("*")) correct = options.size
                options += body.removePrefix("*").trim()
                index++
                continue
            }

            val field = FIELD.find(line)
            if (field == null) {
                index++
                continue
            }
            val key = field.groupValues[1].lowercase()
            val inline = field.groupValues[2].trim()
            if (inline.isNotEmpty()) {
                if (key == ALT) alternatives += inline else fields[key] = inline
                index++
                // "front: Vervollständige node_delete" with the signature in a block underneath:
                // the line says what to do, the block is the code, and both are the front
                if (key == FRONT) {
                    val (code, after) = readBlock(lines, index)
                    if (code.isNotEmpty()) {
                        fields[GIVEN] = code
                        index = after
                    }
                }
                continue
            }

            // an empty value means the fenced block underneath is the value. Under front: that
            // block is code rather than prose - a fence is what code is written in - so it goes
            // where the card keeps its code and is set in a monospaced face when it is shown.
            val (block, next) = readBlock(lines, index + 1)
            val target = if (key == FRONT) GIVEN else key
            if (key == ALT) alternatives += block else fields[target] = block
            index = next
        }

        val prompt = fields[FRONT]?.takeIf { it.isNotBlank() }.orEmpty()
        val given = fields[GIVEN]?.takeIf { it.isNotBlank() }
        val topic = fields[TOPIC]?.takeIf { it.isNotBlank() }
        val tags =
            fields[TAGS]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        val back = fields[BACK]?.takeIf { it.isNotBlank() }
        val image = fields[IMAGE]?.takeIf { it.isNotBlank() }
        val answerImage = fields[ANSWERIMAGE]?.takeIf { it.isNotBlank() }
        val answer = fields[ANSWER]?.takeIf { it.isNotBlank() }
        // the type may be left out: a card with options is a question, one with a back is code,
        // and one whose answer is a picture or a paragraph is answered on paper
        val type =
            fields[TYPE]?.lowercase() ?: when {
                answerImage != null || answer != null -> SKETCH
                back != null -> CODE
                else -> CHOICE
            }
        // a card has to say something on its front, in prose, in code or as a picture - except a
        // generated one, which writes its own wording and its own numbers
        if (type != GEN && prompt.isEmpty() && given == null && image == null) return null
        return when (type) {
            CODE ->
                if (back == null) {
                    null
                } else {
                    CodeTask(
                        prompt = prompt,
                        solution = back,
                        given = given,
                        image = image,
                        alternatives = alternatives,
                        topic = topic,
                        tags = tags,
                    )
                }

            // a card with nothing on the back would be turned over onto an empty screen
            SKETCH ->
                SketchTask(
                    prompt = prompt,
                    given = given,
                    image = image,
                    answerImage = answerImage,
                    answer = answer,
                    topic = topic,
                    tags = tags,
                ).takeIf { it.hasAnswer }

            CHOICE ->
                if (options.size < 2 || correct !in options.indices) {
                    null
                } else {
                    Question(
                        prompt = prompt,
                        answers = options,
                        correctIndex = correct,
                        given = given,
                        image = image,
                        topic = topic,
                        tags = tags,
                    )
                }

            GEN -> generated(fields, prompt.takeIf { it.isNotBlank() }, topic, tags)

            else -> null
        }
    }

    /**
     * A card that names an exercise rather than an instance of it.
     *
     * Everything is a field of its own instead of a small language of its own: `kind: bits` and
     * `op: ^` are duller to write than `bits xor 8`, but a line noise parser would be another
     * thing to get wrong at two in the morning, and this file is written by hand.
     */
    private fun generated(
        fields: Map<String, String>,
        title: String?,
        topic: String?,
        tags: List<String>,
    ): Task? {
        val kind =
            when (fields[KIND]?.lowercase()?.trim()) {
                "convert", "wandeln" -> GenKind.Convert
                "bits", "rechnen" -> GenKind.Bits
                "printf" -> GenKind.Printf
                else -> return null
            }
        val written = fields[OP]?.trim()
        val op = BitOp.entries.firstOrNull { it.symbol == written || it.name.equals(written, ignoreCase = true) }
        val format = fields[FORMAT]?.trim() ?: "%d"
        // a card that would throw when its answer is worked out is no card at all: it is
        // skipped and counted here, where the count is shown, rather than in the study screen
        if (kind != GenKind.Convert && op == null) return null
        if (kind == GenKind.Printf && !GeneratedTask.formatIsSound(format)) return null
        return GeneratedTask(
            kind = kind,
            from = fields[FROM]?.trim()?.toIntOrNull() ?: 2,
            to = fields[TO]?.trim()?.toIntOrNull() ?: 16,
            bits = fields[BITS]?.trim()?.toIntOrNull() ?: 8,
            op = op ?: BitOp.And,
            format = format,
            title = title,
            topic = topic,
            tags = tags,
        )
    }

    /** Reads a fenced block starting at [from], and says where it ended */
    private fun readBlock(
        lines: List<String>,
        from: Int,
    ): Pair<String, Int> {
        var index = from
        while (index < lines.size && lines[index].isBlank()) index++
        if (index >= lines.size || !lines[index].trimStart().startsWith(FENCE)) return "" to from
        index++
        val body = mutableListOf<String>()
        while (index < lines.size && !lines[index].trimStart().startsWith(FENCE)) {
            body += lines[index]
            index++
        }
        // step over the closing fence, if the file bothered to write one
        if (index < lines.size) index++
        return body.joinToString("\n").trim('\n') to index
    }

    private val FIELD = Regex("""^\s*([A-Za-zÄÖÜäöü]+)\s*:(.*)$""")
    private val OPTION = Regex("""^\s*[-*]\s+(.+)$""")

    private const val FENCE = "```"
    private const val SEPARATOR = "---"
    private const val TYPE = "type"
    private const val FRONT = "front"

    /** The code on the front. Written as a block under `front:`, or spelled out as its own field. */
    private const val GIVEN = "given"
    private const val BACK = "back"
    private const val ALT = "alt"
    private const val TAGS = "tags"
    private const val TOPIC = "topic"
    private const val CODE = "code"
    private const val CHOICE = "choice"

    /** Answered on paper and marked by the reader; the back is a picture or a paragraph */
    private const val SKETCH = "sketch"
    private const val IMAGE = "image"
    private const val ANSWER = "answer"
    private const val ANSWERIMAGE = "answerimage"

    private const val GEN = "gen"
    private const val KIND = "kind"
    private const val FROM = "from"
    private const val TO = "to"
    private const val BITS = "bits"
    private const val OP = "op"
    private const val FORMAT = "format"
}

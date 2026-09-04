package net.bison.importer

import net.bison.model.CardKind
import net.bison.model.Question
import net.bison.model.StudyCard
import net.bison.model.Task

/**
 * Reads the written card set: one Markdown file, one card per block.
 *
 * The format is the file's own, documented in its head, and this reads exactly that:
 *
 * ```
 * ## Subsection: MATLAB / Syntax
 *
 * ---
 * type: syntax
 * front: Lege einen Spaltenvektor an.
 * logik: Semikolon trennt Zeilen.
 * back: d = [3;6;2];
 * alt: d = [3; 6; 2];
 * tags: matlab, syntax
 * ```
 *
 * Three details decide most of the code here.
 *
 * A value runs on until the next field starts, because the set needs it to: a trace card carries
 * four lines of MATLAB under `front:`, and a single choice card carries its three options on the
 * line below. Only the seven known field names begin a field - otherwise a line of C reading
 * `default:` would start one.
 *
 * The `---` that separates cards is also Markdown's horizontal rule, so it is only a separator
 * where it stands alone on its line, which is how the file writes it.
 *
 * Everything above the first `##` heading is the file explaining itself, and is skipped. The
 * `#` title is taken as the name of the set, because it is the only name there is.
 */
object MarkdownCards {
    data class Found(
        val cards: List<Task>,
        val skipped: Int,
        val name: String?,
    )

    fun parse(text: String): Found {
        val lines = text.replace("\r\n", "\n").split("\n")
        var name: String? = null
        var subsection: String? = null
        val cards = mutableListOf<Task>()
        var skipped = 0
        var block = mutableListOf<String>()

        fun finish() {
            if (block.none { it.isNotBlank() }) {
                block = mutableListOf()
                return
            }
            val card = readCard(block, subsection)
            if (card != null) cards += card else skipped++
            block = mutableListOf()
        }

        for (line in lines) {
            val trimmed = line.trim()
            when {
                name == null && trimmed.startsWith("# ") -> name = trimmed.removePrefix("# ").trim()

                trimmed.startsWith("## ") -> {
                    finish()
                    subsection =
                        trimmed
                            .removePrefix("## ")
                            .removePrefix("Subsection:")
                            .trim()
                }

                trimmed == SEPARATOR -> finish()

                // before the first heading the file is describing itself
                subsection != null -> block += line
            }
        }
        finish()
        return Found(cards, skipped, name)
    }

    private fun readCard(
        lines: List<String>,
        subsection: String?,
    ): Task? {
        val fields = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null

        for (line in lines) {
            val field = FIELD.find(line)
            val key = field?.groupValues?.get(1)?.lowercase()
            if (key != null && key in FIELDS) {
                current = StringBuilder(field.groupValues[2].trim())
                fields[key] = current
            } else if (current != null) {
                // A value runs on until the next field: the code on a trace card, the options
                // under a single choice question, the answer to a timed one.
                //
                // Blank lines belong to it. A C answer separates its include from its struct
                // with one, and swallowing that would reformat the very thing being learned.
                // Blank lines at the end fall away when the value is trimmed.
                current.append('\n').append(line.trimEnd())
            }
        }

        fun value(key: String) = fields[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val kind = CardKind.of(value(TYPE).orEmpty()) ?: return null
        val front = value(FRONT) ?: return null
        val back = value(BACK) ?: return null
        val tags =
            value(TAGS)
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        // A single choice card becomes an ordinary question: this app had a good one before the
        // set arrived, and the job of an importer is to speak the model's language rather than
        // to have the screens learn the file's.
        if (kind == CardKind.Sc) return question(front, back, value(LOGIK), fields, subsection, tags)

        return StudyCard(
            kind = kind,
            prompt = front,
            target = seconds(value(ZIEL)),
            back = back,
            logic = value(LOGIK),
            // the file separates them with " | ", and an answer may well contain a bare pipe
            // nowhere else, so this is safe to split on
            alternatives =
                value(ALT)
                    .orEmpty()
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            params = value(PARAMS),
            topic = subsection,
            tags = tags,
        )
    }

    /**
     * A single choice card, turned into the question the app already knows.
     *
     * The three options come either as fields `a`, `b`, `c` or - as the set writes them - on the
     * line under the question, which is then taken out of the question so it is not said twice.
     * The answer names its letter first (`b. Call by Reference; …`), so the letter picks the
     * right option and the rest of the line is the reasoning.
     *
     * The options keep the order they were written in. The screen shuffles them itself, every
     * time it shows them, which is why it shows no letters.
     */
    private fun question(
        front: String,
        back: String,
        logic: String?,
        fields: Map<String, StringBuilder>,
        subsection: String?,
        tags: List<String>,
    ): Question? {
        fun value(key: String) = fields[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val written = StudyCard.OPTIONS.map { value(it.toString()) }
        val inFront = if (written.any { it == null }) optionsIn(front) else null
        val options = written.filterNotNull().takeIf { it.size == StudyCard.OPTIONS.size } ?: inFront?.options
        if (options == null || options.size < 2) return null

        val letter = back.trimStart().firstOrNull()?.lowercaseChar() ?: return null
        val correct = StudyCard.OPTIONS.indexOf(letter)
        if (correct !in options.indices) return null

        return Question(
            // the option lines come out of the question, so nothing is said twice
            prompt =
                inFront
                    ?.let { found -> front.lines().filterNot { it in found.lines } }
                    ?.joinToString(NEWLINE)
                    ?.trim()
                    ?: front,
            answers = options,
            correctIndex = correct,
            logic = logic,
            // what is left of the answer once the letter and its full stop are off it
            reason = back.trimStart().drop(1).trimStart('.', ')', ' ').trim().takeIf { it.isNotEmpty() },
            topic = subsection,
            tags = tags,
        )
    }

    /** The three options and the lines they were found on, so those can be taken out of the question */
    private data class Options(
        val lines: List<String>,
        val options: List<String>,
    )

    /**
     * The options a single choice card writes under its question.
     *
     * Two layouts, and the set uses both. Twenty-one cards put all three on one line, the way an
     * exam prints them:
     *
     * ```
     * a) Call by Address  b) Call by Reference  c) Call by Value
     * ```
     *
     * and thirteen give each one a line of its own, because the options are sentences:
     *
     * ```
     * a) Zeigt p auf x, kann p überall stehen, wo x gebraucht wird
     * b) Zeigt p auf x, kann *p überall stehen, wo &x gebraucht wird
     * ```
     *
     * Reading only the first would have thrown away those thirteen cards without a word, which
     * is the one thing an importer must never do.
     */
    private fun optionsIn(front: String): Options? {
        val lines = front.lines()

        // one line each, which is what a long option gets
        val own = lines.filter { OPTION_START.containsMatchIn(it) }
        if (own.size >= 3) {
            return Options(
                lines = own,
                options = own.map { OPTION_START.replaceFirst(it, "").trim() },
            )
        }

        // or all three on one line
        val together = lines.firstOrNull { OPTION_LINE.findAll(it).count() >= 3 } ?: return null
        val found = OPTION_LINE.findAll(together).toList()
        return Options(
            lines = listOf(together),
            options =
                found.mapIndexed { at, match ->
                    val from = match.range.last + 1
                    val to = found.getOrNull(at + 1)?.range?.first ?: together.length
                    together.substring(from, to).trim()
                },
        )
    }

    /** `90` or `1:30`, because both get written and both mean ninety seconds */
    private fun seconds(written: String?): Int? {
        val text = written?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (':' !in text) return text.toIntOrNull()
        val parts = text.split(':')
        val minutes = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val rest = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        return minutes * 60 + rest
    }

    private val FIELD = Regex("""^([A-Za-z]+):(.*)$""")

    /** `a)` or `a.`, anywhere on a line: three of these on one line is the compact layout */
    private val OPTION_LINE = Regex("""\b([abc])\s*[).]\s""")

    /** The same marker at the start of a line, which is the one-option-per-line layout */
    private val OPTION_START = Regex("""^\s*[abc]\s*[).]\s""")

    private const val SEPARATOR = "---"
    private const val NEWLINE = "\n"
    private const val TYPE = "type"
    private const val FRONT = "front"
    private const val LOGIK = "logik"
    private const val BACK = "back"
    private const val ALT = "alt"
    private const val PARAMS = "params"
    private const val TAGS = "tags"
    private const val ZIEL = "ziel"

    /** Only these begin a field. Anything else that looks like one is part of a value. */
    private val FIELDS = setOf(TYPE, FRONT, LOGIK, BACK, ALT, PARAMS, TAGS, ZIEL, "a", "b", "c")
}

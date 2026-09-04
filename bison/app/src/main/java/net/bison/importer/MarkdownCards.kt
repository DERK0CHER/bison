package net.bison.importer

import net.bison.model.CardKind
import net.bison.model.StudyCard

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
        val cards: List<StudyCard>,
        val skipped: Int,
        val name: String?,
    )

    fun parse(text: String): Found {
        val lines = text.replace("\r\n", "\n").split("\n")
        var name: String? = null
        var subsection: String? = null
        val cards = mutableListOf<StudyCard>()
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
    ): StudyCard? {
        val fields = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null

        for (line in lines) {
            val field = FIELD.find(line)
            val key = field?.groupValues?.get(1)?.lowercase()
            if (key != null && key in FIELDS) {
                current = StringBuilder(field.groupValues[2].trim())
                fields[key] = current
            } else if (current != null && line.isNotBlank()) {
                // a value runs on until the next field: the code on a trace card, the options
                // under a single choice question
                current.append('\n').append(line.trimEnd())
            }
        }

        fun value(key: String) = fields[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val kind = CardKind.of(value(TYPE).orEmpty()) ?: return null
        val front = value(FRONT) ?: return null
        val back = value(BACK) ?: return null

        // The options of a single choice card, written either as three fields of their own or -
        // as the set does - on the line under the question. Both are read; when they come out of
        // the question they are taken out of it, so the card does not say everything twice.
        val written = listOf(value("a"), value("b"), value("c"))
        val options = if (written.all { it != null }) written.filterNotNull() else emptyList()
        val fromFront = if (options.isEmpty()) optionsIn(front) else null

        return StudyCard(
            kind = kind,
            prompt = fromFront?.let { front.replace(it.line, "").trim() } ?: front,
            options = options.ifEmpty { fromFront?.options.orEmpty() },
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
            tags =
                value(TAGS)
                    .orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
        )
    }

    /** The three options and the line they were found on, so it can be taken out of the question */
    private data class Options(
        val line: String,
        val options: List<String>,
    )

    /**
     * The options a single choice card writes under its question.
     *
     * `a) Call by Address  b) Call by Reference  c) Call by Value` on one line, which is how the
     * set writes them and how an exam prints them.
     */
    private fun optionsIn(front: String): Options? {
        val line = front.lineSequence().firstOrNull { OPTION_LINE.containsMatchIn(it) } ?: return null
        val found = OPTION_LINE.findAll(line).toList()
        if (found.size < 3) return null
        return Options(
            line = line,
            options =
                found.mapIndexed { at, match ->
                    val from = match.range.last + 1
                    val to = found.getOrNull(at + 1)?.range?.first ?: line.length
                    line.substring(from, to).trim()
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

    /** `a)` or `a.` or `a )`, which is every way anybody writes an option marker */
    private val OPTION_LINE = Regex("""\b([abc])\s*[).]\s""")

    private const val SEPARATOR = "---"
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

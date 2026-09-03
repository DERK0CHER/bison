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
        return StudyCard(
            kind = kind,
            prompt = front,
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

    private val FIELD = Regex("""^([A-Za-z]+):(.*)$""")

    private const val SEPARATOR = "---"
    private const val TYPE = "type"
    private const val FRONT = "front"
    private const val LOGIK = "logik"
    private const val BACK = "back"
    private const val ALT = "alt"
    private const val PARAMS = "params"
    private const val TAGS = "tags"

    /** Only these begin a field. Anything else that looks like one is part of a value. */
    private val FIELDS = setOf(TYPE, FRONT, LOGIK, BACK, ALT, PARAMS, TAGS)
}

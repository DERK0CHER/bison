package net.bison.importer

import net.bison.model.Task

/**
 * The one way cards get in, whichever format they were written in.
 *
 * Multiple choice questions come out of a chat with a language model as JSON. Code cards are
 * written at a desk in a text file, because code does not survive being escaped into JSON. Both
 * are pasted into the same place, so the format is worked out from the text rather than asked
 * for: anything with a `front:` field or a `---` separator is a card file, and everything else
 * goes to the parser that knows JSON and lettered prose.
 */
object CardImport {
    data class Result(
        val tasks: List<Task>,
        val skipped: Int,
        val format: Format,
    )

    enum class Format {
        /** Fields and fenced blocks, written by hand */
        CardFile,

        /** JSON, or prose with lettered options, written by a language model */
        Questions,
    }

    fun parse(text: String): Result {
        if (looksLikeCardFile(text)) {
            val found = CardFileParser.parse(text)
            // a file that yielded nothing at all was probably not one; let the other parser try
            if (found.tasks.isNotEmpty()) {
                return Result(found.tasks, found.skipped, Format.CardFile)
            }
        }
        val parsed = QuestionParser.parse(text)
        return Result(parsed.questions, parsed.skipped, Format.Questions)
    }

    private fun looksLikeCardFile(text: String): Boolean =
        text.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed == "---" || MARKERS.any { trimmed.startsWith(it, ignoreCase = true) }
        }

    private val MARKERS = listOf("front:", "type:", "back:")
}

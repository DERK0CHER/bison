package net.bison.domain

import net.bison.model.CodeTask

/** What happened to one line between what was typed and the model answer */
enum class LineChange {
    /** The same line, in the same place */
    Same,

    /** Typed, but not in the model answer */
    Extra,

    /** In the model answer, but not typed */
    Missing,
}

/**
 * One row of the comparison.
 *
 * A row carries whichever sides it has: [Same] has both, [Extra] only what was typed, [Missing]
 * only the model answer.
 */
data class DiffRow(
    val change: LineChange,
    val mine: String? = null,
    val theirs: String? = null,
) {
    /** The line to show, whichever side exists */
    val text: String get() = mine ?: theirs.orEmpty()
}

/**
 * Lines up what was typed against the model answer.
 *
 * A plain index-by-index comparison is useless for code: one forgotten line makes everything
 * after it look wrong. This finds the longest run of lines the two have in common and reports
 * only the real difference around it, so a missing brace costs one row rather than twenty.
 *
 * Leading and trailing whitespace does not count towards whether two lines are the same, because
 * indentation is not what is being tested and the editor's own auto-indent would otherwise show
 * up as an error. The line as typed is still what gets shown.
 */
object LineDiff {
    fun compare(
        mine: List<String>,
        theirs: List<String>,
    ): List<DiffRow> {
        val a = mine.map { it.trim() }
        val b = theirs.map { it.trim() }

        // lengths[i][j] is the longest common run of a.drop(i) against b.drop(j)
        val lengths = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lengths[i][j] =
                    if (a[i] == b[j]) {
                        lengths[i + 1][j + 1] + 1
                    } else {
                        maxOf(lengths[i + 1][j], lengths[i][j + 1])
                    }
            }
        }

        val rows = mutableListOf<DiffRow>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    rows += DiffRow(LineChange.Same, mine = mine[i], theirs = theirs[j])
                    i++
                    j++
                }
                // whichever side to skip leaves more in common afterwards
                lengths[i + 1][j] >= lengths[i][j + 1] -> {
                    rows += DiffRow(LineChange.Extra, mine = mine[i])
                    i++
                }
                else -> {
                    rows += DiffRow(LineChange.Missing, theirs = theirs[j])
                    j++
                }
            }
        }
        while (i < a.size) {
            rows += DiffRow(LineChange.Extra, mine = mine[i])
            i++
        }
        while (j < b.size) {
            rows += DiffRow(LineChange.Missing, theirs = theirs[j])
            j++
        }
        return rows
    }

    /**
     * The accepted answer that lines up best with what was typed.
     *
     * A card with two accepted spellings must not be marked against the first one listed: an
     * answer written the other way would come out as every line wrong. An exact match wins
     * outright, and otherwise the one that leaves the fewest lines in disagreement.
     *
     * @param accepted the model answers, as text; the returned one is already split into lines
     */
    fun bestMatch(
        mine: List<String>,
        accepted: List<String>,
    ): List<String> {
        val against = accepted.map { CodeTask.lines(it) }
        val exact = against.firstOrNull { same(mine, it) }
        if (exact != null) return exact
        return against.minByOrNull { theirs ->
            compare(mine, theirs).count { it.change != LineChange.Same }
        } ?: emptyList()
    }

    /** Whether two answers are the same code, ignoring indentation and blank lines */
    fun same(
        mine: List<String>,
        theirs: List<String>,
    ): Boolean {
        val a = mine.map { it.trim() }.filter { it.isNotEmpty() }
        val b = theirs.map { it.trim() }.filter { it.isNotEmpty() }
        return a == b
    }

    /**
     * Whether two one-line answers are the same, with the spacing normalised and nothing else.
     *
     * Whitespace beside anything that is not a letter or a digit is dropped, so `d = [3;6]` and
     * `d=[3;6]` are the same answer - nobody should lose a card over a space in front of an
     * equals sign. Between two word characters it is kept, because there it separates two
     * things: `int a` is not `inta`, and in MATLAB `[3 6]` is a row of two and `[36]` is not.
     *
     * Case is left alone. In C and in MATLAB it means something.
     */
    fun sameLine(
        mine: String,
        theirs: String,
    ): Boolean = squeeze(mine) == squeeze(theirs)

    /** The line with the whitespace that carries no meaning taken out */
    private fun squeeze(line: String): String {
        val text = line.trim()
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (!char.isWhitespace()) {
                out.append(char)
                index++
                continue
            }
            var next = index
            while (next < text.length && text[next].isWhitespace()) next++
            val before = out.lastOrNull()
            val after = text.getOrNull(next)
            if (before != null && after != null && before.isLetterOrDigit() && after.isLetterOrDigit()) {
                out.append(' ')
            }
            index = next
        }
        return out.toString()
    }
}

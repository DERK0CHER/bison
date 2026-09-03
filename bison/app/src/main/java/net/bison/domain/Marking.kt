package net.bison.domain

/** What the learner says about one line of their own answer */
enum class LineMark(
    val deduction: Double,
    val label: String,
) {
    /** Nothing wrong with it */
    Right(0.0, "richtig"),

    /** A brace, a semicolon, a name - wrong as written but the thought was there */
    Syntax(0.25, "Syntax"),

    /** It does the wrong thing */
    Semantic(0.5, "Semantik"),
}

/**
 * The marks for one attempt, and what they come to.
 *
 * Marked the way the exam marks it: every line of the model answer is worth a point, a slip of
 * syntax costs a quarter and a wrong idea costs a half. That is deliberately the learner's own
 * judgement rather than the app's - no comparison of text can tell a renamed variable from a
 * wrong one, and pretending otherwise would either wave real mistakes through or fail correct
 * code for a space.
 *
 * @param marks one entry per row of the comparison, in the same order
 */
data class Marking(
    val marks: List<LineMark>,
    val maxPoints: Int,
) {
    val deducted: Double get() = marks.sumOf { it.deduction }

    /** What the attempt scored, never below zero */
    val points: Double get() = (maxPoints - deducted).coerceAtLeast(0.0)

    /** Nothing deducted anywhere. Only this counts as a right answer for the box. */
    val clean: Boolean get() = marks.all { it == LineMark.Right }

    /** "8,25 / 10", in the notation a German exam uses */
    fun asScore(): String = "${format(points)} / $maxPoints"

    companion object {
        /** A number of points as a German paper writes it: no trailing zeros, a comma */
        fun format(value: Double): String {
            val rounded = Math.round(value * 100) / 100.0
            if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
            return rounded.toString().replace('.', ',')
        }

        /**
         * The marks a fresh comparison starts from.
         *
         * Rows that already match are set to right, so a clean answer takes no tapping at all;
         * anything else starts at a semantic error, because the honest default for a line that
         * does not match the model answer is that it is wrong, and talking yourself up to a
         * quarter point should be the deliberate act.
         */
        fun from(rows: List<DiffRow>): Marking =
            Marking(
                marks = rows.map { if (it.change == LineChange.Same) LineMark.Right else LineMark.Semantic },
                maxPoints = rows.count { it.change != LineChange.Extra },
            )
    }
}

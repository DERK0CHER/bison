package net.bison.domain

/**
 * Whether a typed answer counts.
 *
 * The rule is the set's own, and it is deliberately narrow: runs of whitespace - spaces, tabs,
 * line breaks - all count as one space, and everything else has to match exactly, capitals
 * included. In MATLAB and C the capitals are the answer: `zeros` is a function and `Zeros` is an
 * undefined name, so an app that shrugged at the difference would be teaching the wrong thing.
 *
 * What that leaves forgiven is only the layout: `d = [3;6;2];` typed as `d=[3;6;2];` is **not**
 * the same answer here, but one typed across two lines, or with the spacing of the original,
 * is. Where both spellings are meant to count, the file says so with `alt`.
 */
object Typed {
    /** Whether what was typed is one of the answers the card accepts */
    fun matches(
        typed: String,
        accepted: List<String>,
    ): Boolean = accepted.any { same(typed, it) }

    /** Whether two answers are the same, once the layout is taken out of the comparison */
    fun same(
        mine: String,
        theirs: String,
    ): Boolean = squeeze(mine) == squeeze(theirs)

    /** The answer with every run of whitespace reduced to a single space, and the ends trimmed */
    fun squeeze(text: String): String = text.trim().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("""\s+""")
}

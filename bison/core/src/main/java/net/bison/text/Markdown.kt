package net.bison.text

/**
 * The little Markdown a card actually uses, read once for both interfaces.
 *
 * The set is written by hand and leans on it constantly: over a hundred lines put something in
 * backticks, because half of what these cards are about is a piece of syntax standing inside a
 * German sentence. Printed as plain text, `zeros(8,1)` arrives with its backticks showing and in
 * the same face as the prose around it, which is the one thing it must not be.
 *
 * Three things are read, and deliberately no more: fenced blocks, inline backticks, and bold. A
 * card is not a document, and every further piece of syntax would be one more thing that can go
 * wrong silently in a card set written at two in the morning.
 *
 * What comes out is plain data rather than styled text, because the phone and the desktop draw
 * with different Compose artifacts and can share the reading but not the drawing. Reading it
 * twice would eventually mean a card that sets one way in the lecture hall and another at a
 * desk.
 */
object Markdown {
    /** A run of a card's text, either prose or a block of code */
    sealed interface Piece {
        data class Prose(
            val text: String,
        ) : Piece

        data class Code(
            val text: String,
        ) : Piece
    }

    /** A stretch of prose set one way: plain, monospaced, or bold */
    data class Span(
        val text: String,
        val code: Boolean = false,
        val bold: Boolean = false,
    )

    /**
     * Splits the fenced blocks out of the prose.
     *
     * A fence is three backticks on a line of their own, with an optional language after the
     * opening one. An unclosed fence takes the rest of the text with it, which is what a reader
     * would expect from a card that forgot to close one.
     */
    fun pieces(text: String): List<Piece> {
        if (FENCE !in text) return listOf(Piece.Prose(text))

        val pieces = mutableListOf<Piece>()
        val buffer = StringBuilder()
        var inCode = false

        fun flush() {
            val body = buffer.toString().trim('\n')
            if (body.isNotBlank()) pieces += if (inCode) Piece.Code(body) else Piece.Prose(body)
            buffer.clear()
        }

        for (line in text.lines()) {
            if (line.trimStart().startsWith(FENCE)) {
                flush()
                inCode = !inCode
                continue
            }
            if (buffer.isNotEmpty()) buffer.append('\n')
            buffer.append(line)
        }
        flush()
        return pieces.ifEmpty { listOf(Piece.Prose(text)) }
    }

    /**
     * One run of prose, cut into the stretches that are set differently.
     *
     * An unclosed backtick or a lone pair of asterisks is left standing as itself rather than
     * swallowing the rest of the line. A card set is written by hand, and the character that was
     * meant literally is more common than the one that was meant as markup and got typed wrong.
     */
    fun spans(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var at = 0

        fun plain(upTo: Int) {
            if (upTo > at) spans += Span(text.substring(at, upTo))
        }

        while (at < text.length) {
            val code = text.indexOf('`', at)
            val bold = text.indexOf(BOLD, at)
            val next = listOf(code, bold).filter { it >= 0 }.minOrNull()
            if (next == null) {
                plain(text.length)
                break
            }
            if (next == code) {
                val end = text.indexOf('`', next + 1)
                if (end < 0) {
                    plain(text.length)
                    break
                }
                plain(next)
                spans += Span(text.substring(next + 1, end), code = true)
                at = end + 1
            } else {
                val end = text.indexOf(BOLD, next + BOLD.length)
                if (end < 0) {
                    plain(text.length)
                    break
                }
                plain(next)
                spans += Span(text.substring(next + BOLD.length, end), bold = true)
                at = end + BOLD.length
            }
        }
        return spans.filter { it.text.isNotEmpty() }
    }

    private const val FENCE = "```"

    private const val BOLD = "**"
}

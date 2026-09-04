package net.bison.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.bison.ui.theme.BisonColors

/**
 * A card's text, with the little Markdown a card actually uses.
 *
 * The set is written by hand in Markdown and leans on it constantly: a hundred and fifteen lines
 * put something in backticks, because half of what these cards are about is a piece of syntax
 * standing inside a German sentence. Printed as plain text, `zeros(8,1)` arrives with its
 * backticks showing and in the same face as the prose around it, which is the one thing it must
 * not be - the whole point of the sentence is that this part is code.
 *
 * Three things are read, and deliberately no more: fenced blocks, inline backticks, and bold.
 * A card is not a document, and every further piece of syntax would be one more thing that can
 * go wrong silently in a card set written at two in the morning.
 */
@Composable
fun CardText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val parts = remember(text) { split(text) }

    Column(modifier = modifier) {
        for ((at, part) in parts.withIndex()) {
            if (at > 0) Spacer(Modifier.height(10.dp))
            when (part) {
                is Part.Code -> GivenCode(code = part.text)
                is Part.Prose ->
                    Text(
                        text = inline(part.text, color),
                        style = style,
                        color = color,
                    )
            }
        }
    }
}

/** What a card's text is made of */
private sealed interface Part {
    data class Prose(
        val text: String,
    ) : Part

    data class Code(
        val text: String,
    ) : Part
}

/**
 * Splits the fenced blocks out of the prose.
 *
 * A fence is three backticks on a line of their own, with an optional language after the opening
 * one. An unclosed fence takes the rest of the text with it, which is what a reader would expect
 * from a card that forgot to close it.
 */
private fun split(text: String): List<Part> {
    if (FENCE !in text) return listOf(Part.Prose(text))

    val parts = mutableListOf<Part>()
    val buffer = StringBuilder()
    var inCode = false

    fun flush() {
        val body = buffer.toString().trim('\n')
        if (body.isNotBlank()) parts += if (inCode) Part.Code(body) else Part.Prose(body)
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
    return parts.ifEmpty { listOf(Part.Prose(text)) }
}

/**
 * Backticks and asterisks, turned into how they are meant to read.
 *
 * The monospaced run gets a background as well as a face: on a black screen a change of typeface
 * alone is easy to miss at the size a card's prose is set in, and `;` has to be unmissable when
 * the sentence is about the semicolon.
 */
internal fun inline(
    text: String,
    color: Color,
): AnnotatedString =
    buildAnnotatedString {
        var at = 0
        while (at < text.length) {
            val code = text.indexOf('`', at)
            val bold = text.indexOf(BOLD, at)

            val next = listOf(code, bold).filter { it >= 0 }.minOrNull()
            if (next == null) {
                append(text.substring(at))
                return@buildAnnotatedString
            }
            append(text.substring(at, next))

            if (next == code) {
                val end = text.indexOf('`', next + 1)
                if (end < 0) {
                    append(text.substring(next))
                    return@buildAnnotatedString
                }
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = BisonColors.SurfaceRaised,
                        color = color,
                    ),
                ) {
                    append(text.substring(next + 1, end))
                }
                at = end + 1
            } else {
                val end = text.indexOf(BOLD, next + BOLD.length)
                if (end < 0) {
                    append(text.substring(next))
                    return@buildAnnotatedString
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(next + BOLD.length, end))
                }
                at = end + BOLD.length
            }
        }
    }

private const val FENCE = "```"

private const val BOLD = "**"

/** The style a card's prose is set in, so every place that shows one agrees */
@Composable
fun cardProse(): TextStyle = MaterialTheme.typography.bodyLarge

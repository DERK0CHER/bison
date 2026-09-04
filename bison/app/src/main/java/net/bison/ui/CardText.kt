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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.bison.text.Markdown
import net.bison.ui.theme.BisonColors

/**
 * A card's text, with the little Markdown a card actually uses.
 *
 * The reading itself is in the shared core, so the desktop build sets a card the same way this
 * one does; what is left here is only how a piece of code is drawn on a phone.
 */
@Composable
fun CardText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(text) { Markdown.pieces(text) }

    Column(modifier = modifier) {
        for ((at, piece) in pieces.withIndex()) {
            if (at > 0) Spacer(Modifier.height(10.dp))
            when (piece) {
                is Markdown.Piece.Code -> GivenCode(code = piece.text)
                is Markdown.Piece.Prose ->
                    Text(
                        text = inline(piece.text, color),
                        style = style,
                        color = color,
                    )
            }
        }
    }
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
        for (span in Markdown.spans(text)) {
            when {
                span.code ->
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = BisonColors.SurfaceRaised,
                            color = color,
                        ),
                    ) {
                        append(span.text)
                    }

                span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                else -> append(span.text)
            }
        }
    }

/** The style a card's prose is set in, so every place that shows one agrees */
@Composable
fun cardProse(): TextStyle = MaterialTheme.typography.bodyLarge

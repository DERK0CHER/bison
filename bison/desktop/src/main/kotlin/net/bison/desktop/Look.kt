package net.bison.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import net.bison.look.Palette
import net.bison.text.Markdown

/**
 * The same palette as the phone, from the same numbers.
 *
 * Nothing here decides anything; it only draws. Every question about what a colour or a piece of
 * markup means was already answered in the core, which is why this file can be as thin as it is.
 */
object Ink {
    val Background = Color(Palette.BACKGROUND)
    val Surface = Color(Palette.SURFACE)
    val Raised = Color(Palette.SURFACE_RAISED)
    val Border = Color(Palette.BORDER)
    val Primary = Color(Palette.TEXT_PRIMARY)
    val Secondary = Color(Palette.TEXT_SECONDARY)
    val Muted = Color(Palette.TEXT_MUTED)
    val Almost = Color(Palette.ALMOST)
    val Correct = Color(Palette.CORRECT)
    val Wrong = Color(Palette.WRONG)
    val Learned = Color(Palette.LEARNED_GREEN)

    /** How well something is known, red through amber to light green */
    fun forProgress(fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return if (f < 0.5f) lerp(Wrong, Almost, f * 2f) else lerp(Almost, Learned, (f - 0.5f) * 2f)
    }

    private fun lerp(
        from: Color,
        to: Color,
        at: Float,
    ) = Color(
        red = from.red + (to.red - from.red) * at,
        green = from.green + (to.green - from.green) * at,
        blue = from.blue + (to.blue - from.blue) * at,
    )
}

/**
 * Set smaller than on the phone, on purpose.
 *
 * A phone shows one question at arm's length and can afford thirty points for it. A window shows
 * the question, what was typed, the model answer and the marking at once, and the reason to be
 * at a desk in the first place is that all four fit.
 */
private val DesktopType =
    Typography(
        displaySmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )

@Composable
fun BisonDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Ink.Primary,
                onPrimary = Ink.Background,
                background = Ink.Background,
                onBackground = Ink.Primary,
                surface = Ink.Surface,
                onSurface = Ink.Primary,
                surfaceVariant = Ink.Raised,
                onSurfaceVariant = Ink.Secondary,
                outline = Ink.Border,
                error = Ink.Wrong,
            ),
        typography = DesktopType,
        content = content,
    )
}

/** A small grey line naming what is under it */
@Composable
fun Caption(
    text: String,
    color: Color = Ink.Muted,
) {
    Text(text = text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

/** A card's text, set the way the phone sets it, from the same reading */
@Composable
fun CardText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Ink.Primary,
) {
    for ((at, piece) in Markdown.pieces(text).withIndex()) {
        when (piece) {
            is Markdown.Piece.Code -> Code(piece.text, modifier.padding(top = if (at > 0) 10.dp else 0.dp))
            is Markdown.Piece.Prose ->
                Text(
                    text = inline(piece.text, color),
                    style = style,
                    color = color,
                    modifier = modifier.padding(top = if (at > 0) 10.dp else 0.dp),
                )
        }
    }
}

/** A block of code, on its own panel so the indentation is obviously part of it */
@Composable
fun Code(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.Primary,
) {
    Box(
        modifier
            .background(Ink.Raised, RoundedCornerShape(12.dp))
            .border(1.dp, Ink.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 23.sp),
        )
    }
}

/** Backticks and asterisks, turned into how they are meant to read */
private fun inline(
    text: String,
    color: Color,
): AnnotatedString =
    buildAnnotatedString {
        for (span in Markdown.spans(text)) {
            when {
                span.code ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Ink.Raised, color = color)) {
                        append(span.text)
                    }

                span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                else -> append(span.text)
            }
        }
    }

/** A panel: everything on these screens sits on one */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(Ink.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.Border, RoundedCornerShape(18.dp))
            .padding(padding),
    ) { content() }
}

/**
 * A button, with the key that also presses it written on it.
 *
 * The whole point of studying at a desk is that the hands stay on the keyboard; a button that
 * can only be clicked would send them back to the mouse between every card. So every button
 * that matters has a key, and says so.
 */
@Composable
fun KeyButton(
    text: String,
    key: String? = null,
    tint: Color = Ink.Primary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = tint,
                contentColor = Ink.Background,
                disabledContainerColor = Ink.Raised,
                disabledContentColor = Ink.Muted,
            ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        if (key != null) {
            Text(
                text = "  $key",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.Background.copy(alpha = 0.55f),
            )
        }
    }
}

/** A quieter button, for what is available but not what is being asked for */
@Composable
fun PlainButton(
    text: String,
    key: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Ink.Raised,
                contentColor = Ink.Primary,
                disabledContainerColor = Ink.Surface,
                disabledContentColor = Ink.Muted,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        if (key != null) {
            Text(text = "  $key", style = MaterialTheme.typography.labelSmall, color = Ink.Muted)
        }
    }
}

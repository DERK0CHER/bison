package net.bison.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bison.look.Palette

/**
 * The palette: black, white, and three greys.
 *
 * The numbers themselves live in the shared core, because the desktop build draws the same app
 * with a different Compose artifact and the two blacks have to be the same black.
 */
object BisonColors {
    val Background = Color(Palette.BACKGROUND)
    val Surface = Color(Palette.SURFACE)
    val SurfaceRaised = Color(Palette.SURFACE_RAISED)
    val Border = Color(Palette.BORDER)

    val TextPrimary = Color(Palette.TEXT_PRIMARY)

    val TextSecondary = Color(Palette.TEXT_SECONDARY)
    val TextMuted = Color(Palette.TEXT_MUTED)

    val Almost = Color(Palette.ALMOST)

    val Correct = Color(Palette.CORRECT)
    val CorrectSurface = Color(Palette.CORRECT_SURFACE)
    val Wrong = Color(Palette.WRONG)
    val WrongSurface = Color(Palette.WRONG_SURFACE)

    /**
     * How well something is known, as one colour on a run from red through amber to light green.
     *
     * A question passes through eight boxes, so three buckets would throw most of that away:
     * the shade itself is the reading, and every right answer visibly moves it along.
     *
     * @param fraction 0f for not known at all, 1f for finished
     */
    fun progressColor(fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return if (f < 0.5f) {
            lerp(Wrong, Almost, f * 2f)
        } else {
            lerp(Almost, LearnedGreen, (f - 0.5f) * 2f)
        }
    }

    /** The light green the run ends on */
    val LearnedGreen = Color(Palette.LEARNED_GREEN)
}

/** Radii and spacing, in one place so every surface agrees */
object BisonShape {
    /** Panels and answer boxes: generous, as on the reference screens */
    val Radius = 28.dp

    /** Buttons are fully round, as on the reference screens */
    val Pill = 999.dp
    val Gutter = 24.dp
    val Gap = 12.dp
}

/**
 * The two speeds the interface moves at.
 *
 * [Quick] is for feedback under a finger, [Settle] for content taking its place. Everything
 * animates at one of these, so the app has a gait instead of a bag of durations.
 */
object BisonMotion {
    val Quick = 140
    val Settle = 320
}

private val BisonColorScheme =
    darkColorScheme(
        primary = BisonColors.TextPrimary,
        onPrimary = BisonColors.Background,
        background = BisonColors.Background,
        onBackground = BisonColors.TextPrimary,
        surface = BisonColors.Surface,
        onSurface = BisonColors.TextPrimary,
        surfaceVariant = BisonColors.SurfaceRaised,
        onSurfaceVariant = BisonColors.TextSecondary,
        outline = BisonColors.Border,
        error = BisonColors.Wrong,
    )

/**
 * Two sizes carry the screen: a large tight headline for the question, and a relaxed grey
 * body for everything being read rather than answered.
 */
private val BisonTypography =
    Typography(
        // Screen titles are set very large and very tight, the way the reference screens do it
        displayLarge = TextStyle(fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp),
        displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
        // A question is long prose, so it sits a step below a title
        displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        // Captions get a little tracking, so small grey lines read as labels rather than prose
        labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )

/** The app is black whatever the system theme says: a light variant would be a different design. */
@Composable
fun BisonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BisonColorScheme,
        typography = BisonTypography,
        content = content,
    )
}

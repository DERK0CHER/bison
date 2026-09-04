package net.bison.look

/**
 * The palette, as plain numbers.
 *
 * The phone and the desktop draw with two different Compose artifacts, so they cannot share a
 * `Color`. They can share what a colour actually is. Keeping the numbers here rather than in
 * either interface means the two builds cannot drift apart into two slightly different blacks,
 * which is exactly the sort of difference nobody notices until the two are side by side.
 *
 * Black, white and three greys. Colour is spent only where it carries meaning - green for right,
 * red for wrong - and everything else is a shade, which is what lets a screen of answer boxes
 * stay quiet.
 */
object Palette {
    const val BACKGROUND = 0xFF000000L
    const val SURFACE = 0xFF101010L
    const val SURFACE_RAISED = 0xFF1A1A1AL
    const val BORDER = 0xFF232323L

    const val TEXT_PRIMARY = 0xFFFFFFFFL

    /** Body copy: light enough to read at length against black */
    const val TEXT_SECONDARY = 0xFFB4B4B8L
    const val TEXT_MUTED = 0xFF7A7A7EL

    /** The middle of the run from not-known to known */
    const val ALMOST = 0xFFFDB022L

    const val CORRECT = 0xFF32D583L
    const val CORRECT_SURFACE = 0xFF0E1F16L
    const val WRONG = 0xFFF97066L
    const val WRONG_SURFACE = 0xFF1F1211L

    /** The light green the run ends on */
    const val LEARNED_GREEN = 0xFF7BE495L
}

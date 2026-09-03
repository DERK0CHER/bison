package net.bison.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import net.bison.data.ImageStore
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * Where a card's pictures are read from.
 *
 * Handed down rather than passed along: a picture can turn up on any kind of card, so the store
 * would otherwise have to be threaded through the study screen, every round and the shared front
 * - five signatures that have nothing to do with pictures. The rest of the app is explicit about
 * its dependencies; this one is a service the whole tree may ask for, which is what a local is
 * for. Nothing is provided by default, so a screen rendered in a test draws the missing-picture
 * panel rather than reaching for a file.
 */
val LocalImages = staticCompositionLocalOf<ImageStore?> { null }

/**
 * A picture on a card.
 *
 * Fitted rather than filled and capped in height: an activity chart is wider than it is tall and
 * a photographed page is the other way round, and cropping either of them would take away the
 * part being asked about. What does not fit is made smaller, never cut.
 *
 * A picture that is not in the store says so and names itself, because that is a card written
 * against a file that was never added - and a blank space would leave the reader wondering what
 * the question was rather than which file is missing.
 */
@Composable
fun CardPicture(
    name: String,
    modifier: Modifier = Modifier,
) {
    val images = LocalImages.current
    val bitmap = remember(images, name) { images?.load(name)?.asImageBitmap() }

    if (bitmap == null) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BisonShape.Radius))
                    .background(BisonColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Caption(text = "BILD FEHLT")
            Spacer(Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = BisonColors.TextSecondary,
            )
        }
        return
    }

    Image(
        bitmap = bitmap,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = MAX_HEIGHT)
                // The picture's own shape, so it is as wide as the card and as tall as that
                // makes it. Without this the box is only as tall as the picture's pixels say -
                // a 640 pixel diagram is 213 dp on a dense screen - and the picture is drawn
                // small in the middle of a full width row, which is what the first render did.
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                .clip(RoundedCornerShape(BisonShape.Radius)),
    )
}

/** As much of the screen as a picture may take before the rest of the card goes off the bottom */
private val MAX_HEIGHT = 420.dp

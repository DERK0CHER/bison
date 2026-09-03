package net.bison.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The pictures cards use, as files in one directory.
 *
 * A card holds only the name of its picture. The alternative - a path relative to the card file -
 * cannot work here: the file dialog hands back a `content://` URI, and there is no way to reach
 * the directory that URI came from, so a relative path would have nothing to be relative to. So
 * pictures are put in once and referred to by name, which is also what makes the same diagram
 * usable by several cards.
 *
 * Names are tidied on the way in and on the way out, so a card that says `Activity Chart.PNG`
 * finds the file that was added as `activity-chart.png`, and nothing can name its way out of
 * this directory.
 */
class ImageStore(
    private val dir: File,
) {
    constructor(context: Context) : this(File(context.filesDir, DIR))

    /**
     * Puts a picture in the store and says what it ended up being called.
     *
     * The same name twice is the same picture: it is overwritten rather than given a second
     * copy, so importing a set of diagrams again after fixing one of them does what it looks
     * like it does.
     */
    fun save(
        name: String,
        bytes: ByteArray,
    ): String? {
        val tidied = tidy(name)
        return try {
            dir.mkdirs()
            File(dir, tidied).writeBytes(bytes)
            tidied
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not store the picture $tidied", e)
            null
        }
    }

    /** The file one is in, or null when the card names a picture that was never added */
    fun file(name: String): File? = File(dir, tidy(name)).takeIf { it.isFile }

    /** How many are in the store, for saying so on the import screen */
    fun count(): Int = dir.listFiles()?.count { it.isFile } ?: 0

    /**
     * A picture, scaled down on the way in.
     *
     * A diagram photographed with a phone is four thousand pixels wide and sixty megabytes in
     * memory, for a card that is four hundred pixels across. Decoding the bounds first and
     * halving until it is close enough costs one extra read of the header and saves the rest.
     */
    fun load(
        name: String,
        maxWidth: Int = MAX_WIDTH,
    ): Bitmap? {
        val file = file(name) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var step = 1
            while (bounds.outWidth / step > maxWidth * 2) step *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = step })
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not read the picture $name", e)
            null
        }
    }

    companion object {
        const val DIR = "images"

        private const val TAG = "ImageStore"

        /** Wide enough for a diagram on a phone, small enough not to hold a photograph in memory */
        private const val MAX_WIDTH = 1200

        /**
         * The name a picture is filed under.
         *
         * Everything that is not a letter, a digit, a dot, a dash or an underscore becomes a
         * dash, which drops the directory separators along with the spaces - a card must not be
         * able to name a file outside this directory, and the name comes from a text file
         * written by hand.
         */
        fun tidy(name: String): String {
            val bare =
                name
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .trim()
                    .lowercase()
                    .map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '-' }
                    .joinToString("")
                    .take(64)
            // "." and ".." name directories rather than pictures
            if (bare.isEmpty() || bare.all { it == '.' }) return "bild"
            return bare
        }
    }
}

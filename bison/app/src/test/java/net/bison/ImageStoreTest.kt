package net.bison

import android.graphics.Bitmap
import net.bison.data.ImageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Tests the directory a card's pictures live in.
 *
 * Native graphics, because the point of the store is that a real PNG goes in and a real bitmap
 * comes out; the shadow implementation would decode anything at all and prove nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStoreTest {
    private val dir =
        File(System.getProperty("java.io.tmpdir"), "bison-images-${System.nanoTime()}")
            .also { it.mkdirs() }

    private val store = ImageStore(dir)

    private fun png(size: Int = 8): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @Test
    fun `a name is tidied to something that cannot leave the directory`() {
        assertEquals("activity-chart.png", ImageStore.tidy("Activity Chart.PNG"))
        assertEquals("chart.png", ImageStore.tidy("/home/ich/karten/chart.png"))
        assertEquals("chart.png", ImageStore.tidy("""C:\Bilder\chart.png"""))
        // a card file is written by hand, so it can also be written badly
        assertEquals("secrets.txt", ImageStore.tidy("../../secrets.txt"))
        assertEquals("bild", ImageStore.tidy(".."))
        assertEquals("bild", ImageStore.tidy(""))
    }

    @Test
    fun `a picture is filed under its tidied name and found by the untidy one`() {
        assertEquals("activity-chart.png", store.save("Activity Chart.PNG", png()))

        assertTrue(store.file("activity chart.png") != null)
        assertTrue(store.load("Activity Chart.PNG") != null)
        assertEquals(1, store.count())
    }

    @Test
    fun `the same name twice is the same picture rather than a second copy`() {
        store.save("chart.png", png(size = 8))
        store.save("chart.png", png(size = 16))

        assertEquals(1, store.count())
        assertEquals(16, store.load("chart.png")?.width)
    }

    @Test
    fun `a picture a card names but nobody added is simply not there`() {
        assertNull(store.file("fehlt.png"))
        assertNull(store.load("fehlt.png"))
    }

    @Test
    fun `something that is not a picture at all does not bring the card down`() {
        store.save("kaputt.png", "das ist kein PNG".toByteArray())

        assertTrue(store.file("kaputt.png") != null)
        assertNull(store.load("kaputt.png"))
    }
}

package net.bison.desktop

import net.bison.data.DeckStore
import java.io.File
import java.util.Properties
import kotlin.random.Random

/**
 * Where Bison keeps its things on a desktop.
 *
 * The same `decks.json` the phone writes, in whatever the platform calls an application's own
 * folder. Nothing is put beside the executable: the build is unzipped wherever it lands and may
 * well be replaced by the next one, and a term's work must not be sitting in the folder that
 * gets deleted.
 */
class Home(
    val dir: File = defaultDir(),
) {
    init {
        dir.mkdirs()
    }

    val store = DeckStore(File(dir, DeckStore.FILE_NAME))

    private val file = File(dir, "einstellungen.properties")

    private val settings =
        Properties().also { loaded ->
            runCatching { file.inputStream().use(loaded::load) }
        }

    /**
     * The number the phone has to say before this machine will exchange anything with it.
     *
     * Not a password: it is here because three people in the same room may well have this
     * running at once, and a phone that syncs into a classmate's copy would quietly mix two
     * people's revision together. Six digits are enough to make that an unlikely accident, and
     * nothing here is worth defending against somebody who is trying.
     */
    var code: String
        get() = settings.getProperty(CODE) ?: newCode().also { code = it }
        set(value) = write(CODE, value)

    /** Which port the phone connects to. Settable because a machine may already be using it. */
    var port: Int
        get() = settings.getProperty(PORT)?.toIntOrNull() ?: DEFAULT_PORT
        set(value) = write(PORT, value.toString())

    /** The card file that was last read in, so it can be read again after it was edited */
    var lastCardFile: String?
        get() = settings.getProperty(CARD_FILE)?.takeIf { it.isNotEmpty() }
        set(value) = write(CARD_FILE, value.orEmpty())

    private fun write(
        key: String,
        value: String,
    ) {
        settings.setProperty(key, value)
        runCatching { file.outputStream().use { settings.store(it, "Bison") } }
            .onFailure { System.err.println("Bison: could not write the settings: $it") }
    }

    private fun newCode(): String = (1..6).map { Random.nextInt(10) }.joinToString("")

    companion object {
        /** Where the phone is told to connect, unless this machine is already using it */
        const val DEFAULT_PORT = 8777

        private const val CODE = "code"
        private const val PORT = "port"
        private const val CARD_FILE = "kartendatei"

        private fun defaultDir(): File {
            val appData = System.getenv("APPDATA")
            if (!appData.isNullOrBlank()) return File(appData, "Bison")
            val support = File(System.getProperty("user.home"), "Library/Application Support")
            if (support.isDirectory) return File(support, "Bison")
            return File(System.getProperty("user.home"), ".bison")
        }
    }
}

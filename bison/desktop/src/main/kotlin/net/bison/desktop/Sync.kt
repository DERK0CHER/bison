package net.bison.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.bison.progress.CardProgress
import net.bison.progress.Progress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * The desk end of syncing, which is the end that sits still.
 *
 * The desktop listens and the phone connects, because the desktop is the machine with a screen
 * you can read an address off and a keyboard the phone's owner is already looking at. It is one
 * exchange in both directions: the phone posts everything it knows, this merges that with
 * everything here, and answers with the merged whole. Both ends then have the same thing, and
 * neither had to decide which of them was right.
 *
 * There is no account, no cloud and no pairing dance. Two machines on one network, a six digit
 * number so it is the right two machines, and a format in which merging cannot lose anything.
 */
class Sync(
    private val port: Int,
    private val code: String,
    /** Merges what arrived into what is here, saves it, and hands back the merged whole */
    private val exchange: (List<CardProgress>) -> List<CardProgress>,
) {
    private var server: HttpServer? = null

    /** What went over the wire last, so the screen can say something happened */
    @Volatile
    var lastContact: String? = null
        private set

    val isRunning: Boolean get() = server != null

    /**
     * Starts listening, or says why it could not.
     *
     * @return null when it started, or a sentence naming the problem
     */
    fun start(): String? {
        if (server != null) return null
        return try {
            val started = HttpServer.create(InetSocketAddress(port), 0)
            started.createContext("/", ::hello)
            started.createContext(PATH, ::sync)
            // a pool rather than the default null executor, which serves one request at a time
            // on the calling thread and would block the whole interface while it did
            started.executor = Executors.newFixedThreadPool(2)
            started.start()
            server = started
            null
        } catch (e: Exception) {
            "Port $port lässt sich nicht öffnen: ${e.message}"
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    /**
     * Something to look at when the phone cannot reach this.
     *
     * Typing the address into the phone's browser is the first thing anybody does when a sync
     * button says nothing, and a page that says which machine this is settles at once whether
     * the problem is the network or the app.
     */
    private fun hello(http: HttpExchange) {
        val body =
            """
            <!doctype html><meta charset="utf-8">
            <title>Bison</title>
            <body style="background:#000;color:#fff;font-family:system-ui;padding:2rem">
            <h1>Bison läuft</h1>
            <p>Diese Maschine ist erreichbar. In der App die Adresse dieser Seite eintragen
            und den sechsstelligen Code, der hier im Sync-Fenster steht.</p>
            """.trimIndent()
        respond(http, 200, body, "text/html; charset=utf-8")
    }

    private fun sync(http: HttpExchange) {
        if (http.requestHeaders.getFirst(CODE_HEADER) != code) {
            lastContact = "Ein Gerät hat sich mit dem falschen Code gemeldet"
            respond(http, 403, "{\"fehler\":\"code\"}")
            return
        }
        val body = http.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        val theirs = if (http.requestMethod == "POST") Progress.read(body) else emptyList()
        val merged = exchange(theirs)
        lastContact =
            "Zuletzt: ${theirs.sumOf { it.attempts.size }} Versuche empfangen, " +
                "${merged.sumOf { it.attempts.size }} zurückgeschickt"
        respond(http, 200, Progress.json(merged))
    }

    private fun respond(
        http: HttpExchange,
        status: Int,
        body: String,
        type: String = "application/json; charset=utf-8",
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        http.responseHeaders.add("Content-Type", type)
        http.sendResponseHeaders(status, bytes.size.toLong())
        http.responseBody.use { it.write(bytes) }
    }

    companion object {
        /** Where the phone posts. Named in the app as well, so the two have to agree. */
        const val PATH = "/sync"

        /** The header the six digit code travels in */
        const val CODE_HEADER = "X-Bison-Code"

        /**
         * The addresses this machine can be reached at from the same network.
         *
         * A laptop has several - a wired one, a wireless one, whatever a virtual machine left
         * behind - and there is no way to know from in here which of them the phone is on. So
         * all of them are shown and the reader picks; typing the wrong one costs a second, and
         * guessing on their behalf would cost the ten minutes spent wondering why it does not
         * work.
         */
        fun addresses(): List<String> =
            runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.asSequence() }
                    .filter { it.address.size == 4 && !it.isLoopbackAddress && it.isSiteLocalAddress }
                    .map { it.hostAddress }
                    .distinct()
                    .toList()
            }.getOrDefault(emptyList())
    }
}

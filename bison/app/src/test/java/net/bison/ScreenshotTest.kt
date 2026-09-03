package net.bison

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.bison.data.ImageStore
import net.bison.domain.ExamDraw
import net.bison.domain.ExamPlan
import net.bison.model.Card
import net.bison.model.CodeTask
import net.bison.model.Deck
import net.bison.model.GenKind
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.SketchTask
import net.bison.model.Subtopic
import net.bison.ui.DeckListScreen
import net.bison.ui.ExamResult
import net.bison.ui.ExamScreen
import net.bison.ui.ExamSetupScreen
import net.bison.ui.ImportScreen
import net.bison.ui.LocalImages
import net.bison.ui.StudyScreen
import net.bison.ui.SubtopicScreen
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape
import net.bison.ui.theme.BisonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Renders each screen to a PNG.
 *
 * There is no emulator available for this project, so this is how the interface actually gets
 * looked at: the screens are drawn by the real Compose code in a JVM test and CI publishes the
 * images. It catches what unit tests cannot - text that overflows, a control pushed off screen,
 * spacing that reads wrong on a phone-sized window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun card(
        prompt: String,
        answers: List<String>,
        correctIndex: Int,
        box: Int = 0,
    ) = Card(Question(prompt, answers, correctIndex), box = box)

    private val breakdown =
        card(
            prompt = "Wie verhältst du dich bei einer Panne auf der Autobahn?",
            answers =
                listOf(
                    "Warnblinkanlage einschalten und Warnweste anlegen",
                    "Auf der Fahrbahn stehen bleiben und winken",
                    "Das Fahrzeug verlassen und auf dem Standstreifen warten",
                    "Den Motor laufen lassen und sitzen bleiben",
                ),
            correctIndex = 0,
            box = 5,
        )

    private val centreLine =
        card(
            prompt = "Was bedeutet ein durchgezogener Mittelstreifen?",
            answers =
                listOf(
                    "Überholen ist erlaubt",
                    "Er darf nicht überfahren werden",
                    "Er markiert eine Baustelle",
                ),
            correctIndex = 1,
        )

    /** A topic in parts, each one at a different stage, which is what the bars are for */
    private fun sampleDeck() =
        Deck(
            id = "sample",
            name = "Theorieprüfung Klasse B",
            subtopics =
                listOf(
                    Subtopic("sample-0", "Verkehrszeichen", listOf(centreLine.copy(box = Card.LEARNED_BOX))),
                    Subtopic("sample-1", "Verhalten im Verkehr", listOf(breakdown, centreLine)),
                    Subtopic("sample-2", "Erste Hilfe", listOf(centreLine.copy(box = 2), breakdown.copy(box = 0))),
                ),
        )

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("$OUTPUT_DIR/$name.png")
    }

    @Test
    fun deckListEmpty() {
        composeRule.setContent {
            BisonTheme { DeckListScreen(decks = emptyList(), soundOn = true, onSoundChange = {}, onOpen = {}, onImport = {}) }
        }
        capture("01-decks-empty")
    }

    @Test
    fun deckList() {
        composeRule.setContent {
            BisonTheme {
                DeckListScreen(
                    decks = listOf(sampleDeck()),
                    soundOn = true,
                    onSoundChange = {},
                    onOpen = {},
                    onImport = {},
                )
            }
        }
        capture("02-decks")
    }

    @Test
    fun importScreen() {
        composeRule.setContent {
            BisonTheme { ImportScreen(onCancel = {}, onImport = { _, _ -> }) }
        }
        capture("03-import")
    }

    @Test
    fun studyQuestion() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(key = "s", cards = sampleDeck().cards, soundOn = false, onFinished = {}, onLeave = {})
            }
        }
        capture("04-study-question")
    }

    @Test
    fun studyAnsweredWrong() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(key = "s", cards = listOf(breakdown), soundOn = false, onFinished = {}, onLeave = {})
            }
        }
        // the order is shuffled on every presentation, so pick by the answer's own text
        composeRule.onNodeWithText("Auf der Fahrbahn stehen bleiben und winken").performClick()
        capture("05-study-wrong")
    }

    @Test
    fun studyFinished() {
        // one question, one box short of learned: a single right answer finishes the set
        val nearly = listOf(centreLine.copy(box = Card.LEARNED_BOX - 1))
        composeRule.setContent {
            BisonTheme {
                StudyScreen(key = "s", cards = nearly, soundOn = false, onFinished = {}, onLeave = {})
            }
        }
        composeRule.onNodeWithText("Er darf nicht überfahren werden").performClick()
        // tapping anywhere moves on, and the question is the one target outside every answer
        composeRule.onNodeWithText("Was bedeutet ein durchgezogener Mittelstreifen?").performClick()
        capture("06-study-finished")
    }

    /**
     * The case that broke: more question and answer than fits.
     *
     * Only the question used to scroll, so a set of long answers ran off the bottom with no way
     * to reach the last one. This renders that state, which is the only way to see it here.
     */
    @Test
    fun studyLongQuestion() {
        val long =
            listOf(
                card(
                    prompt =
                        "Du näherst dich bei Nacht einer unbeschrankten Bahnübergangstelle und " +
                            "siehst das Andreaskreuz. Wie verhältst du dich?",
                    answers =
                        listOf(
                            "Mit mäßiger Geschwindigkeit heranfahren, auf Signale achten und " +
                                "notfalls vor dem Andreaskreuz anhalten",
                            "Zügig über den Übergang fahren, damit du ihn schnell wieder " +
                                "verlässt und niemanden aufhältst",
                            "Anhalten, aussteigen und in beide Richtungen die Strecke absuchen, " +
                                "bevor du weiterfährst",
                            "Hupen und die Lichthupe betätigen, um auf dich aufmerksam zu " +
                                "machen, dann weiterfahren",
                        ),
                    correctIndex = 0,
                ),
            )
        composeRule.setContent {
            BisonTheme {
                StudyScreen(key = "s", cards = long, soundOn = false, onFinished = {}, onLeave = {})
            }
        }
        capture("07-study-long")
    }

    /** The parts of one topic, each with its own bar */
    @Test
    fun subtopics() {
        composeRule.setContent {
            BisonTheme {
                SubtopicScreen(deck = sampleDeck(), onOpen = {}, onStudyAll = {}, onBack = {})
            }
        }
        capture("08-subtopics")
    }

    private val nodeDelete =
        CodeTask(
            prompt = "Vervollständige node_delete",
            given = "void node_delete(node_t *n) {\n${CodeTask.GAP}\n}",
            solution = "    free(n->data);\n    free(n);\n    n = NULL;",
            topic = "Verkettete Listen",
            tags = listOf("WS24"),
        )

    /** Sorting the model answer's lines, which is where a code card starts */
    @Test
    fun sortCode() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "sort",
                    cards = listOf(Card(nodeDelete)),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                )
            }
        }
        capture("09-sort-code")
    }

    /** The editor, once the card has been sorted cleanly twice */
    @Test
    fun writeCode() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "write",
                    cards = listOf(Card(nodeDelete, sorted = Card.SORTS_TO_WRITE)),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                )
            }
        }
        capture("10-write-code")
    }

    private val columnVector =
        CodeTask(
            prompt = "Lege einen Spaltenvektor mit den Werten 3, 6, 2, 5, 9 an",
            solution = "d = [3;6;2;5;9]",
            alternatives = listOf("d = [3 6 2 5 9]'"),
            topic = "MATLAB",
            tags = listOf("WS24"),
        )

    /** A model answer of one line: one field, no marking, the app decides */
    @Test
    fun typeOneLiner() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "type",
                    cards = listOf(Card(columnVector)),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                )
            }
        }
        capture("11-type-line")
    }

    /** The verdict, with the model answer shown because the empty answer was wrong */
    @Test
    fun typeOneLinerWrong() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "type",
                    cards = listOf(Card(columnVector)),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                )
            }
        }
        composeRule.onNodeWithText("Abgeben").performClick()
        capture("12-type-wrong")
    }

    /** A program and three outputs, which is how the exam asks what code does */
    @Test
    fun traceQuestion() {
        val trace =
            Question(
                prompt = "Was gibt das Programm aus?",
                given = "int a = 3;\nint b = a << 2;\nprintf(\"%d\\n\", b);",
                answers = listOf("6", "12", "24"),
                correctIndex = 1,
                topic = "C",
            )
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "trace",
                    cards = listOf(Card(trace)),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                )
            }
        }
        capture("13-trace")
    }

    /** The labels that cut across the parts, with one of them picked */
    @Test
    fun subtopicsFilteredByTag() {
        val exam =
            Deck(
                id = "klausur",
                name = "Technische Informatik",
                subtopics =
                    listOf(
                        Subtopic("k-0", "Verkettete Listen", listOf(Card(nodeDelete))),
                        Subtopic(
                            "k-1",
                            "MATLAB",
                            listOf(
                                Card(columnVector),
                                Card(
                                    Question(
                                        prompt = "Was liefert size([3 6 2])?",
                                        answers = listOf("1 3", "3 1", "3"),
                                        correctIndex = 0,
                                        tags = listOf("SS25"),
                                    ),
                                ),
                            ),
                        ),
                    ),
            )
        composeRule.setContent {
            BisonTheme {
                SubtopicScreen(deck = exam, onOpen = {}, onStudyAll = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("WS24").performClick()
        capture("14-tags")
    }

    /** A card that made its own sum up, which is a different sum every time it is asked */
    @Test
    fun generatedRound() {
        val toHex =
            GeneratedTask(
                kind = GenKind.Convert,
                from = 2,
                to = 16,
                bits = 8,
                topic = "Zahlensysteme",
                tags = listOf("WS24"),
            )
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "gen",
                    cards = listOf(Card(toHex)),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                )
            }
        }
        capture("15-generated")
    }

    /**
     * A stand-in for a diagram, drawn here so the test carries no binary file of its own.
     *
     * Boxes and a line: enough to see that a picture is scaled, rounded and placed the way the
     * rest of a card is, which is all these screenshots can tell anybody about a picture.
     */
    private fun chartPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF1F1F1F.toInt())
        val ink = Paint().apply { color = 0xFFE9E9E9.toInt() }
        canvas.drawRect(48f, 40f, 300f, 120f, ink)
        canvas.drawRect(340f, 200f, 592f, 280f, ink)
        canvas.drawRect(170f, 120f, 178f, 240f, ink)
        canvas.drawRect(170f, 236f, 466f, 244f, ink)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** A card answered on paper: the task, then the answer, then the reader's own verdict */
    @Test
    fun sketchCard() {
        val dir = File(System.getProperty("java.io.tmpdir"), "bison-shots-${System.nanoTime()}")
        val images = ImageStore(dir)
        images.save("node-delete-chart.png", chartPng())
        val sketch =
            SketchTask(
                prompt = "Zeichne das Activity Chart zu node_delete",
                given = "void node_delete(node_t *n) {\n    free(n->data);\n    free(n);\n}",
                answerImage = "node-delete-chart.png",
                topic = "UML",
                tags = listOf("WS24"),
            )
        composeRule.setContent {
            BisonTheme {
                CompositionLocalProvider(LocalImages provides images) {
                    StudyScreen(
                        key = "sketch",
                        cards = listOf(Card(sketch)),
                        soundOn = false,
                        onFinished = {},
                        onLeave = {},
                    )
                }
            }
        }
        capture("16-sketch")
        composeRule.onNodeWithText("Lösung zeigen").performClick()
        capture("17-sketch-answer")
    }

    /**
     * The wash of colour over the screen when an answer lands, caught while it is there.
     *
     * It was written down as unverifiable because Roborazzi photographs a screen that has come
     * to rest, and by then the wash is gone. That is a stopped clock away: with the test clock
     * held, the shutter can be opened at the top of the animation instead of after it.
     */
    @Test
    fun studyFlash() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BisonTheme {
                StudyScreen(key = "s", cards = listOf(breakdown), soundOn = false, onFinished = {}, onLeave = {})
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Auf der Fahrbahn stehen bleiben und winken").performClick()
        // it fades in over 110 ms and away again over 420; this is the peak
        composeRule.mainClock.advanceTimeBy(110)
        capture("21-study-flash")
    }

    /** Setting the paper up: how many questions out of each part, and how long there is */
    @Test
    fun examSetup() {
        composeRule.setContent {
            BisonTheme { ExamSetupScreen(deck = sampleDeck(), onStart = {}, onBack = {}) }
        }
        capture("18-exam-setup")
    }

    /**
     * The paper being sat: a clock, no colour on the boxes, and a way back through the questions.
     *
     * The clock is stopped for the picture. It ticks once a second and takes the wall clock
     * whenever that says less; under the test clock a tick costs nothing at all, so left to
     * itself it would run the whole two hours out before the shutter.
     */
    @Test
    fun examSitting() {
        val paper = ExamDraw.draw(sampleDeck(), ExamPlan(mapOf("sample-1" to 2)), Random(1))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BisonTheme { ExamScreen(exam = paper, onLeave = {}, onDone = {}) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        capture("19-exam")
    }

    /** What it came to, part by part, which is the half that says what to revise */
    @Test
    fun examResult() {
        val paper =
            ExamDraw.draw(
                sampleDeck(),
                ExamPlan(mapOf("sample-0" to 1, "sample-1" to 2, "sample-2" to 2)),
                Random(2),
            )
        // two right, three not, so the bars have something to say
        for (at in listOf(0, 3)) {
            val asked = paper.item(at).task as Question
            paper.pick(at, paper.item(at).order.indexOf(asked.correctIndex))
        }
        composeRule.setContent {
            BisonTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(BisonColors.Background)
                            .padding(horizontal = BisonShape.Gutter, vertical = 24.dp),
                ) {
                    ExamResult(exam = paper, onDone = {})
                }
            }
        }
        capture("20-exam-result")
    }

    private companion object {
        const val OUTPUT_DIR = "build/outputs/roborazzi"
    }
}

package net.bison.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.bison.domain.Exam
import net.bison.domain.ExamItem
import net.bison.domain.LineChange
import net.bison.domain.LineDiff
import net.bison.domain.LineMark
import net.bison.domain.Marking
import net.bison.model.CodeTask
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.SketchTask
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * Sitting a mock paper, marking it, and reading what it came to.
 *
 * Nothing is given away while it is being sat. A ticked box goes grey rather than green or red,
 * a typed answer gets no verdict, and the questions can be leafed through in both directions -
 * which is the difference between a mock and a study session, and the reason this screen exists
 * next to the other one rather than inside it.
 *
 * Marking comes after handing in, and only for the questions the app cannot mark itself: a
 * written function is marked line by line at the exam's own rates, a drawing is marked by
 * looking at it. Then the parts, because a total says the paper went badly and the parts say
 * which twenty minutes of revision would have fixed it.
 */
@Composable
fun ExamScreen(
    exam: Exam,
    onLeave: () -> Unit,
    onDone: () -> Unit,
) {
    var phase by remember(exam) { mutableStateOf(Phase.Sitting) }
    var at by remember(exam) { mutableIntStateOf(0) }
    // bumped on every answer and every mark, so the screen redraws off the paper's new state
    var writes by remember(exam) { mutableIntStateOf(0) }
    var left by remember(exam) { mutableLongStateOf(exam.minutes * 60L) }

    fun handIn() {
        phase = if (exam.unmarked.isEmpty()) Phase.Result else Phase.Marking
    }

    // The clock counts its own ticks and takes the wall clock whenever that says less. Ticks
    // alone would stop while the app is put away; the wall clock alone would never finish where
    // a tick costs no time at all, which is exactly the case in a test.
    val endsAt = remember(exam) { System.currentTimeMillis() + exam.minutes * 60_000L }
    LaunchedEffect(exam, phase) {
        if (phase != Phase.Sitting) return@LaunchedEffect
        while (left > 0) {
            delay(TICK)
            left = minOf(left - 1, (endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        }
        handIn()
    }

    BackHandler { onLeave() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BisonColors.Background)
                .systemBarsPadding()
                .padding(horizontal = BisonShape.Gutter),
    ) {
        when (phase) {
            Phase.Sitting -> {
                val item = remember(at, writes) { exam.item(at) }
                Header(
                    left = "Frage ${at + 1} / ${exam.size}",
                    right = clockLine(left),
                    urgent = left <= URGENT,
                    onLeave = onLeave,
                )
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Caption(text = item.block.uppercase())
                    Spacer(Modifier.height(14.dp))
                    Paper(
                        item = item,
                        at = at,
                        onPick = { position ->
                            exam.pick(at, position)
                            writes++
                        },
                        onWrite = { text ->
                            exam.write(at, text)
                            writes++
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (at > 0) {
                        BisonButton(
                            text = "Zurück",
                            onClick = { at-- },
                            filled = false,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    BisonButton(
                        text = if (at == exam.size - 1) "Abgeben" else "Weiter",
                        onClick = { if (at == exam.size - 1) handIn() else at++ },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Caption(text = "${exam.attempted} von ${exam.size} bearbeitet")
                Spacer(Modifier.height(16.dp))
            }

            Phase.Marking -> {
                val next = remember(writes) { exam.unmarked.firstOrNull() }
                if (next == null) {
                    // writing state during composition would risk looping, so step on afterwards
                    LaunchedEffect(writes) { phase = Phase.Result }
                } else {
                    Header(
                        left = "Noch ${exam.unmarked.size} zu bewerten",
                        right = "",
                        urgent = false,
                        onLeave = onLeave,
                    )
                    MarkOne(
                        item = exam.item(next),
                        onAward = { points ->
                            exam.award(next, points)
                            writes++
                        },
                    )
                }
            }

            Phase.Result -> {
                Header(left = "Ergebnis", right = "", urgent = false, onLeave = onDone)
                ExamResult(exam = exam, onDone = onDone)
            }
        }
    }
}

private enum class Phase { Sitting, Marking, Result }

/** One row across the top: the way out, where in the paper this is, and the clock */
@Composable
private fun Header(
    left: String,
    right: String,
    urgent: Boolean,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BisonColors.Surface)
                    .clickable(onClickLabel = "Schluss", onClick = onLeave),
        ) {
            Text(text = "×", style = MaterialTheme.typography.titleMedium, color = BisonColors.TextSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = left,
            style = MaterialTheme.typography.labelLarge,
            color = BisonColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (right.isNotEmpty()) {
            Text(
                text = right,
                style = MaterialTheme.typography.titleMedium,
                color = if (urgent) BisonColors.Wrong else BisonColors.TextPrimary,
            )
        }
    }
}

/** One question of the paper, answerable and saying nothing about whether it is right */
@Composable
private fun Paper(
    item: ExamItem,
    at: Int,
    onPick: (Int) -> Unit,
    onWrite: (String) -> Unit,
) {
    TaskFront(item.task)
    Spacer(Modifier.height(16.dp))

    when (val asked = item.task) {
        is Question -> {
            for (position in item.order.indices) {
                Ticked(
                    text = asked.answers[item.order[position]],
                    picked = item.picked == position,
                    onClick = { onPick(position) },
                )
                Spacer(Modifier.height(BisonShape.Gap))
            }
        }

        is GeneratedTask -> {
            item.rolled?.let { rolled ->
                GivenCode(code = rolled.question)
                Spacer(Modifier.height(16.dp))
            }
            Written(item = item, at = at, onWrite = onWrite, oneLine = true)
        }

        is CodeTask -> Written(item = item, at = at, onWrite = onWrite, oneLine = asked.isOneLiner)

        is SketchTask -> {
            Caption(text = "AUF PAPIER BEANTWORTEN")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Diese Aufgabe wird nach der Abgabe selbst bewertet.",
                style = MaterialTheme.typography.bodyMedium,
                color = BisonColors.TextSecondary,
            )
        }
    }
}

/** The field a written answer goes in, kept in step with the paper on every keystroke */
@Composable
private fun Written(
    item: ExamItem,
    at: Int,
    onWrite: (String) -> Unit,
    oneLine: Boolean,
) {
    // Keyed on which question this is, not on the item: the item is a fresh copy after every
    // keystroke, and remembering against that would empty the field while it is being typed in.
    var typed by remember(at) { mutableStateOf(TextFieldValue(item.written.orEmpty())) }

    if (oneLine) {
        OneLineField(
            value = typed,
            readOnly = false,
            outline = BisonColors.Border,
            onValueChange = {
                typed = it
                onWrite(it.text)
            },
        )
    } else {
        CodeEditor(
            value = typed,
            onValueChange = {
                typed = it
                onWrite(it.text)
            },
        )
    }
    Spacer(Modifier.height(12.dp))
    SymbolBar(onInsert = {
        typed = typed.insert(it)
        onWrite(typed.text)
    })
}

/**
 * One option on the paper.
 *
 * Grey when it is ticked, and grey is all it ever gets: an exam does not tell you.
 */
@Composable
private fun Ticked(
    text: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(if (picked) BisonColors.SurfaceRaised else BisonColors.Surface)
                .border(
                    BorderStroke(1.dp, if (picked) BisonColors.TextPrimary else BisonColors.Border),
                    RoundedCornerShape(BisonShape.Radius),
                ).clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = BisonColors.TextPrimary,
        )
    }
}

/** Marking the one kind of question the app cannot mark itself */
@Composable
private fun MarkOne(
    item: ExamItem,
    onAward: (Double) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Caption(text = item.block.uppercase())
        Spacer(Modifier.height(14.dp))
        TaskFront(item.task)
        Spacer(Modifier.height(18.dp))

        when (val asked = item.task) {
            is CodeTask -> {
                val mine = remember(item) { CodeTask.lines(item.written.orEmpty()) }
                val rows = remember(item) { LineDiff.compare(mine, LineDiff.bestMatch(mine, asked.accepted)) }
                val marks = remember(item) { mutableStateListOf<LineMark>().also { it += Marking.from(rows).marks } }
                val marking = Marking(marks.toList(), maxPoints = rows.count { it.change != LineChange.Extra })

                LineMarking(rows = rows, marks = marks, onCycle = { at -> marks[at] = marks[at].next() })
                Spacer(Modifier.height(18.dp))
                Text(
                    text = marking.asScore(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (marking.clean) BisonColors.Correct else BisonColors.Almost,
                )
                Spacer(Modifier.height(16.dp))
                BisonButton(text = "Übernehmen", onClick = { onAward(marking.points) })
            }

            else -> {
                Caption(text = "LÖSUNG")
                Spacer(Modifier.height(10.dp))
                (asked as? SketchTask)?.answerImage?.let {
                    CardPicture(name = it)
                    Spacer(Modifier.height(12.dp))
                }
                (asked as? SketchTask)?.answer?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = BisonColors.TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(6.dp))
                BisonButton(text = "Hatte ich", onClick = { onAward(item.maxPoints) })
                Spacer(Modifier.height(10.dp))
                BisonButton(text = "Hatte ich nicht", onClick = { onAward(0.0) }, filled = false)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** What the paper came to, part by part */
@Composable
fun ExamResult(
    exam: Exam,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = exam.asScore(),
            style = MaterialTheme.typography.displayMedium,
            color = BisonColors.progressColor(fraction(exam.points, exam.maxPoints)),
        )
        Spacer(Modifier.height(6.dp))
        Caption(text = "PUNKTE")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            for (block in exam.blocks()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BisonShape.Radius))
                            .background(BisonColors.Surface)
                            .padding(20.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = block.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = BisonColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${Marking.format(block.points)} / ${Marking.format(block.maxPoints)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = BisonColors.TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Caption(text = questionCount(block.questions))
                    Spacer(Modifier.height(14.dp))
                    ProgressBar(fraction = fraction(block.points, block.maxPoints), height = 10.dp)
                }
                Spacer(Modifier.height(BisonShape.Gap))
            }
            Spacer(Modifier.height(16.dp))
        }

        BisonButton(text = "Fertig", onClick = onDone)
        Spacer(Modifier.height(20.dp))
    }
}

private fun fraction(
    points: Double,
    max: Double,
): Float = if (max <= 0.0) 0f else (points / max).toFloat()

/** "1:59:04" while there is an hour left, "59:04" once there is not */
private fun clockLine(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    val twoDigits = { value: Long -> value.toString().padStart(2, '0') }
    return if (hours > 0) "$hours:${twoDigits(minutes)}:${twoDigits(rest)}" else "${twoDigits(minutes)}:${twoDigits(rest)}"
}

/** How often the clock is read. A second, because it is written to the second. */
private const val TICK = 1000L

/** When the clock turns red: the last five minutes, which is when it starts to matter */
private const val URGENT = 300L

package net.bison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.bison.domain.RowDrag
import net.bison.model.CodeTask
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * Put the model answer's lines back in order.
 *
 * This is the easier half of writing code: the pieces are given, only the sequence is missing.
 * A card starts here and is promoted to writing it out once it has been sorted cleanly twice, so
 * the first meeting with a function is not a blank page.
 *
 * Every row is one line of code at one fixed height. That is not only for looks: a row that can
 * wrap would make rows of different heights, and the drag would then have to measure each one to
 * know which it is over. Code lines do not wrap here; they scroll sideways.
 */
@Composable
fun SortRound(
    task: CodeTask,
    round: String,
    onSubmit: (correct: Boolean) -> Unit,
) {
    val target = remember(task) { task.solutionLines }
    val order = remember(task) { mutableStateListOf<Int>().also { it += target.indices.shuffled() } }
    var verdict by remember(task) { mutableStateOf<Boolean?>(null) }

    // which row the finger picked up, and how far it has travelled from where it started
    var dragging by remember(task) { mutableIntStateOf(-1) }
    var travel by remember(task) { mutableStateOf(0f) }
    val rowPx = with(LocalDensity.current) { (ROW_HEIGHT + ROW_GAP).toPx() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(6.dp))
        Caption(text = round)
        Spacer(Modifier.height(14.dp))
        TaskFront(task)
        Spacer(Modifier.height(10.dp))
        Caption(text = "Zeilen in die richtige Reihenfolge ziehen · lang drücken zum Greifen")
        Spacer(Modifier.height(20.dp))

        order.forEachIndexed { position, line ->
            // Keyed on the line rather than left to fall where it lands. Without this the rows
            // are told apart by their slot, so a swap hands every row below the finger a
            // different one - and with it the gesture that was running on it.
            key(line) {
                val held = position == dragging
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .graphicsLayer {
                                if (held) {
                                    translationY = travel
                                    // lifted off the stack, so it is obvious which row is in hand
                                    shadowElevation = 12f
                                }
                            }.clip(RoundedCornerShape(10.dp))
                            .background(if (held) BisonColors.SurfaceRaised else BisonColors.Surface)
                            // keyed on the line, not on where it currently sits: a swap changes
                            // every position below the finger, and re-keying this would throw
                            // away the gesture that is running on it
                            .pointerInput(task, line) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = order.indexOf(line)
                                        travel = 0f
                                    },
                                    onDragEnd = {
                                        dragging = -1
                                        travel = 0f
                                    },
                                    onDragCancel = {
                                        dragging = -1
                                        travel = 0f
                                    },
                                    // the drag distance is the second parameter; the change
                                    // itself carries a position, not a delta
                                    onDrag = { _, dragAmount ->
                                        travel += dragAmount.y
                                        val from = dragging
                                        if (from >= 0) {
                                            val step = RowDrag.step(from, travel, rowPx, order.size)
                                            if (step.to != from) {
                                                order.add(step.to, order.removeAt(from))
                                                travel = step.travel
                                                dragging = step.to
                                            }
                                        }
                                    },
                                )
                            }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = target[line],
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = BisonColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(ROW_GAP))
            }
        }

        Spacer(Modifier.height(18.dp))
        when (val settled = verdict) {
            null ->
                BisonButton(
                    text = "Prüfen",
                    onClick = { verdict = order.toList() == target.indices.toList() },
                )

            else -> {
                Text(
                    text = if (settled) "Reihenfolge stimmt" else "Noch nicht die richtige Reihenfolge",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (settled) BisonColors.Correct else BisonColors.Wrong,
                )
                Spacer(Modifier.height(14.dp))
                BisonButton(text = "Weiter", onClick = { onSubmit(settled) })
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** One line of code, one row, always the same height so the drag can count rows */
private val ROW_HEIGHT = 46.dp

private val ROW_GAP = BisonShape.Gap / 2

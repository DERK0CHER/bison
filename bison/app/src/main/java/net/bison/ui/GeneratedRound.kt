package net.bison.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bison.domain.Generator
import net.bison.model.GeneratedTask
import net.bison.ui.theme.BisonColors
import kotlin.random.Random

/**
 * A sum the card has just made up, and one line to answer it with.
 *
 * The numbers come from [seed] rather than from the clock, so a round drawn twice is the same
 * sum. A screen redraws itself constantly - one being animated redraws every frame - and a
 * question that changed underneath the reader while they worked it out would be worse than no
 * question at all. It also means the rendered screenshots show the same numbers every run.
 */
@Composable
fun GeneratedRound(
    task: GeneratedTask,
    round: String,
    seed: Int,
    onSubmit: (correct: Boolean) -> Unit,
) {
    val rolled = remember(task, seed) { Generator.roll(task, Random(seed)) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = round)
        Spacer(Modifier.height(14.dp))
        Text(
            text = task.prompt,
            style = MaterialTheme.typography.titleLarge,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(14.dp))
        GivenCode(code = rolled.question)
        Spacer(Modifier.height(18.dp))
        OneLineAnswer(
            key = rolled,
            accepts = { rolled.matches(it) },
            solution = rolled.answer,
            onSubmit = onSubmit,
            // The braces and arrows belong to writing C; what a hexadecimal answer needs is the
            // six letters, and a decimal one needs nothing the keyboard does not already show.
            // Asked of the card rather than of the roll, so the bar never says something about
            // the number that came up.
            symbols = if (task.wantsHex) SymbolSet.Hex else null,
        )
        Spacer(Modifier.height(20.dp))
    }
}

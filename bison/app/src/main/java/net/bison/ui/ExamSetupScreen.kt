package net.bison.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.bison.domain.ExamPlan
import net.bison.model.Deck
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape
import kotlin.math.roundToInt

/**
 * Setting up a mock paper: how many questions out of each part, and how long there is.
 *
 * This screen exists because the weighting is the whole exercise. A paper is twenty-five
 * multiple choice, four bits of theory, five programming exercises and twenty MATLAB, and only
 * the person sitting it knows that - so they set it, once, and the numbers stay where they were
 * put for the next mock.
 *
 * Sliders rather than plus and minus buttons: setting a part to twenty-five with a stepper is
 * twenty-five taps, and nothing here needs to be exact to the question.
 */
@Composable
fun ExamSetupScreen(
    deck: Deck,
    onStart: (ExamPlan) -> Unit,
    onBack: () -> Unit,
) {
    val counts =
        remember(deck.id) {
            mutableStateMapOf<String, Int>().apply {
                deck.subtopics.forEach { put(it.id, it.cards.size.coerceAtMost(DEFAULT_PER_PART)) }
            }
        }
    var minutes by remember(deck.id) { mutableIntStateOf(ExamPlan.DEFAULT_MINUTES) }
    val total = deck.subtopics.sumOf { counts[it.id] ?: 0 }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BisonColors.Background)
                .systemBarsPadding()
                .padding(horizontal = BisonShape.Gutter),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BisonColors.Surface)
                        .clickable(onClickLabel = "Zurück", onClick = onBack),
            ) {
                Text(text = "←", style = MaterialTheme.typography.titleMedium, color = BisonColors.TextSecondary)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Klausur",
                style = MaterialTheme.typography.displaySmall,
                color = BisonColors.TextPrimary,
            )
        }

        Spacer(Modifier.height(10.dp))
        Caption(text = "WIE VIELE FRAGEN AUS WELCHEM TEIL")

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(18.dp))
            for (subtopic in deck.subtopics) {
                Amount(
                    name = subtopic.name,
                    value = counts[subtopic.id] ?: 0,
                    max = subtopic.cards.size,
                    onChange = { counts[subtopic.id] = it },
                )
                Spacer(Modifier.height(14.dp))
            }
            Amount(
                name = "Zeit",
                value = minutes,
                max = MAX_MINUTES,
                min = STEP_MINUTES,
                step = STEP_MINUTES,
                unit = "Min",
                onChange = { minutes = it },
            )
            Spacer(Modifier.height(24.dp))
        }

        Caption(text = "$total Fragen · $minutes Minuten")
        Spacer(Modifier.height(10.dp))
        BisonButton(
            text = if (total == 0) "Nichts ausgewählt" else "Klausur starten",
            enabled = total > 0,
            onClick = { onStart(ExamPlan(counts.toMap(), minutes)) },
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** One row: what it is, how many, and a slider to say so */
@Composable
private fun Amount(
    name: String,
    value: Int,
    max: Int,
    onChange: (Int) -> Unit,
    min: Int = 0,
    step: Int = 1,
    unit: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = BisonColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (unit == null) "$value / $max" else "$value $unit",
                style = MaterialTheme.typography.titleMedium,
                color = if (value == 0) BisonColors.TextMuted else BisonColors.TextPrimary,
            )
        }
        // a part holding one question has nothing to slide, and a slider with no steps in it
        // reads as broken rather than as full
        if (max > min) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange((it / step).roundToInt() * step) },
                valueRange = min.toFloat()..max.toFloat(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = BisonColors.TextPrimary,
                        activeTrackColor = BisonColors.TextPrimary,
                        inactiveTrackColor = BisonColors.SurfaceRaised,
                    ),
            )
        }
    }
}

/** What a part starts at, before anybody says otherwise */
private const val DEFAULT_PER_PART = 10

private const val MAX_MINUTES = 240

private const val STEP_MINUTES = 15

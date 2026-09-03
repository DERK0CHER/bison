package net.bison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.bison.domain.Schedule
import net.bison.model.Deck
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * The home screen: what there is to learn, and how far along each topic is.
 *
 * A masthead at the top and everything actionable gathered at the bottom, within reach of a
 * thumb. A short list sits in the middle of what is left, so the leftover space reads as air
 * rather than as a hole at one end.
 */
@Composable
fun DeckListScreen(
    decks: List<Deck>,
    soundOn: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onOpen: (Deck) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    // read once for the whole list: every row asks the same question of the calendar, and a
    // screen that answered it twice differently would be a screen opened at midnight
    val today = remember { Schedule.today() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BisonColors.Background)
                .systemBarsPadding()
                .padding(horizontal = BisonShape.Gutter),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "bison",
            style = MaterialTheme.typography.displayLarge,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Caption(text = summaryLine(decks))

        if (decks.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BisonShape.Gap, Alignment.CenterVertically),
                contentPadding = PaddingValues(top = 24.dp, bottom = 4.dp),
            ) {
                items(decks, key = { it.id }) { deck ->
                    DeckRow(deck = deck, today = today, onClick = { onOpen(deck) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillToggle(checked = soundOn, label = "Ton", onCheckedChange = onSoundChange)
            Spacer(Modifier.weight(1f))
            QuietAction(text = "sichern", onClick = onExport)
            Spacer(Modifier.width(8.dp))
            QuietAction(text = "laden", onClick = onRestore)
        }
        Spacer(Modifier.height(14.dp))
        BisonButton(text = "Fragen einfügen", onClick = onImport)
        Spacer(Modifier.height(20.dp))
    }
}

/** One quiet line under the wordmark, so the header says what the app currently holds */
private fun summaryLine(decks: List<Deck>): String {
    if (decks.isEmpty()) return "Multiple Choice, bis es sitzt"
    val questions = decks.sumOf { it.cards.size }
    val themes = if (decks.size == 1) "1 Thema" else "${decks.size} Themen"
    val count = if (questions == 1) "1 Frage" else "$questions Fragen"
    return "$themes · $count"
}

/**
 * The first launch, laid out as an invitation rather than a shrug.
 *
 * The three steps stand where the topics will later stand - directly above the button that
 * starts them - so the empty screen already has the shape of the full one.
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = "Noch leer.",
            style = MaterialTheme.typography.displayMedium,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Eine KI schreibt dir die Fragen. Bison fragt sie ab, bis jede sitzt.",
            style = MaterialTheme.typography.bodyLarge,
            color = BisonColors.TextSecondary,
        )
        Spacer(Modifier.height(30.dp))
        StepLabel(number = "1", text = "Prompt kopieren", textColor = BisonColors.TextSecondary)
        Spacer(Modifier.height(14.dp))
        StepLabel(number = "2", text = "Von einer KI beantworten lassen", textColor = BisonColors.TextSecondary)
        Spacer(Modifier.height(14.dp))
        StepLabel(number = "3", text = "Antwort hier einfügen", textColor = BisonColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One topic: its name, what it holds, and one bar.
 *
 * The bar had a caption reading "Lern-O-Meter" over it. A bar running red to green with a
 * percentage beside it does not need a word to say that it is progress.
 */
@Composable
private fun DeckRow(
    deck: Deck,
    today: Long,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .clickable(onClick = onClick)
                .padding(22.dp),
    ) {
        Text(
            text = deck.name,
            style = MaterialTheme.typography.titleLarge,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Caption(text = deckLine(deck))
        Spacer(Modifier.height(4.dp))
        // what the schedule has to say, in its own colour when there is something to do: this
        // is the line the app is opened for
        Caption(
            text = dueLine(deck.cards, today),
            color = if (deck.dueCount(today) > 0) BisonColors.Almost else BisonColors.TextMuted,
        )
        Spacer(Modifier.height(16.dp))
        ProgressBar(fraction = deck.progress, height = 12.dp)
    }
}

/** Parts and questions, or just questions when the topic was never split up */
private fun deckLine(deck: Deck): String {
    val questions = countLine(deck.cards.size, deck.learnedCount)
    if (deck.subtopics.size <= 1) return questions
    return "${deck.subtopics.size} Bereiche · $questions"
}

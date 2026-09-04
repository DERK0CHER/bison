package net.bison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.bison.domain.Schedule
import net.bison.model.Deck
import net.bison.model.Subtopic
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * The parts of one topic, each with its own progress.
 *
 * A theory paper is not one subject but a dozen, and knowing the signs are through while right
 * of way is still red is the reason to split them at all. The bar at the top is all of them
 * together, weighted by how many questions each part holds.
 *
 * The parts are how a set was written; the tags underneath them cut across it. A set of exam
 * questions is filed by subject but revised by exam and by kind of exercise - "everything from
 * WS24", "every Node_Delete variant" - and that is a second question about the same cards, not
 * a second way of filing them.
 */
@Composable
fun SubtopicScreen(
    deck: Deck,
    onOpen: (Subtopic) -> Unit,
    onStudyAll: () -> Unit,
    onBack: () -> Unit,
    onStudyTagged: (Set<String>) -> Unit = {},
    onExam: () -> Unit = {},
    onStudyLeeches: () -> Unit = {},
    reversed: Boolean = false,
    onReversedChange: (Boolean) -> Unit = {},
) {
    // Which labels are being narrowed down to. Held here rather than by the caller: it is the
    // question this screen asks, and it is answered again every time the screen is opened.
    val selected = remember(deck.id) { mutableStateListOf<String>() }
    val tagged = deck.cardsTagged(selected.toSet())
    val today = remember { Schedule.today() }

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
                Text(
                    text = "←",
                    style = MaterialTheme.typography.titleMedium,
                    color = BisonColors.TextSecondary,
                )
            }
            Spacer(Modifier.width(16.dp))
            ProgressBar(fraction = deck.progress, modifier = Modifier.weight(1f), height = 10.dp)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = deck.name,
            style = MaterialTheme.typography.displayMedium,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Caption(text = countLine(deck.cards.size, deck.learnedCount))
        Spacer(Modifier.height(4.dp))
        Caption(text = listOfNotNull(dueLine(deck.cards, today), timeLine(deck.seconds)).joinToString(" · "))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BisonShape.Gap, Alignment.CenterVertically),
            contentPadding = PaddingValues(top = 24.dp, bottom = 4.dp),
        ) {
            items(deck.subtopics, key = { it.id }) { subtopic ->
                SubtopicRow(subtopic = subtopic, today = today, onClick = { onOpen(subtopic) })
            }
        }

        if (deck.tags.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Caption(text = "TAGS")
            Spacer(Modifier.height(10.dp))
            TagRow(
                tags = deck.tags,
                selected = selected.toSet(),
                onToggle = { tag -> if (!selected.remove(tag)) selected += tag },
            )
        }

        Spacer(Modifier.height(14.dp))
        // the reasoning as the question and the answer to be produced: the same cards read the
        // other way round, which is the direction an exam asks them in
        PillToggle(checked = reversed, label = "Umgekehrt", onCheckedChange = onReversedChange)
        Spacer(Modifier.height(16.dp))
        if (selected.isEmpty()) {
            BisonButton(text = "Alles gemischt lernen", onClick = onStudyAll)
        } else {
            BisonButton(
                text = if (tagged.isEmpty()) "Keine Karte mit dieser Auswahl" else "${cardCount(tagged.size)} lernen",
                enabled = tagged.isNotEmpty(),
                onClick = { onStudyTagged(selected.toSet()) },
            )
        }
        // The cards that keep going: worth their own way in, because the answer to a leech is
        // usually to rewrite it rather than to see it again, and that starts with finding it.
        if (deck.leeches.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BisonButton(
                // an adjective goes between the number and the noun in German, so this one
                // cannot come out of the shared wording
                text = if (deck.leeches.size == 1) "1 schwierige Karte" else "${deck.leeches.size} schwierige Karten",
                onClick = onStudyLeeches,
                filled = false,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Quieter than the studying, because it is the rarer thing to want: a mock is worth
        // sitting once a week, and studying is worth doing every day.
        BisonButton(text = "Klausur", onClick = onExam, filled = false)
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The topic's labels, on one row that scrolls sideways.
 *
 * Sideways rather than wrapped: the row keeps its height whatever the set carries, so the parts
 * above it do not jump about as tags are picked, and a topic with twenty labels does not push
 * the button that uses them off the bottom of the screen.
 */
@Composable
private fun TagRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        for (tag in tags) {
            val on = tag in selected
            Text(
                text = tag,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) BisonColors.Background else BisonColors.TextSecondary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(BisonShape.Pill))
                        .background(if (on) BisonColors.TextPrimary else BisonColors.Surface)
                        .clickable { onToggle(tag) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SubtopicRow(
    subtopic: Subtopic,
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
                .padding(20.dp),
    ) {
        Text(
            text = subtopic.name,
            style = MaterialTheme.typography.titleMedium,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Caption(text = countLine(subtopic.cards.size, subtopic.learnedCount))
        Spacer(Modifier.height(2.dp))
        Caption(
            text = dueLine(subtopic.cards, today),
            color = if (subtopic.dueCount(today) > 0) BisonColors.Almost else BisonColors.TextMuted,
        )
        Spacer(Modifier.height(14.dp))
        ProgressBar(fraction = subtopic.progress, height = 10.dp)
    }
}

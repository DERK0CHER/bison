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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bison.model.SketchTask
import net.bison.ui.theme.BisonColors

/**
 * The card that is answered away from the phone.
 *
 * An activity chart is drawn on paper and no app is going to compare a drawing, so this one
 * shows the task, waits, and then shows the answer. Whether the drawing matches it is the
 * reader's own call - the same call the marking of a written answer already leaves to them, and
 * for the same reason: the alternative is not a stricter app but a useless one.
 *
 * The answer is behind a button rather than further down the page. Scrolling past it by accident
 * would give the card away, and the whole point of this one is the minute spent working it out
 * before looking.
 */
@Composable
fun RevealRound(
    task: SketchTask,
    round: String,
    onSubmit: (correct: Boolean) -> Unit,
) {
    var shown by remember(task) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = round)
        Spacer(Modifier.height(14.dp))
        TaskFront(task)
        Spacer(Modifier.height(18.dp))

        if (!shown) {
            BisonButton(text = "Lösung zeigen", onClick = { shown = true })
        } else {
            Caption(text = "LÖSUNG")
            Spacer(Modifier.height(10.dp))
            task.answerImage?.let { picture ->
                CardPicture(name = picture)
                Spacer(Modifier.height(12.dp))
            }
            task.answer?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BisonColors.TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            // The honest question, and it is asked in the reader's own words rather than as
            // right and wrong: nobody draws a chart exactly the way the model answer draws it.
            BisonButton(text = "Hatte ich", onClick = { onSubmit(true) })
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Hatte ich nicht", onClick = { onSubmit(false) }, filled = false)
        }
        Spacer(Modifier.height(20.dp))
    }
}

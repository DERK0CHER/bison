package net.bison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import net.bison.importer.CardImport
import net.bison.model.Task
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * Getting questions in, without a keyboard.
 *
 * The questions are written in a chat with a language model, which means the text is already on
 * the clipboard. So this screen hands out the prompt to paste into that chat, then reads the
 * answer back off the clipboard. Nobody types a question set on a phone, and the paragraph-sized
 * text box this screen used to have could not scroll inside a scrolling page, so it clipped.
 *
 * Code cards come the other way: they are written at a desk and reach the phone as a file, so
 * the same screen will open one. Either way in ends at the same parser and the same preview.
 *
 * The way out is pinned to the bottom edge, where a cancel belongs, instead of trailing the
 * content into the middle of the screen.
 */
@Composable
fun ImportScreen(
    onCancel: () -> Unit,
    onImport: (name: String, tasks: List<Task>) -> Unit,
    onPickFile: () -> Unit = {},
    fileText: String? = null,
    onPickImages: () -> Unit = {},
    picturesAdded: Int = 0,
) {
    val clipboard = LocalClipboardManager.current
    var parsed by remember { mutableStateOf<CardImport.Result?>(null) }
    var promptCopied by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    // The file is picked outside this screen, because the picker belongs to the activity, and
    // its text arrives here. Both ways in end up at the same parser and the same preview.
    LaunchedEffect(fileText) {
        if (fileText != null) parsed = CardImport.parse(fileText)
    }

    val found = parsed?.tasks.orEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BisonColors.Background)
                // safeDrawing, not systemBars plus ime: the keyboard's inset already covers the
                // navigation bar, so adding both pushes the screen up by the keyboard AND the
                // bar again. safeDrawing takes whichever is larger.
                .safeDrawingPadding()
                .padding(horizontal = BisonShape.Gutter),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "einfügen",
                style = MaterialTheme.typography.displayMedium,
                color = BisonColors.TextPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Lass dir die Fragen von einer KI schreiben. Den Prompt dafür gibt es hier.",
                style = MaterialTheme.typography.bodyLarge,
                color = BisonColors.TextSecondary,
            )

            Spacer(Modifier.height(36.dp))
            StepLabel(number = "1", text = "Prompt kopieren, in einen KI-Chat einfügen")
            Spacer(Modifier.height(14.dp))
            BisonButton(
                text = if (promptCopied) "Prompt kopiert ✓" else "Prompt kopieren",
                onClick = {
                    clipboard.setText(AnnotatedString(AI_PROMPT))
                    promptCopied = true
                },
                filled = false,
            )

            Spacer(Modifier.height(28.dp))
            StepLabel(number = "2", text = "Antwort einlesen: aus der Zwischenablage oder aus einer Datei")
            Spacer(Modifier.height(14.dp))
            BisonButton(
                text = "Aus Zwischenablage einlesen",
                onClick = { parsed = CardImport.parse(clipboard.getText()?.text.orEmpty()) },
            )
            Spacer(Modifier.height(10.dp))
            // The card file for code is written at a desk and lands on the phone as a file, not
            // on the clipboard. Copying a page of code out of a file manager to paste it here is
            // the sort of fiddling that stops a set from being written at all.
            BisonButton(
                text = "Kartendatei vom Gerät öffnen",
                onClick = onPickFile,
                filled = false,
            )

            Spacer(Modifier.height(28.dp))
            StepLabel(number = "3", text = "Bilder dazu, falls Karten welche nennen")
            Spacer(Modifier.height(14.dp))
            // The pictures are added on their own and filed under their own names, because a
            // card file arrives as a content:// URI and nothing can be found relative to that.
            BisonButton(
                text = if (picturesAdded > 0) "$picturesAdded Bilder hinzugefügt ✓" else "Bilder hinzufügen",
                onClick = onPickImages,
                filled = false,
            )

            parsed?.let { result ->
                Spacer(Modifier.height(24.dp))
                ResultPanel(result = result, read = fileText)
            }

            if (found.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Caption(text = "NAME")
                Spacer(Modifier.height(8.dp))
                NameField(value = name, onValueChange = { name = it })
                Spacer(Modifier.height(20.dp))
                BisonButton(
                    text = "${found.size} Karten übernehmen",
                    onClick = { onImport(name.ifBlank { defaultName(found) }, found) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        BisonButton(text = "Abbrechen", onClick = onCancel, filled = false)
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * What was read, and what it turned out to be.
 *
 * When nothing was recognised it says what arrived - how much text, and its first line. "Nothing
 * recognised" on its own cannot tell an empty read from a file that came through whole and was
 * not understood, and those two have nothing in common but the message.
 *
 * @param read the text a picked file produced, if the last attempt came from one
 */
@Composable
private fun ResultPanel(
    result: CardImport.Result,
    read: String? = null,
) {
    val found = result.tasks.size
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .padding(22.dp),
    ) {
        Text(
            text =
                when {
                    found == 0 -> "Nichts erkannt"
                    result.skipped == 0 -> "$found Karten erkannt"
                    else -> "$found Karten erkannt, ${result.skipped} übersprungen"
                },
            style = MaterialTheme.typography.titleLarge,
            color = if (found == 0) BisonColors.Wrong else BisonColors.Correct,
        )
        Spacer(Modifier.height(10.dp))
        if (found == 0) {
            Text(
                text =
                    when {
                        read == null ->
                            "Steht der Text wirklich in der Zwischenablage? Erwartet wird das " +
                                "JSON aus dem Prompt oben, eine Kartendatei mit front:/back:, " +
                                "oder ein Kartenset mit type:/front:/logik:/back:."

                        read.isEmpty() ->
                            "Aus der Datei kam kein Text. Entweder ist sie leer, oder die " +
                                "App durfte sie nicht lesen — dann noch einmal über den " +
                                "Dateidialog auswählen statt über eine Verknüpfung."

                        else ->
                            "Gelesen wurden ${read.length} Zeichen, sie fangen so an:\n" +
                                read.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = BisonColors.TextSecondary,
            )
        } else {
            // which of the three readers understood it, which is also the quickest way to see
            // that a file was read by the wrong one
            Caption(
                text =
                    when (result.format) {
                        CardImport.Format.WrittenSet -> "KARTENSET"
                        CardImport.Format.CardFile -> "KARTENDATEI"
                        CardImport.Format.Questions -> "ERSTE FRAGE"
                    },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.tasks.first().label,
                style = MaterialTheme.typography.bodyMedium,
                color = BisonColors.TextSecondary,
            )
        }
    }
}

/** One line only: a single line stays usable with the keyboard up */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = BisonColors.TextPrimary),
        cursorBrush = SolidColor(BisonColors.TextPrimary),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .padding(horizontal = 18.dp, vertical = 18.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = "leer lassen für automatisch",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BisonColors.TextMuted,
                    )
                }
                inner()
            }
        },
    )
}

/** Names a set after its first question, so nothing ends up called just "Fragen" */
private fun defaultName(tasks: List<Task>): String {
    val first =
        tasks
            .firstOrNull()
            ?.label
            .orEmpty()
            .trim()
    if (first.isEmpty()) return "Fragen"
    return first.take(28).trimEnd() + if (first.length > 28) "…" else ""
}

/** Handed to the user's chat of choice, so the answer comes back in a shape the parser reads */
private const val AI_PROMPT =
    """Schreibe mir 60 Multiple-Choice-Fragen zum Thema: <HIER DEIN THEMA>

Teile sie in 4 bis 8 Unterbereiche auf und schreibe zu jeder Frage dazu, in welchen sie gehoert.

Antworte ausschliesslich mit JSON in genau dieser Form, ohne weiteren Text:

[
  {
    "topic": "Name des Unterbereichs",
    "question": "Worum geht es hier?",
    "answers": ["Erste Antwort", "Zweite Antwort", "Dritte Antwort"],
    "correct": 1
  }
]

Regeln:
- "topic" ist der Unterbereich, immer gesetzt, gleich geschrieben fuer alle Fragen darin
- "correct" ist der Index der richtigen Antwort, gezaehlt ab 0
- genau eine richtige Antwort pro Frage
- drei oder vier Antworten
- die falschen Antworten muessen plausibel sein, keine offensichtlichen Fuellsel
- keine Erklaerungen, keine Ueberschriften, kein Text vor oder nach dem JSON"""

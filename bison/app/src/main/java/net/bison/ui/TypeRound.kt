package net.bison.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bison.domain.LineDiff
import net.bison.model.CodeTask
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * One line, typed out, compared against the model answer.
 *
 * The card that asks for `d = [3;6;2;5;9]` used to open the whole editor: a text area six lines
 * tall, a line by line comparison and three marks to choose between, for an answer that is
 * either right or it is not. So a card whose model answer is a single line is asked here
 * instead, where the app decides and the reader only reads the verdict.
 *
 * It can decide because there is nothing to judge: with one line there is no renamed variable to
 * argue about and no half-right thought to be generous with. The spacing is normalised - see
 * [LineDiff.sameLine] - and a card that accepts two spellings lists the other under `alt:`.
 */
@Composable
fun TypeRound(
    task: CodeTask,
    round: String,
    onSubmit: (correct: Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = round)
        Spacer(Modifier.height(14.dp))
        TaskFront(task)
        Spacer(Modifier.height(18.dp))
        OneLineAnswer(
            key = task,
            accepts = { typed -> task.accepted.any { LineDiff.sameLine(typed, it) } },
            solution = task.solution,
            onSubmit = onSubmit,
        )
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The field a one-line answer is typed into, the verdict, and the way on.
 *
 * Shared by the cards whose model answer is one line and by the ones that make their own sums
 * up: both are answered the same way, and both are marked by the app rather than by the reader.
 *
 * @param key what the field belongs to; a new one empties it and takes the verdict back
 * @param accepts whether what was typed is right
 * @param solution shown after a wrong answer, and only then: after a right one it is already on
 *   the screen, written by the reader
 * @param symbols which extra characters to offer, or null for none - a decimal answer needs no
 *   bar of braces underneath it
 */
@Composable
fun OneLineAnswer(
    key: Any,
    accepts: (String) -> Boolean,
    solution: String,
    onSubmit: (correct: Boolean) -> Unit,
    symbols: SymbolSet? = SymbolSet.Code,
) {
    var typed by remember(key) { mutableStateOf(TextFieldValue("")) }
    var verdict by remember(key) { mutableStateOf<Boolean?>(null) }
    val settled = verdict

    Column(modifier = Modifier.fillMaxWidth()) {
        OneLineField(
            value = typed,
            readOnly = settled != null,
            outline =
                when (settled) {
                    null -> BisonColors.Border
                    true -> BisonColors.Correct
                    false -> BisonColors.Wrong
                },
            onValueChange = { typed = it },
        )

        if (settled == null) {
            symbols?.let { set ->
                Spacer(Modifier.height(12.dp))
                SymbolBar(onInsert = { typed = typed.insert(it) }, set = set)
            }
            Spacer(Modifier.height(18.dp))
            BisonButton(text = "Abgeben", onClick = { verdict = accepts(typed.text) })
        } else {
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (settled) "Richtig" else "Nicht ganz",
                style = MaterialTheme.typography.titleLarge,
                color = if (settled) BisonColors.Correct else BisonColors.Wrong,
            )
            if (!settled) {
                Spacer(Modifier.height(14.dp))
                Caption(text = "MUSTERLÖSUNG")
                Spacer(Modifier.height(8.dp))
                GivenCode(code = solution)
            }
            Spacer(Modifier.height(18.dp))
            BisonButton(text = "Weiter", onClick = { onSubmit(settled) })
        }
    }
}

/**
 * The editor's keyboard on one line.
 *
 * Same rules as the multi-line one, and for the same reason: autocorrect turns `int i` into
 * `Int i`. Return does not break the line here - there is only one - so it is labelled Done.
 */
@Composable
fun OneLineField(
    value: TextFieldValue,
    readOnly: Boolean,
    outline: Color,
    onValueChange: (TextFieldValue) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        textStyle =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = BisonColors.TextPrimary,
            ),
        cursorBrush = SolidColor(BisonColors.LearnedGreen),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .border(BorderStroke(1.dp, outline), RoundedCornerShape(BisonShape.Radius))
                .padding(horizontal = 16.dp, vertical = 18.dp),
    )
}

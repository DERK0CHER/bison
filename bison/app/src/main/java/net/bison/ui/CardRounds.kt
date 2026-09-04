package net.bison.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bison.domain.Params
import net.bison.domain.Typed
import net.bison.model.StudyCard
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape
import kotlin.random.Random

/**
 * A card with three sides, turned over one at a time.
 *
 * The reasoning comes before the answer on purpose. Reading the answer to "why does this
 * concatenation fail" teaches nothing that transfers to the next one; the pattern - same number
 * of rows to go side by side - is the part worth having, so it gets its own tap and is read
 * before the answer is available to read instead of it.
 *
 * A card with no reasoning written on it turns over once, straight to the answer.
 */
@Composable
fun FlipRound(
    card: StudyCard,
    round: String,
    onSubmit: (correct: Boolean) -> Unit,
    reversed: Boolean = false,
) {
    var turned by remember(card, reversed) { mutableStateOf(0) }
    // turned round, the reasoning is the question and there is only the answer left to show
    val backwards = reversed && card.logic != null
    val stages = if (card.logic == null || backwards) 1 else 2

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = if (backwards) "$round · umgekehrt" else round)
        Spacer(Modifier.height(14.dp))
        Prompt(text = if (backwards) card.logic.orEmpty() else card.prompt)

        if (turned >= 1 && card.logic != null && !backwards) {
            Spacer(Modifier.height(20.dp))
            Caption(text = "LOGIK")
            Spacer(Modifier.height(8.dp))
            Answer(text = card.logic, tint = BisonColors.Almost)
        }
        if (turned >= stages) {
            Spacer(Modifier.height(20.dp))
            Caption(text = "LÖSUNG")
            Spacer(Modifier.height(8.dp))
            Answer(text = card.back, tint = BisonColors.TextPrimary)
        }

        Spacer(Modifier.height(22.dp))
        if (turned < stages) {
            BisonButton(
                text = if (turned == 0 && stages == 2) "Logik zeigen" else "Lösung zeigen",
                onClick = { turned++ },
            )
        } else {
            // the reader is the only one who can say whether they had it
            BisonButton(text = "Gewusst", onClick = { onSubmit(true) })
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Nicht gewusst", onClick = { onSubmit(false) }, filled = false)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Three options, and the letter is the answer.
 *
 * The options are written into the question in the file, one line under it, so they are shown
 * as they were written and the three buttons carry nothing but a, b and c. Putting the option
 * text on the buttons as well would say everything twice on a screen that has no room for it.
 */
@Composable
fun PickRound(
    card: StudyCard,
    round: String,
    onSubmit: (correct: Boolean) -> Unit,
) {
    var picked by remember(card) { mutableStateOf<Char?>(null) }
    val right = card.correctOption

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = round)
        Spacer(Modifier.height(14.dp))
        Prompt(text = card.prompt)
        Spacer(Modifier.height(22.dp))

        for (option in StudyCard.OPTIONS) {
            val chosen = picked
            OptionButton(
                letter = option,
                state =
                    when {
                        chosen == null -> OptionState.Open
                        option == right -> OptionState.Right
                        option == chosen -> OptionState.Wrong
                        else -> OptionState.Quiet
                    },
                onClick = { if (picked == null) picked = option },
            )
            Spacer(Modifier.height(10.dp))
        }

        picked?.let { chosen ->
            Spacer(Modifier.height(12.dp))
            Caption(text = "LÖSUNG")
            Spacer(Modifier.height(8.dp))
            Answer(text = card.back, tint = BisonColors.TextPrimary)
            card.logic?.let {
                Spacer(Modifier.height(14.dp))
                Caption(text = "LOGIK")
                Spacer(Modifier.height(8.dp))
                Answer(text = it, tint = BisonColors.TextSecondary)
            }
            Spacer(Modifier.height(20.dp))
            BisonButton(text = "Weiter", onClick = { onSubmit(chosen == right) })
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Write the answer out, and have it compared.
 *
 * The comparison is narrow on purpose - whitespace normalised, capitals kept, otherwise exact -
 * because in MATLAB and C the capitals and the semicolons are the answer. What it cannot judge
 * is whether a different-looking answer is the same answer, so a failed comparison is not the
 * verdict: what was typed and what was expected go up one above the other, and the reader says
 * which of the three it was. That is the same division of labour the exam itself uses.
 *
 * A parametrised card rolls its numbers from the round it is being asked in, so the question
 * stands still while it is being answered and is a different one when the card comes back.
 */
@Composable
fun AnswerRound(
    card: StudyCard,
    round: String,
    seed: Int,
    onSubmit: (correct: Boolean) -> Unit,
    reversed: Boolean = false,
) {
    val filled = remember(card, seed) { fill(card, seed) }
    var typed by remember(filled, reversed) { mutableStateOf(TextFieldValue("")) }
    var verdict by remember(filled, reversed) { mutableStateOf<Boolean?>(null) }
    val settled = verdict
    // turned round, the reasoning is the question and the answer is still what gets typed
    val backwards = reversed && filled.logic != null

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = if (backwards) "$round · umgekehrt" else round)
        Spacer(Modifier.height(14.dp))
        Prompt(text = if (backwards) filled.logic.orEmpty() else filled.prompt)
        Spacer(Modifier.height(18.dp))

        Field(
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
            Spacer(Modifier.height(12.dp))
            SymbolBar(onInsert = { typed = typed.insert(it) }, set = SymbolSet.Study)
            Spacer(Modifier.height(18.dp))
            BisonButton(
                text = "Abgeben",
                onClick = { verdict = Typed.matches(typed.text, filled.accepted) },
            )
        } else if (settled) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Richtig",
                style = MaterialTheme.typography.titleLarge,
                color = BisonColors.Correct,
            )
            Spacer(Modifier.height(18.dp))
            BisonButton(text = "Weiter", onClick = { onSubmit(true) })
        } else {
            Spacer(Modifier.height(18.dp))
            Caption(text = "DEINE EINGABE")
            Spacer(Modifier.height(8.dp))
            Code(text = typed.text.ifBlank { " " }, tint = BisonColors.Wrong)
            Spacer(Modifier.height(14.dp))
            Caption(text = "LÖSUNG")
            Spacer(Modifier.height(8.dp))
            Code(text = filled.back, tint = BisonColors.TextPrimary)
            filled.logic?.let {
                Spacer(Modifier.height(14.dp))
                Caption(text = "LOGIK")
                Spacer(Modifier.height(8.dp))
                Answer(text = it, tint = BisonColors.TextSecondary)
            }

            Spacer(Modifier.height(20.dp))
            Caption(text = "WAS WAR ES?")
            Spacer(Modifier.height(10.dp))
            // the app compared two strings and they differed; only the reader knows whether that
            // was the answer being wrong or only being written differently
            BisonButton(text = "War richtig", onClick = { onSubmit(true) })
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Syntaxfehler", onClick = { onSubmit(false) }, filled = false)
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Logikfehler", onClick = { onSubmit(false) }, filled = false)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * A card with its numbers rolled and written in.
 *
 * Only a parametrised card goes through the substitution. Braces are ordinary characters in C -
 * `void square(long *v){ *v *= *v; }` is a question on one of these cards - and running that
 * through a placeholder replacement would take it apart.
 */
private fun fill(
    card: StudyCard,
    seed: Int,
): StudyCard {
    val spec = card.params ?: return card
    val values = Params.roll(spec, Random(seed))
    return card.copy(
        prompt = Params.fill(card.prompt, values),
        back = Params.fill(card.back, values),
        logic = card.logic?.let { Params.fill(it, values) },
        alternatives = card.alternatives.map { Params.fill(it, values) },
    )
}

/** The question, with any code on it set as code */
@Composable
private fun Prompt(text: String) {
    val lines = text.split("\n")
    Text(
        text = lines.first(),
        style = MaterialTheme.typography.titleLarge,
        color = BisonColors.TextPrimary,
    )
    // a trace card carries four lines of MATLAB under its question, and a proportional face
    // makes the indentation of those meaningless
    if (lines.size > 1) {
        Spacer(Modifier.height(14.dp))
        GivenCode(code = lines.drop(1).joinToString("\n"))
    }
}

/** An answer or a piece of reasoning: prose, so set as prose */
@Composable
private fun Answer(
    text: String,
    tint: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = tint,
    )
}

/** An answer that is code, which is what a syntax card's is */
@Composable
private fun Code(
    text: String,
    tint: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = tint,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/**
 * The field an answer is typed into.
 *
 * Autocorrect and automatic capitals turn `zeros` into `Zeros`, which is a different name in
 * MATLAB and a wrong answer here, so all three are off and the keyboard is asked for ASCII. It
 * grows past one line because a block matrix does not fit on one.
 */
@Composable
private fun Field(
    value: TextFieldValue,
    readOnly: Boolean,
    outline: Color,
    onValueChange: (TextFieldValue) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        textStyle =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = BisonColors.TextPrimary,
            ),
        cursorBrush = SolidColor(BisonColors.LearnedGreen),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .border(BorderStroke(1.dp, outline), RoundedCornerShape(BisonShape.Radius))
                .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

private enum class OptionState { Open, Right, Wrong, Quiet }

/** One of the three letters, which stays quiet until it has been picked */
@Composable
private fun OptionButton(
    letter: Char,
    state: OptionState,
    onClick: () -> Unit,
) {
    val tint =
        when (state) {
            OptionState.Open -> BisonColors.TextPrimary
            OptionState.Right -> BisonColors.Correct
            OptionState.Wrong -> BisonColors.Wrong
            OptionState.Quiet -> BisonColors.TextMuted
        }
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.titleLarge,
        color = tint,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .border(BorderStroke(1.dp, if (state == OptionState.Open) BisonColors.Border else tint), RoundedCornerShape(BisonShape.Radius))
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 18.dp),
    )
}

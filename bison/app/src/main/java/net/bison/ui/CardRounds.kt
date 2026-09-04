package net.bison.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import net.bison.model.Rating
import net.bison.model.StudyCard
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * What a round reports when it is done with a card.
 *
 * More than "right or wrong", because the exam marks in three grades and so does the reader: the
 * difference between a slip of syntax and a wrong idea is what the whole self-marking exists for,
 * and it has to reach the history intact.
 *
 * @param typed what was written, word for word, where anything was
 * @param rolled the numbers a parametrised card came up with, so the attempt can be read back
 */
data class Given(
    val rating: Rating,
    val typed: String? = null,
    val rolled: Map<String, String> = emptyMap(),
)

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
    onSubmit: (given: Given) -> Unit,
    reversed: Boolean = false,
) {
    var turned by remember(card, reversed) { mutableStateOf(0) }
    // read out once rather than reached for three times: the card is declared in another module
    // now, and the compiler will not carry a null check across a property it cannot see into
    val logic = card.logic
    // turned round, the reasoning is the question and there is only the answer left to show
    val backwards = reversed && logic != null
    val stages = if (logic == null || backwards) 1 else 2

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Caption(text = if (backwards) "$round · umgekehrt" else round)
        Spacer(Modifier.height(14.dp))
        Prompt(text = if (backwards) logic.orEmpty() else card.prompt)

        if (turned >= 1 && logic != null && !backwards) {
            Spacer(Modifier.height(20.dp))
            Caption(text = "LOGIK")
            Spacer(Modifier.height(8.dp))
            Answer(text = logic, tint = BisonColors.Almost)
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
            BisonButton(text = "Gewusst", onClick = { onSubmit(Given(Rating.Right)) })
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Nicht gewusst", onClick = { onSubmit(Given(Rating.Logic)) }, filled = false)
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
    onSubmit: (given: Given) -> Unit,
    reversed: Boolean = false,
) {
    val shown = remember(card, seed) { fill(card, seed) }
    val filled = shown.card
    val rolled = shown.rolled
    var typed by remember(shown, reversed) { mutableStateOf(TextFieldValue("")) }
    var verdict by remember(shown, reversed) { mutableStateOf<Boolean?>(null) }
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
            BisonButton(text = "Weiter", onClick = { onSubmit(Given(Rating.Right, typed.text)) })
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
            BisonButton(text = "War richtig", onClick = { onSubmit(Given(Rating.Right, typed.text, rolled)) })
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Syntaxfehler", onClick = { onSubmit(Given(Rating.Syntax, typed.text, rolled)) }, filled = false)
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Logikfehler", onClick = { onSubmit(Given(Rating.Logic, typed.text, rolled)) }, filled = false)
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
): Filled {
    val spec = card.params ?: return Filled(card, emptyMap())
    val values = Params.roll(spec, Random(seed))
    return Filled(
        card =
            card.copy(
                prompt = Params.fill(card.prompt, values),
                back = Params.fill(card.back, values),
                logic = card.logic?.let { Params.fill(it, values) },
                alternatives = card.alternatives.map { Params.fill(it, values) },
            ),
        // kept as text: what the history wants is what stood on the card, not how it was worked
        // out, and a value that was a string of bits was never a number to begin with
        rolled = values.mapValues { it.value.text },
    )
}

/** A card as it was shown, and the numbers it was shown with */
private data class Filled(
    val card: StudyCard,
    val rolled: Map<String, String>,
)

/** The question, with any code on it set as code */
@Composable
private fun Prompt(text: String) {
    val lines = text.split("\n")
    CardText(
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

/** An answer or a piece of reasoning: prose, with whatever code stands inside it set as code */
@Composable
private fun Answer(
    text: String,
    tint: Color,
) {
    CardText(text = text, style = MaterialTheme.typography.bodyLarge, color = tint)
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
    // The keyboard is up before the card is: on a typing card there is nothing else to do, and
    // making the reader tap the field first is a tap per card for no reason at all. It is asked
    // for once, when the field appears, so it does not fight a reader who has put it away.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

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
                .focusRequester(focus)
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .border(BorderStroke(1.dp, outline), RoundedCornerShape(BisonShape.Radius))
                .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

/**
 * A card solved on paper, against a clock.
 *
 * The exam gives a programming exercise so many minutes and the thing being trained is finishing
 * inside them, so this card is timed rather than compared: the clock starts the moment it is on
 * screen and stops when the answer is asked for. What is kept is how long it took.
 *
 * The clock reads the wall clock rather than counting its own ticks. Counting would stop while
 * the phone is locked or the app is put away - and a card whose whole purpose is measuring how
 * long something took must not measure only the part where the screen was on.
 *
 * The previous times stand next to the target, because a number on its own says nothing: five
 * minutes is either progress or a disaster depending on what the last five were.
 */
@Composable
fun TimedRound(
    card: StudyCard,
    round: String,
    history: List<Long>,
    best: Long?,
    onSubmit: (given: Given) -> Unit,
) {
    val startedAt = remember(card) { System.currentTimeMillis() }
    var stoppedAt by remember(card) { mutableStateOf<Long?>(null) }
    var now by remember(card) { mutableStateOf(startedAt) }

    // ticks only to redraw; the value it shows always comes from the wall clock, so a minute
    // spent with the screen off is a minute on the card
    LaunchedEffect(card) {
        while (stoppedAt == null) {
            now = System.currentTimeMillis()
            delay(TICK)
        }
    }

    val elapsed = ((stoppedAt ?: now) - startedAt) / 1000
    val target = card.target

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caption(text = round, modifier = Modifier.weight(1f))
            Text(
                text = asClock(elapsed),
                style = MaterialTheme.typography.titleLarge,
                color =
                    when {
                        target == null -> BisonColors.TextPrimary
                        elapsed > target -> BisonColors.Wrong
                        else -> BisonColors.Correct
                    },
            )
        }
        Spacer(Modifier.height(14.dp))
        Prompt(text = card.prompt)

        Spacer(Modifier.height(18.dp))
        Caption(text = pace(target, history, best))

        if (stoppedAt == null) {
            Spacer(Modifier.height(22.dp))
            BisonButton(text = "Lösung zeigen", onClick = { stoppedAt = System.currentTimeMillis() })
        } else {
            card.logic?.let {
                Spacer(Modifier.height(20.dp))
                Caption(text = "LOGIK")
                Spacer(Modifier.height(8.dp))
                Answer(text = it, tint = BisonColors.Almost)
            }
            Spacer(Modifier.height(20.dp))
            Caption(text = "LÖSUNG")
            Spacer(Modifier.height(8.dp))
            Code(text = card.back, tint = BisonColors.TextPrimary)

            Spacer(Modifier.height(22.dp))
            Caption(text = "WAS WAR ES?")
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Richtig", onClick = { onSubmit(Given(Rating.Right)) })
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Syntaxfehler", onClick = { onSubmit(Given(Rating.Syntax)) }, filled = false)
            Spacer(Modifier.height(10.dp))
            BisonButton(text = "Logikfehler", onClick = { onSubmit(Given(Rating.Logic)) }, filled = false)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** The target, the last five and the best, in one line, or as much of it as exists */
private fun pace(
    target: Int?,
    history: List<Long>,
    best: Long?,
): String {
    val parts = mutableListOf<String>()
    target?.let { parts += "Ziel ${asClock(it.toLong())}" }
    best?.let { parts += "Beste ${asClock(it)}" }
    if (history.isNotEmpty()) parts += "Letzte: " + history.take(5).joinToString(", ") { asClock(it) }
    return if (parts.isEmpty()) "Noch nie bearbeitet" else parts.joinToString(" · ")
}

/** "2:05", which is how anybody reads a time that long */
private fun asClock(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** How often the clock redraws. Four times a second reads as running without costing anything. */
private const val TICK = 250L

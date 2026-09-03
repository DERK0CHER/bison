package net.bison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import net.bison.model.CodeTask
import net.bison.model.Task
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonShape

/**
 * The code that is already on the front of a card, set as code.
 *
 * A C signature in the proportional title face is barely readable: the stars, the braces and the
 * indentation are the parts that carry the meaning, and they are exactly what a text face
 * flattens. So the front's code gets what the sort rows and the editor already get - one line per
 * line, monospaced, scrolling sideways rather than wrapping, because a wrapped line of code reads
 * as two statements.
 *
 * The gap marker is the one line here that is not code but an instruction, so it is coloured as
 * one.
 */
@Composable
fun GivenCode(
    code: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BisonShape.Radius))
                .background(BisonColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (line in CodeTask.lines(code)) {
            Text(
                text = line.ifBlank { " " },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (line.trim() == CodeTask.GAP) BisonColors.Almost else BisonColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * A card's front: the task in prose, the code underneath it as code, the picture under that.
 *
 * Any of the three may be missing - a card can be all prose, all code or nothing but a diagram -
 * and nothing is drawn for the ones that are not there.
 */
@Composable
fun TaskFront(
    task: Task,
    modifier: Modifier = Modifier,
    promptStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Column(modifier = modifier) {
        if (task.prompt.isNotBlank()) {
            Text(
                text = task.prompt,
                style = promptStyle,
                color = BisonColors.TextPrimary,
            )
        }
        task.given?.let { code ->
            if (task.prompt.isNotBlank()) Spacer(Modifier.height(14.dp))
            GivenCode(code)
        }
        task.image?.let { picture ->
            if (task.prompt.isNotBlank() || task.given != null) Spacer(Modifier.height(14.dp))
            CardPicture(name = picture)
        }
    }
}

/** Which characters the bar offers, because a hexadecimal answer wants none of the braces */
enum class SymbolSet {
    /** Braces, brackets and the arrows, for writing C */
    Code,

    /** The six digits a number keyboard does not have */
    Hex,

    /** What the written card set asks for: MATLAB and C punctuation, nothing else */
    Study,
}

/** The characters a German phone keyboard hides three menus deep, on one scrolling row */
@Composable
fun SymbolBar(
    onInsert: (String) -> Unit,
    set: SymbolSet = SymbolSet.Code,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        val offered =
            when (set) {
                SymbolSet.Hex -> HEX
                SymbolSet.Study -> STUDY
                else -> SYMBOLS
            }
        for (symbol in offered) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BisonColors.SurfaceRaised)
                        .clickable { onInsert(symbol.inserts) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = symbol.shows,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = BisonColors.TextPrimary,
                )
            }
        }
    }
}

/** Inserts at the cursor, replacing whatever was selected */
fun TextFieldValue.insert(text: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val updated = this.text.replaceRange(start, end, text)
    return TextFieldValue(updated, TextRange(start + text.length))
}

private data class Symbol(
    val shows: String,
    val inserts: String = shows,
)

private val SYMBOLS =
    listOf(
        Symbol("⇥", "    "),
        Symbol("{"),
        Symbol("}"),
        Symbol("("),
        Symbol(")"),
        Symbol("["),
        Symbol("]"),
        Symbol(";"),
        Symbol("*"),
        Symbol("&"),
        Symbol("->"),
        Symbol("=="),
        Symbol("!="),
        Symbol("<"),
        Symbol(">"),
        Symbol("="),
        Symbol("\""),
        Symbol("%"),
    )

/**
 * What a hexadecimal answer needs and a keyboard does not offer together.
 *
 * The letters are on the keyboard, but not beside the digits: answering `3f` means switching
 * layouts twice for two characters. The `0x` is there because it is habit, not because the
 * answer needs it - it is thrown away before the comparison either way.
 */
private val HEX =
    listOf(
        Symbol("a"),
        Symbol("b"),
        Symbol("c"),
        Symbol("d"),
        Symbol("e"),
        Symbol("f"),
        Symbol("0x"),
    )

/**
 * Exactly what the written set asks for.
 *
 * MATLAB and C punctuation and nothing else: these are the characters an answer needs and a
 * German phone keyboard hides two layouts deep, and a bar that also offered the ones it does not
 * need would make the ones it does harder to hit.
 */
private val STUDY =
    listOf(
        Symbol("["),
        Symbol("]"),
        Symbol("("),
        Symbol(")"),
        Symbol("'"),
        Symbol(";"),
        Symbol(":"),
        Symbol(","),
        Symbol("."),
        Symbol("^"),
        Symbol("="),
        Symbol("*"),
        Symbol("&"),
        Symbol("{"),
        Symbol("}"),
        Symbol("->"),
        Symbol("<"),
        Symbol(">"),
    )

package net.bison.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import net.bison.audio.Feedback
import net.bison.domain.StudySession
import net.bison.model.Attempt
import net.bison.model.Card
import net.bison.model.CardMode
import net.bison.model.CodeTask
import net.bison.model.GeneratedTask
import net.bison.model.Question
import net.bison.model.Rating
import net.bison.model.SketchTask
import net.bison.model.StudyCard
import net.bison.ui.theme.BisonColors
import net.bison.ui.theme.BisonMotion
import net.bison.ui.theme.BisonShape

/**
 * The study loop: a question, one box per answer, and nothing else on screen.
 *
 * The question and its answers sit together in the middle of the screen, with the leftover
 * space split above and below them rather than opening a hole at one end. Rounds slide in from
 * the right and out to the left, so answering visibly moves the set along.
 *
 * Picking a box reveals the outcome at once and the screen then waits. Nothing advances on a
 * timer, so how long the answer stays up is the reader's decision.
 *
 * @param key identifies the set being studied; a new one starts a new session
 * @param onProgress called after every answer, so nothing is lost if the app never gets to leave
 *   this screen properly
 */
@Composable
fun StudyScreen(
    key: String,
    cards: List<Card>,
    soundOn: Boolean,
    onFinished: (List<Card>) -> Unit,
    onLeave: (List<Card>) -> Unit,
    onProgress: (List<Card>) -> Unit = {},
    reversed: Boolean = false,
) {
    val session = remember(key) { StudySession(cards) }
    var picked by remember { mutableStateOf<Int?>(null) }
    // bumped after each answer so the screen recomposes off the session's new state
    var round by remember { mutableIntStateOf(0) }

    // A fresh order every time the question comes round: with a fixed order the answer that
    // gets remembered is "the second one from the top" rather than the answer itself.
    // Everything one round shows, captured together: the outgoing and incoming rounds are on
    // screen at the same time while they slide past each other, so neither may read the session
    // as it is now.
    val step =
        remember(round) {
            val card = session.current()
            val line = roundLine(session.roundNumber, session.target, session.remainingThisRound)
            Step(
                index = round,
                card = card,
                line = line,
                view =
                    (card?.task as? Question)?.let { question ->
                        val order = question.answers.indices.shuffled()
                        RoundView(
                            prompt = question.prompt,
                            given = question.given,
                            image = question.image,
                            logic = question.logic,
                            reason = question.reason,
                            answers = order.map { question.answers[it] },
                            correctPosition = order.indexOf(question.correctIndex),
                        )
                    },
            )
        }
    val card = step.card
    val chosen = picked

    // Flagging must not bump the round: that would redraw the answers in a new order under a
    // finger mid-question, and any already picked position would then point at the wrong one.
    // The session is told straight away; this only keeps the button in step until the next round.
    var flagged by remember(round) { mutableStateOf<Boolean?>(null) }

    // A single soft wash of colour over the whole screen, once, when an answer is picked.
    // Which answer was right is already written on the boxes; this only has to say "that
    // landed" from the corner of the eye, so it is faint and gone again quickly.
    val flash = remember { Animatable(0f) }
    val flashColour =
        if (chosen != null && chosen == step.view?.correctPosition) {
            BisonColors.LearnedGreen
        } else {
            BisonColors.Wrong
        }
    LaunchedEffect(round, chosen) {
        flash.snapTo(0f)
        if (chosen != null) {
            flash.animateTo(1f, tween(FLASH_IN))
            flash.animateTo(0f, tween(FLASH_OUT))
        }
    }

    val feedback = remember { Feedback() }
    DisposableEffect(Unit) { onDispose { feedback.release() } }

    // the session lives here, so leaving has to be handled here too: anywhere else would write
    // back the cards as they were before any of this was answered
    BackHandler { onLeave(session.snapshot()) }

    // when the question went up, so the time spent on it can be added to it. Kept per round, so
    // a question left on screen while the phone is put down is capped by the session rather than
    // by this.
    val shownAt = remember(round) { System.currentTimeMillis() }

    /**
     * Writes one answer down and moves on.
     *
     * The attempt is kept whole - what it was graded, what was typed, which numbers the card
     * came up with - and it is persisted here, on the spot. A session that only wrote its
     * history back on the way out would lose an evening to a locked screen.
     */
    fun recordGiven(given: Given) {
        val seconds = (System.currentTimeMillis() - shownAt) / 1000
        session.answer(
            correct = given.rating.correct,
            seconds = seconds,
            attempt =
                Attempt(
                    at = System.currentTimeMillis(),
                    rating = given.rating,
                    seconds = seconds,
                    typed = given.typed,
                    rolled = given.rolled,
                ),
        )
        picked = null
        round++
        onProgress(session.snapshot())
    }

    /** For the modes that only know right from wrong */
    fun record(correct: Boolean) = recordGiven(Given(if (correct) Rating.Right else Rating.Wrong))

    fun advance() {
        val position = picked ?: return
        val current = step.view ?: return
        // written after every answer, not only on the way out: the app can be swiped away or
        // the process reclaimed at any moment, and a whole session's work would go with it
        record(correct = position == current.correctPosition)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BisonColors.Background)
                // painted over the content rather than laid on top of it, so it can never come
                // between a finger and the screen underneath
                .drawWithContent {
                    drawContent()
                    if (flash.value > 0f) {
                        drawRect(flashColour.copy(alpha = flash.value * FLASH_PEAK))
                    }
                }
                // once an answer is showing the whole screen moves on: the finger is already in
                // the middle of the screen, so making it travel to a bar at the bottom is a
                // second act of aiming for no reason
                .clickable(
                    enabled = chosen != null,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { advance() },
                ).systemBarsPadding()
                .padding(horizontal = BisonShape.Gutter),
    ) {
        TopBar(
            progress = session.progress,
            hard = flagged ?: (card?.hard == true),
            canUndo = session.canUndo && chosen == null,
            onHard = {
                session.flag(it)
                flagged = it
                onProgress(session.snapshot())
            },
            onUndo = {
                if (session.undo()) {
                    picked = null
                    round++
                    onProgress(session.snapshot())
                }
            },
            onLeave = { onLeave(session.snapshot()) },
        )

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (
                    slideInHorizontally(tween(BisonMotion.Settle)) { it / 3 } +
                        fadeIn(tween(BisonMotion.Settle))
                ) togetherWith
                    (
                        slideOutHorizontally(tween(BisonMotion.Settle)) { -it / 3 } +
                            fadeOut(tween(BisonMotion.Quick))
                    )
            },
            label = "round",
        ) { target ->
            val shown = target.card
            val choice = target.view
            when {
                shown == null ->
                    FinishedPanel(total = session.total, onDone = { onFinished(session.snapshot()) })

                choice != null ->
                    Round(
                        view = choice,
                        line = target.line,
                        picked = if (target.index == round) chosen else null,
                        onPick = { position ->
                            if (picked == null) {
                                picked = position
                                if (soundOn) feedback.play(correct = position == choice.correctPosition)
                            }
                        },
                    )

                shown.mode == CardMode.Timed ->
                    TimedRound(
                        card = shown.task as StudyCard,
                        round = target.line,
                        history = shown.times,
                        best = shown.best,
                        onSubmit = { given ->
                            if (soundOn) feedback.play(correct = given.rating.correct)
                            recordGiven(given)
                        },
                    )

                shown.mode == CardMode.Flip ->
                    FlipRound(
                        card = shown.task as StudyCard,
                        round = target.line,
                        reversed = reversed,
                        onSubmit = { given ->
                            if (soundOn) feedback.play(correct = given.rating.correct)
                            recordGiven(given)
                        },
                    )

                shown.mode == CardMode.Answer ->
                    AnswerRound(
                        card = shown.task as StudyCard,
                        round = target.line,
                        reversed = reversed,
                        // the round's own number, so a parametrised card stands still while it
                        // is answered and asks something else when it comes back
                        seed = target.index,
                        onSubmit = { given ->
                            if (soundOn) feedback.play(correct = given.rating.correct)
                            recordGiven(given)
                        },
                    )

                shown.mode == CardMode.Reveal ->
                    RevealRound(
                        task = shown.task as SketchTask,
                        round = target.line,
                        onSubmit = { correct ->
                            if (soundOn) feedback.play(correct = correct)
                            record(correct)
                        },
                    )

                shown.mode == CardMode.Generate ->
                    GeneratedRound(
                        task = shown.task as GeneratedTask,
                        round = target.line,
                        // the round's own number, so the sum stands still while it is answered
                        // and is a different one when the card comes back
                        seed = target.index,
                        onSubmit = { correct ->
                            if (soundOn) feedback.play(correct = correct)
                            record(correct)
                        },
                    )

                shown.mode == CardMode.Type ->
                    TypeRound(
                        task = shown.task as CodeTask,
                        round = target.line,
                        onSubmit = { correct ->
                            if (soundOn) feedback.play(correct = correct)
                            record(correct)
                        },
                    )

                shown.mode == CardMode.Sort ->
                    SortRound(
                        task = shown.task as CodeTask,
                        round = target.line,
                        onSubmit = { correct ->
                            if (soundOn) feedback.play(correct = correct)
                            // two clean sorts and the card is asked to be written out instead
                            session.sorted(correct)
                            record(correct)
                        },
                    )

                else ->
                    CodeRound(
                        task = shown.task as CodeTask,
                        round = target.line,
                        onSubmit = { correct ->
                            if (soundOn) feedback.play(correct = correct)
                            record(correct)
                        },
                    )
            }
        }
    }
}

/** One round as it was when it began, so a round leaving the screen keeps showing itself */
private data class Step(
    val index: Int,
    val card: Card?,
    val line: String,
    val view: RoundView?,
)

/** What a multiple choice round shows: the answers already in the order they are drawn */
private data class RoundView(
    val prompt: String,
    val given: String?,
    val image: String?,
    val logic: String?,
    val reason: String?,
    val answers: List<String>,
    val correctPosition: Int,
)

/**
 * What the caption over the question says.
 *
 * The round matters more than the count: knowing this pass only wants four right answers is what
 * makes a set of two hundred feel finishable.
 */
private fun roundLine(
    round: Int,
    target: Int,
    remaining: Int,
): String {
    val left = if (remaining == 1) "letzte Frage" else "noch $remaining"
    return "Runde $round · $target" + "× richtig · $left"
}

/** One question with its answers, as one block in the middle of the screen */
@Composable
private fun Round(
    view: RoundView,
    line: String,
    picked: Int?,
    onPick: (Int) -> Unit,
) {
    // Everything scrolls together. Scrolling only the question left a long set of answers
    // clipped at the bottom with no way to reach them - the air between the two blocks is worth
    // having, but not at the price of an answer you cannot tap.
    //
    // The column is at least as tall as the screen, so a short question and its answers sit
    // together in the middle with the leftover split above and below. Once they are taller than
    // that, the column simply grows past the screen and the whole thing scrolls.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                Caption(text = line)
                Spacer(Modifier.height(14.dp))
                if (view.prompt.isNotBlank()) {
                    Text(
                        text = view.prompt,
                        style = MaterialTheme.typography.displaySmall,
                        color = BisonColors.TextPrimary,
                    )
                }
                // a trace question is a program and three outputs: the program is code and has
                // to be set as code, or the answer cannot be worked out from it
                view.given?.let { code ->
                    if (view.prompt.isNotBlank()) Spacer(Modifier.height(14.dp))
                    GivenCode(code)
                }
                view.image?.let { picture ->
                    if (view.prompt.isNotBlank() || view.given != null) Spacer(Modifier.height(14.dp))
                    CardPicture(name = picture)
                }
            }

            Column {
                Spacer(Modifier.height(24.dp))
                view.answers.forEachIndexed { position, answer ->
                    AnswerCard(
                        text = answer,
                        state = answerState(position, picked, view.correctPosition),
                        onClick = { onPick(position) },
                    )
                    Spacer(Modifier.height(BisonShape.Gap))
                }

                // Once a box has been picked, why. Knowing that the second one was right
                // teaches nothing about the next question; the pattern behind it does, and it
                // can only be shown afterwards without giving the answer away.
                if (picked != null) {
                    view.reason?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BisonColors.TextSecondary,
                        )
                    }
                    view.logic?.let {
                        Spacer(Modifier.height(12.dp))
                        Caption(text = "LOGIK")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BisonColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

/** How the wash fades in, then away again. Out is slower, so it reads as a wash, not a blink. */
private const val FLASH_IN = 110

private const val FLASH_OUT = 420

/** As strong as the wash ever gets. Faint on purpose: it is a nudge, not an announcement. */
private const val FLASH_PEAK = 0.16f

/**
 * One row across the top: a way out, the bar, and the flag.
 *
 * Everything lives on one line so the questions start as high as they can. The bar takes what
 * the controls leave, which is why those are single glyphs rather than words.
 */
@Composable
private fun TopBar(
    progress: Float,
    hard: Boolean,
    canUndo: Boolean,
    onHard: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphAction(glyph = "\u00d7", description = "Schluss", onClick = onLeave)
        if (canUndo) {
            Spacer(Modifier.width(8.dp))
            GlyphAction(glyph = "\u2190", description = "Zurück", onClick = onUndo)
        }
        ProgressBar(
            fraction = progress,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            height = 10.dp,
        )
        HardFlag(hard = hard, onChange = onHard)
    }
}

/**
 * Marks the question on screen as one that keeps going wrong.
 *
 * A flagged question comes back twice as often as its box would otherwise say, which is the
 * whole point of being able to say so: the schedule is an average, and the one question that
 * refuses to stick should not have to wait its turn with the rest.
 */
@Composable
private fun HardFlag(
    hard: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (hard) BisonColors.Almost else BisonColors.Surface,
        animationSpec = tween(BisonMotion.Quick),
        label = "flagFill",
    )
    val foreground by animateColorAsState(
        targetValue = if (hard) BisonColors.Background else BisonColors.TextMuted,
        animationSpec = tween(BisonMotion.Quick),
        label = "flagText",
    )
    Text(
        text = "schwer",
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier =
            Modifier
                .clip(RoundedCornerShape(BisonShape.Pill))
                .background(background)
                .clickable { onChange(!hard) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** A single character on a round target, big enough to hit without looking */
@Composable
private fun GlyphAction(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BisonColors.Surface)
                .clickable(onClickLabel = description, onClick = onClick),
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = BisonColors.TextSecondary,
        )
    }
}

private enum class AnswerState { Untouched, Correct, Wrong, Dimmed }

private fun answerState(
    index: Int,
    picked: Int?,
    correctIndex: Int,
): AnswerState =
    when {
        picked == null -> AnswerState.Untouched
        index == correctIndex -> AnswerState.Correct
        index == picked -> AnswerState.Wrong
        else -> AnswerState.Dimmed
    }

/**
 * One answer, as a rounded box carrying its own text.
 *
 * A box rather than a stadium: full pill ends eat into the first and last line of a two-line
 * answer, and four tall pills in a stack read as four separate blobs instead of one list.
 *
 * There is no letter on it. The answer is written right there, so a badge saying "C" beside it
 * would only name something the reader is already looking at.
 */
@Composable
private fun AnswerCard(
    text: String,
    state: AnswerState,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && state == AnswerState.Untouched) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "answerPress",
    )
    val fill by animateColorAsState(
        targetValue =
            when (state) {
                AnswerState.Correct -> BisonColors.CorrectSurface
                AnswerState.Wrong -> BisonColors.WrongSurface
                else -> BisonColors.Surface
            },
        animationSpec = tween(BisonMotion.Quick),
        label = "answerFill",
    )
    val stroke by animateColorAsState(
        targetValue =
            when (state) {
                AnswerState.Correct -> BisonColors.Correct
                AnswerState.Wrong -> BisonColors.Wrong
                AnswerState.Dimmed -> BisonColors.Surface
                else -> BisonColors.Border
            },
        animationSpec = tween(BisonMotion.Quick),
        label = "answerStroke",
    )
    val textColor by animateColorAsState(
        targetValue =
            when (state) {
                AnswerState.Dimmed -> BisonColors.TextMuted
                AnswerState.Correct -> BisonColors.Correct
                AnswerState.Wrong -> BisonColors.Wrong
                else -> BisonColors.TextPrimary
            },
        animationSpec = tween(BisonMotion.Quick),
        label = "answerText",
    )

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(RoundedCornerShape(BisonShape.Radius))
                .background(fill)
                .border(BorderStroke(1.dp, stroke), RoundedCornerShape(BisonShape.Radius))
                // The modifier is dropped once answered rather than disabled. A disabled
                // clickable still takes part in hit testing and swallows the touch, so tapping
                // an answer after answering did nothing at all and only the gaps between the
                // boxes moved to the next question.
                .then(
                    if (state == AnswerState.Untouched) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
        )
    }
}

/** Shown once every question has reached the last box */
@Composable
private fun FinishedPanel(
    total: Int,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "durch",
            style = MaterialTheme.typography.displayLarge,
            color = BisonColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Alle $total Fragen sitzen — jede ${Card.LEARNED_BOX}-mal hintereinander richtig.",
            style = MaterialTheme.typography.bodyLarge,
            color = BisonColors.TextSecondary,
        )
        Spacer(Modifier.height(36.dp))
        BisonButton(text = "Fertig", onClick = onDone)
        Spacer(Modifier.height(20.dp))
    }
}

package net.bison

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bison.model.Card
import net.bison.model.Question
import net.bison.ui.StudyScreen
import net.bison.ui.theme.BisonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the study screen the way a finger does, to check the shuffle against the grading.
 *
 * The pills are drawn in a fresh random order every round, so what the screen knows is a
 * position and what the question knows is an index. Every answer here is picked by its own
 * text, never by where it sits: if the two ever came apart, the right answer would start
 * scoring as wrong and the deck would never finish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StudyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun deck() =
        listOf(
            Card(
                Question(
                    prompt = PROMPT,
                    answers = listOf("blau", "grün", "gelb", "violett"),
                    correctIndex = 0,
                ),
            ),
        )

    private fun show() {
        composeRule.setContent {
            BisonTheme {
                StudyScreen(key = "d", cards = deck(), soundOn = false, onFinished = {}, onLeave = {})
            }
        }
    }

    /** Tapping anywhere moves on, and the question itself is the one target outside every card */
    private fun advance() = composeRule.onNodeWithText(PROMPT).performClick()

    @Test
    fun `the right answer counts as right wherever the shuffle puts it`() {
        show()

        repeat(Card.LEARNED_BOX) {
            composeRule.onNodeWithText("blau").performClick()
            advance()
        }

        composeRule.onNodeWithText("durch").assertIsDisplayed()
    }

    @Test
    fun `a wrong answer never finishes the question, wherever the shuffle puts it`() {
        show()

        // more rounds than there are answers, so a mapping that only happens to line up once
        // does not get away with it
        repeat(12) {
            composeRule.onNodeWithText("violett").performClick()
            advance()
        }

        composeRule.onNodeWithText(PROMPT).assertIsDisplayed()
    }

    @Test
    fun `eight wrong answers do not finish the question either`() {
        show()

        repeat(Card.LEARNED_BOX) {
            composeRule.onNodeWithText("grün").performClick()
            advance()
        }

        composeRule.onNodeWithText(PROMPT).assertIsDisplayed()
    }

    @Test
    fun `every answer is reported, not only the way out`() {
        // the app can be swiped away or the process reclaimed mid-session, and before this the
        // progress was written only when the screen was left properly
        val saved = mutableListOf<List<Card>>()
        composeRule.setContent {
            BisonTheme {
                StudyScreen(
                    key = "d",
                    cards = deck(),
                    soundOn = false,
                    onFinished = {},
                    onLeave = {},
                    onProgress = { saved += it },
                )
            }
        }

        repeat(3) {
            composeRule.onNodeWithText("blau").performClick()
            advance()
        }

        assertEquals(3, saved.size)
        assertEquals(listOf(1, 2, 3), saved.map { it.single().box })
    }

    private companion object {
        const val PROMPT = "Welche Farbe hat der Himmel?"
    }
}

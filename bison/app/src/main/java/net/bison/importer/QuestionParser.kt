package net.bison.importer

import net.bison.model.Question
import net.bison.model.Task
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads questions out of pasted text.
 *
 * JSON is the format asked for, because a language model can be told to produce it exactly and
 * there is then nothing to guess: the right answer is named outright rather than inferred from a
 * letter. Prose is still accepted as a fallback for text that was written by hand or by a model
 * that ignored the instruction.
 *
 * A block that cannot be read is skipped rather than failing the whole paste, and counted in
 * [ImportResult.skipped] so the report is honest.
 */
object QuestionParser {
    data class ImportResult(
        val questions: List<Task>,
        val skipped: Int,
    )

    /** The study screen shows one pill per answer, and four is where that stops being readable */
    const val MAX_ANSWERS = 4

    fun parse(text: String): ImportResult = parseJson(text) ?: parseProse(text)

    // region JSON

    /**
     * Reads `[{"question": "...", "answers": ["...", "..."], "correct": 0}]`.
     *
     * `correct` may be the index, counted from zero, or the text of the right answer: a model
     * asked for one sometimes gives the other, and both are unambiguous.
     *
     * @return null if the text is not JSON at all, so prose can be tried instead
     */
    private fun parseJson(text: String): ImportResult? {
        val array = readArray(text) ?: return null
        val questions = mutableListOf<Question>()
        var skipped = 0
        for (i in 0 until array.length()) {
            val question = array.optJSONObject(i)?.let(::readQuestion)
            if (question != null) questions += question else skipped++
        }
        return ImportResult(questions, skipped)
    }

    /** Accepts a bare array, an object wrapping one, and text with the JSON buried in it */
    private fun readArray(text: String): JSONArray? {
        val trimmed = stripCodeFence(text).trim()
        if (trimmed.isEmpty()) return null
        runCatching { return JSONArray(trimmed) }
        runCatching {
            val obj = JSONObject(trimmed)
            for (key in listOf("questions", "fragen", "items", "data")) {
                obj.optJSONArray(key)?.let { return it }
            }
        }
        // a model often wraps the array in a sentence, so fall back to the outermost brackets
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start >= 0 && end > start) {
            runCatching { return JSONArray(trimmed.substring(start, end + 1)) }
        }
        return null
    }

    /** Removes a ```json fence, which models add whether or not they were asked to */
    private fun stripCodeFence(text: String): String {
        val fence = Regex("""```[a-zA-Z]*\s*\n(.*?)```""", RegexOption.DOT_MATCHES_ALL).find(text)
        return fence?.groupValues?.get(1) ?: text
    }

    private fun readQuestion(json: JSONObject): Question? {
        val prompt = json.firstString("question", "frage", "prompt", "q") ?: return null
        val answersJson =
            json.optJSONArray("answers")
                ?: json.optJSONArray("antworten")
                ?: json.optJSONArray("options")
                ?: json.optJSONArray("choices")
                ?: return null

        val answers = mutableListOf<String>()
        for (i in 0 until answersJson.length()) {
            val answer = answersJson.optString(i).trim()
            if (answer.isEmpty()) return null
            answers += answer
        }
        if (answers.size < 2 || answers.size > MAX_ANSWERS) return null

        val correct = resolveCorrect(json, answers) ?: return null
        return Question(
            prompt = prompt,
            answers = answers,
            correctIndex = correct,
            topic = json.firstString("topic", "thema", "kategorie", "category", "subtopic"),
        )
    }

    /** `correct` as an index counted from zero, or as the text of the right answer */
    private fun resolveCorrect(
        json: JSONObject,
        answers: List<String>,
    ): Int? {
        for (key in listOf("correct", "richtig", "answer", "solution", "correctIndex")) {
            if (!json.has(key)) continue
            val value = json.get(key)
            if (value is Int) return value.takeIf { it in answers.indices }
            val asText = value.toString().trim()
            asText.toIntOrNull()?.let { index -> return index.takeIf { it in answers.indices } }
            val match = answers.indexOfFirst { it.equals(asText, ignoreCase = true) }
            if (match >= 0) return match
        }
        return null
    }

    private fun JSONObject.firstString(vararg keys: String): String? {
        for (key in keys) {
            val value = optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    // endregion

    // region prose fallback

    /** `A) text`, `1. text`, `- b: text`, `(C) text` */
    private val ANSWER_LINE = Regex("""^[-*•]?\s*\(?\s*([A-Ja-j1-9])\s*[).:\-\]]\s*(.+)$""")

    private val SOLUTION_LINE =
        Regex(
            """^\s*(?:lösung|loesung|antwort|richtig(?:e antwort)?|solution|answer|correct)\s*[:\-]\s*(.+)$""",
            RegexOption.IGNORE_CASE,
        )

    private val QUESTION_PREFIX =
        Regex("""^\s*(?:frage|question|q)\s*\d*\s*[:.\-]\s*""", RegexOption.IGNORE_CASE)

    private val NUMBERED_QUESTION = Regex("""^\s*\d+\s*[).:]\s+(.+)$""")

    private val RULE = Regex("""^\s*([-=_*])\1{2,}\s*$""")

    private val WHITESPACE = Regex("""\s+""")

    private fun parseProse(text: String): ImportResult {
        val questions = mutableListOf<Question>()
        var skipped = 0
        for (block in splitIntoBlocks(text)) {
            val question = parseBlock(block)
            if (question != null) questions += question else skipped++
        }
        return ImportResult(questions, skipped)
    }

    private fun splitIntoBlocks(text: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            val line = raw.trim()
            if (line.isEmpty() || RULE.matches(line)) {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                }
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) blocks += current
        return blocks
    }

    private fun parseBlock(lines: List<String>): Question? {
        val prompt = StringBuilder()
        val answers = mutableListOf<Pair<Int, String>>()
        var solution: String? = null

        for (line in lines) {
            val solutionMatch = SOLUTION_LINE.find(line)
            if (solutionMatch != null) {
                if (solution == null) solution = solutionMatch.groupValues[1].trim()
                continue
            }

            val answer = ANSWER_LINE.find(line)
            // an answer line only counts once the question has been read, otherwise a numbered
            // question such as "1. Was gilt hier?" would be taken for the first answer
            if (answer != null && prompt.isNotEmpty()) {
                val index = labelToIndex(answer.groupValues[1][0])
                if (index != null) {
                    answers += index to answer.groupValues[2].trim()
                    continue
                }
            }
            if (answers.isEmpty()) {
                val cleaned =
                    NUMBERED_QUESTION.find(line)?.groupValues?.get(1)
                        ?: QUESTION_PREFIX.replace(line, "")
                if (prompt.isNotEmpty()) prompt.append(' ')
                prompt.append(cleaned.trim())
            }
        }

        val texts = toAnswers(answers) ?: return null
        val text = prompt.toString().trim()
        if (text.isEmpty()) return null
        val correctIndex = resolveProseSolution(solution, texts) ?: return null
        return Question(prompt = text, answers = texts, correctIndex = correctIndex)
    }

    /** Requires 2..4 answers, running in order from the first */
    private fun toAnswers(answers: List<Pair<Int, String>>): List<String>? {
        if (answers.size < 2 || answers.size > MAX_ANSWERS) return null
        if (answers.withIndex().any { (position, answer) -> answer.first != position }) return null
        if (answers.any { it.second.isBlank() }) return null
        return answers.map { it.second }
    }

    private fun resolveProseSolution(
        solution: String?,
        answers: List<String>,
    ): Int? {
        val value = solution?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val bare = value.trim { it == '(' || it == ')' || it == '.' || it.isWhitespace() }
        if (bare.length == 1) {
            labelToIndex(bare[0])?.let { if (it in answers.indices) return it }
        }

        val normalised = normalise(value)
        answers.indexOfFirst { normalise(it) == normalised }.takeIf { it >= 0 }?.let { return it }

        ANSWER_LINE.find(value)?.let { match ->
            labelToIndex(match.groupValues[1][0])?.let { if (it in answers.indices) return it }
        }
        return null
    }

    private fun normalise(text: String): String = WHITESPACE.replace(text.lowercase(), " ").trim()

    private fun labelToIndex(label: Char): Int? =
        when (label) {
            in 'a'..'j' -> label - 'a'
            in 'A'..'J' -> label - 'A'
            in '1'..'9' -> label - '1'
            else -> null
        }

    // endregion
}

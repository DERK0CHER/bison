package net.bison.importer

import net.bison.model.Card
import net.bison.model.Deck
import net.bison.model.Subtopic
import net.bison.model.Task

/** Turns freshly imported questions into a topic with its parts */
object DeckBuilder {
    /**
     * Groups the questions by the topic each one names.
     *
     * Order follows the questions themselves rather than the alphabet, so a set written to build
     * on itself is still in that order when it is studied. Anything that named no topic goes to
     * one part at the end, so a set written without topics still works and simply has one part.
     */
    fun build(
        id: String,
        name: String,
        questions: List<Task>,
    ): Deck {
        val grouped = LinkedHashMap<String, MutableList<Task>>()
        for (question in questions) {
            val topic = question.topic?.trim()?.takeIf { it.isNotEmpty() } ?: UNSORTED
            grouped.getOrPut(topic) { mutableListOf() } += question
        }
        val subtopics =
            grouped.entries.mapIndexed { index, (topic, list) ->
                Subtopic(
                    id = "$id-$index",
                    // a set with no topics at all is one part, and it is the topic itself
                    name = if (topic == UNSORTED && grouped.size == 1) name else topic,
                    cards = list.map { Card(it) },
                )
            }
        return Deck(id = id, name = name, subtopics = subtopics)
    }

    /** Shown when some questions named a topic and others did not */
    const val UNSORTED = "Sonstiges"
}

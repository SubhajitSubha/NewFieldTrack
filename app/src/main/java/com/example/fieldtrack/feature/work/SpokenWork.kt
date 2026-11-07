package com.example.fieldtrack.feature.work

import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.labelRes

enum class SpokenDay { Today, Yesterday }

/** What was understood of a sentence like "rotavator Ramesh three acre yesterday". Anything not understood is null. */
data class SpokenWork(
    val workType: WorkTypeOption? = null,
    val customer: CustomerEntity? = null,
    val quantity: Double? = null,
    val unit: MeasureUnit? = null,
    val day: SpokenDay? = null,
) {
    val understoodAnything: Boolean get() = workType != null || customer != null || quantity != null || unit != null || day != null
}

/**
 * Turns what somebody said into the form they would otherwise fill in.
 *
 * It only fills in what it is sure of: a name that matches two farmers is left for the
 * person to pick, a number it cannot find is left blank. It never saves anything; it
 * puts words in boxes, and the person looks at the boxes.
 */
object SpokenWorkParser {

    private val NUMBER_WORDS = mapOf(
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0,
        "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0, "fifteen" to 15.0,
        "twenty" to 20.0, "half" to 0.5,
    )

    private fun words(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+(?:[.,][\\p{N}]+)?").findAll(text.lowercase()).map { it.value }.toList()

    /**
     * [workWords] maps a word or phrase a person might say to the work it names;
     * [unitWords] does the same for units, and both are given by the screen so they can follow the language.
     */
    fun parse(
        text: String,
        customers: List<CustomerEntity>,
        workWords: Map<String, WorkTypeOption>,
        unitWords: Map<String, MeasureUnit>,
    ): SpokenWork {
        val tokens = words(text)
        if (tokens.isEmpty()) return SpokenWork()
        val joined = " " + tokens.joinToString(" ") + " "

        // The longest phrase first, so "ploughing" is not taken for a shorter word inside it.
        val work = workWords.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (phrase, _) -> joined.contains(" ${phrase.lowercase()} ") }?.value

        val unit = tokens.firstNotNullOfOrNull { unitWords[it] }

        val number = tokens.firstNotNullOfOrNull { token ->
            token.replace(',', '.').toDoubleOrNull() ?: NUMBER_WORDS[token]
        }?.takeIf { it > 0 }

        val day = when {
            "yesterday" in tokens -> SpokenDay.Yesterday
            "today" in tokens -> SpokenDay.Today
            else -> null
        }

        // A farmer is the one whose name words are said. Two farmers who share the
        // best score are ambiguous, and nobody is picked.
        val taken = (workWords.keys + unitWords.keys).map { it.lowercase() }.toSet()
        val spoken = tokens.filter { it !in taken }.toSet()
        val scored = customers.map { customer ->
            customer to words(customer.name).count { part -> part.length >= 2 && part in spoken }
        }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second }
        val customer = best?.let { top -> scored.filter { it.second == top }.singleOrNull()?.first }

        return SpokenWork(work, customer, number, unit, day)
    }
}

/**
 * The words people say for each kind of work and each unit: what the screen calls it in the
 * language it is in, what it is called underneath, and the plain plural of each.
 */
fun spokenWords(context: android.content.Context): Pair<Map<String, WorkTypeOption>, Map<String, MeasureUnit>> {
    val work = buildMap {
        com.example.fieldtrack.feature.workTypeOptions.forEach { option ->
            put(option.key.lowercase(), option)
            put(context.getString(option.labelRes).lowercase(), option)
        }
    }
    val units = buildMap {
        MeasureUnit.entries.forEach { unit ->
            val label = context.getString(unit.labelRes).lowercase()
            for (word in listOf(unit.name.lowercase(), label)) {
                put(word, unit)
                put(word + "s", unit)
            }
        }
    }
    return work to units
}

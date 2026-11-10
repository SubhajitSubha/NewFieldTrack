package com.example.fieldtrack

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every translation says what the English says, with the same blanks to fill.
 *
 * A translated string that lost its `%1$s`, or has one too many, crashes the screen that shows it,
 * in that language only, on somebody else's phone. So the files are held to the English: the same
 * strings (none missing, none spare), the same placeholders in each, every plural with the forms
 * its language needs, and nothing empty. These are drafts: each is for a native speaker to read
 * before it ships, and this test is only the floor under that.
 */
class TranslationsTest {

    private val res = File("src/main/res")
    private val placeholder = Regex("%(\\d+\\$)?[sdf]|%%")

    /** What each language's plural needs, beyond `other`, which every one needs. */
    private val pluralForms = mapOf(
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "es" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "hi" to setOf("one", "other"),
        "bn" to setOf("one", "other"),
        "sw" to setOf("one", "other"),
        "in" to setOf("other"),
    )

    private class Strings(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun read(dir: File): Strings {
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        dir.listFiles { f -> f.extension == "xml" && f.name.startsWith("strings") }.orEmpty().sortedBy { it.name }.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val root = doc.documentElement
            for (i in 0 until root.childNodes.length) {
                val node = root.childNodes.item(i) as? Element ?: continue
                if (node.getAttribute("translatable") == "false") continue
                when (node.tagName) {
                    "string" -> strings[node.getAttribute("name")] = node.textContent
                    "plurals" -> {
                        val items = linkedMapOf<String, String>()
                        for (j in 0 until node.childNodes.length) {
                            val item = node.childNodes.item(j) as? Element ?: continue
                            items[item.getAttribute("quantity")] = item.textContent
                        }
                        plurals[node.getAttribute("name")] = items
                    }
                }
            }
        }
        return Strings(strings, plurals)
    }

    private fun languages(): List<File> =
        res.listFiles { f -> f.isDirectory && Regex("values-[a-z]{2,3}(-r[A-Z]{2})?").matches(f.name) }.orEmpty().sortedBy { it.name }

    private fun blanks(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test fun `every language has every string and plural of the English, with the same blanks`() {
        val english = read(File(res, "values"))
        val problems = mutableListOf<String>()
        for (dir in languages()) {
            val tag = dir.name.removePrefix("values-").substringBefore("-r")
            val other = read(dir)
            for ((name, text) in english.strings) {
                val translated = other.strings[name]
                when {
                    translated == null -> problems += "$tag: missing string $name"
                    translated.isBlank() -> problems += "$tag: empty string $name"
                    blanks(translated) != blanks(text) -> problems += "$tag: $name has blanks ${blanks(translated)} but English has ${blanks(text)}"
                }
            }
            for (name in other.strings.keys - english.strings.keys) problems += "$tag: spare string $name"
            val needs = pluralForms[tag] ?: setOf("other")
            for ((name, forms) in english.plurals) {
                val translated = other.plurals[name]
                if (translated == null) { problems += "$tag: missing plurals $name"; continue }
                for (q in needs) if (translated[q].isNullOrBlank()) problems += "$tag: $name lacks the '$q' form"
                val englishBlanks = forms.values.map { blanks(it) }.first()
                for ((q, text) in translated) {
                    // A form may drop the number where the language says "one" without it, but may not invent a blank.
                    if (!blanks(text).all { it in englishBlanks }) problems += "$tag: $name/$q has blanks ${blanks(text)} not in English ${englishBlanks}"
                }
            }
            for (name in other.plurals.keys - english.plurals.keys) problems += "$tag: spare plurals $name"
        }
        assertTrue(problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test fun `no apostrophe is left bare, which stops the whole file from compiling`() {
        val bare = mutableListOf<String>()
        for (dir in languages()) {
            dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty().forEach { file ->
                file.readLines().forEachIndexed { i, line ->
                    val body = line.substringAfter(">", "").substringBeforeLast("</", "")
                    if (Regex("(?<!\\\\)'").containsMatchIn(body) && !line.trim().startsWith("<!--")) bare += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(bare.take(20).joinToString("\n"), bare.isEmpty())
    }

    @Test fun `the languages shipped are the languages the app offers and Android lists`() {
        // Indonesian's folder is still called "in" in resources; the language tag everywhere else is "id".
        val shipped = languages().map { it.name.removePrefix("values-").substringBefore("-r").let { t -> if (t == "in") "id" else t } }.toSet() + "en"
        val offered = com.example.fieldtrack.data.settings.AppLanguage.SUPPORTED.toSet()
        assertEquals(shipped, offered)
        val listed = Regex("android:name=\"([^\"]+)\"").findAll(File(res, "xml/locales_config.xml").readText()).map { it.groupValues[1].substringBefore("-") }.toSet()
        assertEquals(shipped, listed)
    }
}

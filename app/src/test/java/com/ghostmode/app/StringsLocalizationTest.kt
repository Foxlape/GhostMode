package com.ghostmode.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every translatable English string must exist in Russian (and vice versa) with the same format arguments. */
class StringsLocalizationTest {

    private val en = File("src/main/res/values/strings.xml")
    private val ru = File("src/main/res/values-ru/strings.xml")

    @Test
    fun englishAndRussianHaveTheSameKeys() {
        val enKeys = strings(en).filterValues { it.translatable }.keys
        val ruKeys = strings(ru).keys

        assertTrue("Missing in values-ru: ${enKeys - ruKeys}", (enKeys - ruKeys).isEmpty())
        assertTrue("Missing in values: ${ruKeys - enKeys}", (ruKeys - enKeys).isEmpty())
    }

    @Test
    fun formatArgumentsMatch() {
        val enStrings = strings(en)
        val mismatched = strings(ru).filter { (key, value) ->
            enStrings[key]?.let { formatArgs(it.text) != formatArgs(value.text) } ?: false
        }.keys
        assertTrue("Format arguments differ: $mismatched", mismatched.isEmpty())
    }

    private data class Entry(val text: String, val translatable: Boolean)

    private fun strings(file: File): Map<String, Entry> {
        assertTrue("${file.path} must exist", file.exists())
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to Entry(
                text = node.textContent,
                translatable = node.attributes.getNamedItem("translatable")?.nodeValue != "false"
            )
        }
    }

    private fun formatArgs(text: String): List<String> =
        Regex("%\\d+\\$[sd]").findAll(text).map { it.value }.sorted().toList()
}

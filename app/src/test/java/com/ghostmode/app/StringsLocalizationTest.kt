package com.ghostmode.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every translatable English string must exist in each translation (and vice versa) with the same format arguments. */
class StringsLocalizationTest {

    private val en = File("src/main/res/values/strings.xml")
    private val translations = listOf("values-ru", "values-zh-rCN").map { File("src/main/res/$it/strings.xml") }

    @Test
    fun translationsHaveTheSameKeys() {
        val enKeys = strings(en).filterValues { it.translatable }.keys
        translations.forEach { file ->
            val keys = strings(file).keys
            assertTrue("Missing in ${file.path}: ${enKeys - keys}", (enKeys - keys).isEmpty())
            assertTrue("Missing in values (present in ${file.path}): ${keys - enKeys}", (keys - enKeys).isEmpty())
        }
    }

    @Test
    fun formatArgumentsMatch() {
        val enStrings = strings(en)
        translations.forEach { file ->
            val mismatched = strings(file).filter { (key, value) ->
                enStrings[key]?.let { formatArgs(it.text) != formatArgs(value.text) } ?: false
            }.keys
            assertTrue("Format arguments differ in ${file.path}: $mismatched", mismatched.isEmpty())
        }
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

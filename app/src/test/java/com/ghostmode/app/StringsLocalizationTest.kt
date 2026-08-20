package com.ghostmode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsLocalizationTest {

    @Test
    fun verifyRussianAndEnglishStringsMatch() {
        val baseDir = File("src/main/res")
        val enFile = File(baseDir, "values/strings.xml")
        val ruFile = File(baseDir, "values-ru/strings.xml")

        assertTrue("English strings.xml must exist", enFile.exists())
        assertTrue("Russian strings.xml must exist", ruFile.exists())

        val enKeys = extractStringKeys(enFile)
        val ruKeys = extractStringKeys(ruFile)

        val missingInRu = enKeys - ruKeys
        val missingInEn = ruKeys - enKeys

        assertTrue("Missing in values-ru: $missingInRu", missingInRu.isEmpty())
        assertTrue("Missing in values (en): $missingInEn", missingInEn.isEmpty())
        assertEquals("Both files must have the same number of keys", enKeys.size, ruKeys.size)
    }

    private fun extractStringKeys(file: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        val keys = mutableSetOf<String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val nameAttr = node.attributes.getNamedItem("name")?.nodeValue
            if (nameAttr != null) {
                keys.add(nameAttr)
            }
        }
        return keys
    }
}

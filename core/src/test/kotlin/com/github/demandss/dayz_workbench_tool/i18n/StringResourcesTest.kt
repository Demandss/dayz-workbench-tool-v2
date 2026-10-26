package com.github.demandss.dayz_workbench_tool.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The strings of the interface: every language has the same texts as the default one and keeps their placeholders. */
class StringResourcesTest {
    private val root = File("src/main/composeResources")
    private val entry = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholder = Regex("""%\d+\$[sd]""")

    private fun read(folder: String): Map<String, String> {
        val file = File(root, "$folder/strings.xml")
        assertTrue(file.isFile, "${file.path} is missing")
        return entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val default = read("values")

    @Test
    fun `the default texts are found`() {
        assertTrue(default.size > 600, "the strings should be found, got ${default.size}")
    }

    @Test
    fun `no text of the default language is empty`() {
        val empty = default.filterValues { it.isBlank() }.keys
        assertTrue(empty.isEmpty(), "empty texts: ${empty.take(5)}")
    }

    @Test
    fun `every language has every text of the default language`() {
        for (language in Language.entries.filter { it != Language.EN }) {
            val table = read("values-${language.code}")
            val missing = default.keys.filter { table[it].isNullOrBlank() }
            val extra = table.keys - default.keys
            assertTrue(missing.isEmpty(), "${language.code} misses ${missing.size} texts, for instance: ${missing.take(5)}")
            assertTrue(extra.isEmpty(), "${language.code} has texts the default language does not: ${extra.take(5)}")
        }
    }

    @Test
    fun `a translation keeps the placeholders of its text`() {
        for (language in Language.entries.filter { it != Language.EN }) {
            val table = read("values-${language.code}")
            for ((name, text) in table) {
                assertEquals(
                    placeholder
                        .findAll(default.getValue(name))
                        .map { it.value }
                        .sorted()
                        .toList(),
                    placeholder
                        .findAll(text)
                        .map { it.value }
                        .sorted()
                        .toList(),
                    "${language.code}: $name",
                )
            }
        }
    }
}

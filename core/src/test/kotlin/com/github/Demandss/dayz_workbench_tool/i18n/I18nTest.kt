package com.github.demandss.dayz_workbench_tool.i18n

import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    private val call = Regex("""\btr(?:Key)?\(\s*"((?:[^"\\\n]|\\.)*)"""")
    private val placeholder = Regex("""\{\d+}""")

    @AfterTest
    fun reset() {
        I18n.setting = I18n.SYSTEM
    }

    /** The text as the compiler reads it from a string literal of the sources. */
    private fun unescape(text: String): String =
        Regex("""\\(.)""").replace(text) {
            when (val c = it.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                else -> c
            }
        }

    /** Every text that goes through tr() or trKey() in the sources of the modules. */
    private fun usedTexts(): Set<String> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        return listOf("layout-editor", "core", "app")
            .map { File(root, "$it/src/main") }
            .filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" && it.name != "I18n.kt" }.toList() }
            .flatMap { file -> file.readLines().flatMap { line -> call.findAll(line).map { unescape(it.groupValues[1]) }.toList() } }
            .toSet()
    }

    @Test
    fun `every text of the interface is translated into every language`() {
        val texts = usedTexts()
        assertTrue(texts.size > 100, "the sources should be found, got ${texts.size} texts")
        for (language in Language.entries.filter { it != Language.RU }) {
            val table = Translations.of(language)
            val missing = texts.filter { table[it].isNullOrBlank() }
            assertTrue(missing.isEmpty(), "${language.code} misses ${missing.size} texts, for instance: ${missing.take(5)}")
        }
    }

    @Test
    fun `a translation keeps the placeholders of its text`() {
        for (language in Language.entries.filter { it != Language.RU }) {
            for ((key, value) in Translations.of(language)) {
                assertEquals(
                    placeholder
                        .findAll(key)
                        .map { it.value }
                        .sorted()
                        .toList(),
                    placeholder
                        .findAll(value)
                        .map { it.value }
                        .sorted()
                        .toList(),
                    "${language.code}: $key",
                )
            }
        }
    }

    @Test
    fun `the language of the system is used when it is supported and English otherwise`() {
        assertEquals(Language.DE, Language.ofSystem(Locale.forLanguageTag("de-AT")))
        assertEquals(Language.RU, Language.ofSystem(Locale.forLanguageTag("ru")))
        assertEquals(Language.ZH, Language.ofSystem(Locale.forLanguageTag("zh-CN")))
        assertEquals(Language.EN, Language.ofSystem(Locale.forLanguageTag("ja")))
    }

    @Test
    fun `a chosen language translates and fills the arguments`() {
        I18n.setting = "de"
        assertEquals("Speichern", tr("Сохранить"))
        assertEquals("Gespeichert: a.layout", tr("Сохранено: {0}", "a.layout"))
        I18n.setting = "ru"
        assertEquals("Сохранить", tr("Сохранить"))
        assertEquals("Сохранено: a.layout", tr("Сохранено: {0}", "a.layout"))
    }

    @Test
    fun `a text without a translation shows as it is and an unknown setting falls back to English`() {
        I18n.setting = "fr"
        assertEquals("Нет такого текста 5", tr("Нет такого текста {0}", 5))
        I18n.setting = "xx"
        assertEquals(Language.EN, I18n.language)
        assertEquals("Save", tr("Сохранить"))
    }
}

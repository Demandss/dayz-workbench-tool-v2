package com.github.demandss.dayz_workbench_tool.ui.components

import com.github.demandss.dayz_workbench_tool.i18n.Language
import com.github.demandss.dayz_workbench_tool.i18n.strInEveryLanguage
import com.github.demandss.dayz_workbench_tool.resources.Res
import com.github.demandss.dayz_workbench_tool.resources.language
import com.github.demandss.dayz_workbench_tool.resources.port
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The search of the settings reads the texts of a setting in every language of the interface. */
class SettingsSearchTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun item(vararg words: String) = SettingItem(Res.string.language, words = words.toList()) { }

    @Test
    fun `a text is given in every language, in the order of the languages`() {
        val texts = strInEveryLanguage(Res.string.language)
        assertEquals(Language.entries.size, texts.size)
        assertEquals("Language", texts[Language.entries.indexOf(Language.EN)])
        assertEquals("Язык", texts[Language.entries.indexOf(Language.RU)])
    }

    @Test
    fun `asking for the texts leaves the language of the program as it was`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        strInEveryLanguage(Res.string.language)
        assertEquals("ru", Locale.getDefault().language)
    }

    @Test
    fun `a setting is found by its name in any language`() {
        val index = HashMap<String, String>()
        val setting = item()
        assertTrue(setting.matches("язык", index))
        assertTrue(setting.matches("language", index))
        assertTrue(setting.matches("lang", index))
        assertFalse(setting.matches("grid", index))
    }

    @Test
    fun `a name that is the same in every language is found too`() {
        val index = HashMap<String, String>()
        assertTrue(item("MCP").matches("mcp", index))
        assertFalse(SettingItem(Res.string.port) { }.matches("mcp", index))
    }

    @Test
    fun `the texts of a resource are kept once and found again by its name`() {
        val index = HashMap<String, String>()
        item().matches("язык", index)
        assertTrue(Res.string.language.key in index)
        assertEquals(1, index.size)
    }
}

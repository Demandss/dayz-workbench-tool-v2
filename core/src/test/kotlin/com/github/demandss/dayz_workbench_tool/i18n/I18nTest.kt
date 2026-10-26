package com.github.demandss.dayz_workbench_tool.i18n

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {
    @AfterTest
    fun reset() {
        I18n.setting = I18n.SYSTEM
    }

    @Test
    fun `the language of the system is used when it is supported and English otherwise`() {
        assertEquals(Language.RU, Language.ofSystem(Locale.forLanguageTag("ru")))
        assertEquals(Language.RU, Language.ofSystem(Locale.forLanguageTag("ru-RU")))
        assertEquals(Language.EN, Language.ofSystem(Locale.forLanguageTag("en-GB")))
        // A language the interface does not have
        assertEquals(Language.EN, Language.ofSystem(Locale.forLanguageTag("de-AT")))
        assertEquals(Language.EN, Language.ofSystem(Locale.forLanguageTag("ja")))
    }

    @Test
    fun `a chosen language is used and an unknown setting falls back to English`() {
        I18n.setting = "en"
        assertEquals(Language.EN, I18n.language)
        I18n.setting = "ru"
        assertEquals(Language.RU, I18n.language)
        // A language that was chosen when there were more of them (or a mistake in the file)
        I18n.setting = "de"
        assertEquals(Language.EN, I18n.language)
        I18n.setting = "xx"
        assertEquals(Language.EN, I18n.language)
    }

    @Test
    fun `a saved language the interface does not have becomes the language of the system`() {
        assertEquals("ru", I18n.settingOf("ru"))
        assertEquals("en", I18n.settingOf(" EN ".trim().lowercase()))
        assertEquals(I18n.SYSTEM, I18n.settingOf("system"))
        assertEquals(I18n.SYSTEM, I18n.settingOf(null))
        assertEquals(I18n.SYSTEM, I18n.settingOf(""))
        assertEquals(I18n.SYSTEM, I18n.settingOf("de"))
    }
}

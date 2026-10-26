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
        assertEquals(Language.DE, Language.ofSystem(Locale.forLanguageTag("de-AT")))
        assertEquals(Language.RU, Language.ofSystem(Locale.forLanguageTag("ru")))
        assertEquals(Language.ZH, Language.ofSystem(Locale.forLanguageTag("zh-CN")))
        assertEquals(Language.EN, Language.ofSystem(Locale.forLanguageTag("ja")))
    }

    @Test
    fun `a chosen language is used and an unknown setting falls back to English`() {
        I18n.setting = "de"
        assertEquals(Language.DE, I18n.language)
        I18n.setting = "ru"
        assertEquals(Language.RU, I18n.language)
        I18n.setting = "xx"
        assertEquals(Language.EN, I18n.language)
    }
}

package com.github.demandss.dayz_workbench_tool.stringtable

import com.github.demandss.dayz_workbench_tool.i18n.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringtableTest {
    private val vanilla =
        "\"Language\",\"original\",\"english\",\"czech\",\"german\",\"russian\",\"chinesesimp\",\n" +
            "\"STR_Craft\",\"Craft\",\"Craft\",\"Vyrobit\",\"Herstellen\",\"Create\",\"制作\",\n" +
            "\"STR_Multi\",\"a\",\"Line one\nLine two, \"\"quoted\"\"\",\"\",\"\",\"\",\"\",\n"

    @Test
    fun readsQuotedCellsWithLineBreaksAndTrailingComma() {
        val entries = StringtableCsv.parse(vanilla, "game")
        assertEquals(listOf("STR_Craft", "STR_Multi"), entries.map { it.key })
        assertEquals("Create", entries[0].texts["russian"])
        assertEquals("Line one\nLine two, \"quoted\"", entries[1].texts["english"])
    }

    @Test
    fun aByteOrderMarkIsNotPartOfTheFirstCell() {
        val entries = StringtableCsv.parse("﻿" + vanilla, "game")
        assertEquals(listOf("STR_Craft", "STR_Multi"), entries.map { it.key })
        assertEquals("Create", entries[0].texts["russian"])
    }

    @Test
    fun readsModTablesWithCapitalsAndUnquotedCells() {
        val entries =
            StringtableCsv.parse(
                "LANGUAGE,ORIGINAL,ENGLISH,RUSSIAN,CHINESE SIMP.\r\nSTR_DF_NAME,Dabs,Dabs Framework,Dabs RU,中文\r\n",
                "DabsFramework",
            )
        assertEquals("Dabs RU", entries.single().texts["russian"])
        assertEquals("中文", entries.single().texts["chinesesimp"])
        assertEquals("DabsFramework", entries.single().origin)
    }

    @Test
    fun takesTheColumnOfTheInterfaceLanguage() {
        val craft = StringtableCsv.parse(vanilla, "game").first()
        assertEquals("Create", craft.textIn(Language.RU))
        assertEquals("Craft", craft.textIn(Language.EN))
        assertEquals("Herstellen", craft.textIn(Language.DE))
        assertEquals("制作", craft.textIn(Language.ZH))
        // No column for Ukrainian, and an empty cell: the text stays as it is
        assertNull(craft.textIn(Language.UK))
        assertNull(craft.textIn(Language.FR))
    }

    @Test
    fun registryFindsKeysWithoutRegardToCase() {
        StringtableRegistry.set(StringtableCsv.parse(vanilla, "game"))
        assertEquals("Craft", StringtableRegistry.find("str_craft")?.texts?.get("english"))
        assertNull(StringtableRegistry.find("STR_Nothing"))
    }
}

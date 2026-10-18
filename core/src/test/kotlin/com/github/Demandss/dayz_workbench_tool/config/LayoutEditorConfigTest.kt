package com.github.demandss.dayz_workbench_tool.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutEditorConfigTest {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    @Test
    fun `settings saved before sessions existed still load`() {
        val config = json.decodeFromString(LayoutEditorConfig.serializer(), """{ "undoLimit": 9 }""")

        assertEquals(9, config.undoLimit)
        assertTrue(config.sessions.isEmpty())
    }

    @Test
    fun `session survives a save and load`() {
        val session =
            EditorSession(
                layouts = listOf("K:\\MF_Mods\\a.layout"),
                textures = listOf("K:\\MF_Mods\\b.edds"),
                active = "K:\\MF_Mods\\b.edds",
                activeIsTexture = true,
                expandedDirs = listOf("K:\\MF_Mods\\gui"),
                selectedPath = "K:\\MF_Mods\\b.edds",
                referencePath = "K:\\shots\\workbench.png",
                referenceOpacity = 0.75f,
                referenceShown = true,
            )
        val config = LayoutEditorConfig(sessions = mapOf("k:\\mf_mods" to session))

        val back = json.decodeFromString(LayoutEditorConfig.serializer(), json.encodeToString(LayoutEditorConfig.serializer(), config))

        assertEquals(session, back.sessions["k:\\mf_mods"])
    }
}

package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class McpTokenTest {
    @Test
    fun `a config without a token gets a new one, which is kept`() {
        var kept: LayoutEditorConfig? = null
        val token = McpHost.tokenOf(LayoutEditorConfig()) { kept = it }

        assertTrue(token.matches(Regex("[0-9a-f]{64}")), token)
        assertEquals(token, kept?.mcpToken)
    }

    @Test
    fun `a token that is kept stays the same and is not written again`() {
        val first = McpHost.tokenOf(LayoutEditorConfig()) { }
        var written = 0
        val again = McpHost.tokenOf(LayoutEditorConfig(mcpToken = first)) { written++ }

        assertEquals(first, again)
        assertEquals(0, written)
    }

    @Test
    fun `a token that is too short is replaced`() {
        var kept: LayoutEditorConfig? = null
        val token = McpHost.tokenOf(LayoutEditorConfig(mcpToken = "abc")) { kept = it }

        assertNotEquals("abc", token)
        assertEquals(token, kept?.mcpToken)
    }

    @Test
    fun `the other settings stay when a token is added`() {
        var kept: LayoutEditorConfig? = null
        McpHost.tokenOf(LayoutEditorConfig(mcpPort = 40000, gridSize = 24)) { kept = it }

        assertEquals(40000, kept?.mcpPort)
        assertEquals(24, kept?.gridSize)
    }
}

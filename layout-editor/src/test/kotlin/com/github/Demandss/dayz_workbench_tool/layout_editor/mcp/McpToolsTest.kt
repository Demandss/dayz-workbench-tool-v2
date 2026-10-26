package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tools of the MCP server on a real editor state, working in a folder of their own. */
class McpToolsTest {
    private val workDrive = createTempDirectory("mcp-work").toFile()
    private val mod = File(workDrive, "Mod").also { File(it, "GUI").mkdirs() }
    private val state =
        LayoutEditorState(workDrive.path, CoroutineScope(SupervisorJob() + Dispatchers.Default), "", listOf(mod.path))
    private val tools = McpTools(state)

    private class Answer(
        val text: String,
        val isError: Boolean,
    )

    private fun call(
        name: String,
        args: JsonObject = buildJsonObject { },
    ): Answer {
        val result = tools.call(name, args).jsonObject
        val text =
            result["content"]!!
                .jsonArray[0]
                .jsonObject["text"]!!
                .jsonPrimitive.content
        return Answer(text, result["isError"]?.jsonPrimitive?.booleanOrNull == true)
    }

    private fun ok(
        name: String,
        args: JsonObject = buildJsonObject { },
    ): String {
        val answer = call(name, args)
        assertFalse(answer.isError, "$name failed: ${answer.text}")
        return answer.text
    }

    private fun newLayout(path: String = File(mod, "GUI/menu.layout").path): String {
        ok("create_layout", buildJsonObject { put("path", path) })
        return path
    }

    @Test
    fun `a new layout is written and opened with its root`() {
        val path = newLayout()
        assertTrue(File(path).readText().startsWith("FrameWidgetClass Root"))
        val status = ok("editor_status")
        assertTrue(path.replace("\\", "\\\\") in status || path in status, status)
        assertTrue("work_drive" in status && "mod_folders" in status && mod.path.replace("\\", "\\\\") in status, status)
    }

    @Test
    fun `a widget is added and changed, and the changes are undone`() {
        newLayout()
        ok(
            "add_widget",
            buildJsonObject {
                put("type", "TextWidget")
                put("name", "Title")
            },
        )
        assertTrue("Title" in ok("get_tree"))

        ok(
            "set_properties",
            buildJsonObject {
                put("name", "Title")
                put("properties", buildJsonObject { put("text", "\"Hello\"") })
            },
        )
        assertTrue("Hello" in ok("get_widget", buildJsonObject { put("name", "Title") }))

        ok("undo")
        assertFalse("Hello" in ok("get_widget", buildJsonObject { put("name", "Title") }))
        ok("undo")
        assertFalse("Title" in ok("get_tree"))
    }

    @Test
    fun `saving writes the widgets into the file`() {
        val path = newLayout()
        ok(
            "add_widget",
            buildJsonObject {
                put("type", "TextWidget")
                put("name", "Title")
            },
        )
        ok("save_layout")
        assertTrue("TextWidgetClass Title" in File(path).readText())
    }

    @Test
    fun `a layout outside the work drive and the mods is refused`() {
        val outside = createTempDirectory("mcp-outside").toFile()
        val answer = call("create_layout", buildJsonObject { put("path", File(outside, "x.layout").path) })
        assertTrue(answer.isError)
        assertTrue("outside" in answer.text, answer.text)
        assertFalse(File(outside, "x.layout").exists())
    }

    @Test
    fun `a layout is found by words of its path in the connected mods`() {
        newLayout(File(mod, "GUI/inventory/bag.layout").path)
        newLayout(File(mod, "GUI/map.layout").path)

        val all = ok("list_layouts")
        assertTrue("bag.layout" in all && "map.layout" in all, all)

        val some = ok("list_layouts", buildJsonObject { put("query", "inventory") })
        assertTrue("bag.layout" in some && "map.layout" !in some, some)

        val none = ok("list_layouts", buildJsonObject { put("query", "no-such-layout") })
        assertTrue("\"count\":0" in none, none)
    }

    @Test
    fun `an unknown tool is an error of that tool`() {
        val answer = call("no_such_tool")
        assertTrue(answer.isError, answer.text)
    }

    @Test
    fun `a tool that only looks says so, and one that changes does not`() {
        val hints =
            McpTools.definitions().associate {
                it["name"]!!.jsonPrimitive.content to it["annotations"]!!.jsonObject["readOnlyHint"]!!.jsonPrimitive.booleanOrNull
            }
        assertEquals(true, hints["get_tree"])
        assertEquals(true, hints["list_layouts"])
        assertEquals(false, hints["set_properties"])
        assertEquals(false, hints["delete_widget"])
        assertEquals(false, hints["save_layout"])
    }

    @Test
    fun `the answer of a tool is a content list`() {
        val result = tools.call("editor_status", buildJsonObject { })
        val content = result.jsonObject["content"] as JsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(JsonPrimitive(content[0].jsonObject["text"]!!.jsonPrimitive.content).isString)
    }
}

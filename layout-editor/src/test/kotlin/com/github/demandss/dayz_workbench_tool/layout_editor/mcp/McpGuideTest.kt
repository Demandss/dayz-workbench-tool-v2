package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the assistant is told about the tools has to agree with the tools. */
class McpGuideTest {
    private val tools = McpTools.definitions()
    private val names = tools.map { it["name"]!!.jsonPrimitive.content }

    private fun promptText(
        name: String,
        arguments: JsonObject? = null,
    ): String =
        McpGuide
            .getPrompt(name, arguments)!!
            .jsonObject["messages"]!!
            .jsonArray[0]
            .jsonObject["content"]!!
            .jsonObject["text"]!!
            .jsonPrimitive.content

    private val promptNames =
        (McpGuide.listPrompts().jsonObject["prompts"] as JsonArray).map { (it as JsonObject)["name"]!!.jsonPrimitive.content }

    @Test
    fun `tool names are unique and every tool has a description`() {
        assertEquals(names.size, names.toSet().size, "a name is used twice")
        for (tool in tools) {
            assertTrue(tool["description"]!!.jsonPrimitive.content.isNotBlank(), "${tool["name"]} has no description")
        }
    }

    @Test
    fun `a required argument is one of the properties of the tool`() {
        for (tool in tools) {
            val schema = tool["inputSchema"]!!.jsonObject
            val properties = schema["properties"]!!.jsonObject.keys
            val required = (schema["required"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
            assertTrue(properties.containsAll(required), "${tool["name"]} requires ${required - properties}, which it does not take")
        }
    }

    @Test
    fun `every extra shape of an argument names a tool and an argument that exist`() {
        for (key in McpTools.ARGUMENT_SHAPES.keys) {
            val (tool, argument) = key.split(".")
            val definition = tools.firstOrNull { it["name"]!!.jsonPrimitive.content == tool }
            assertTrue(definition != null, "$key: there is no tool $tool")
            val properties = definition["inputSchema"]!!.jsonObject["properties"]!!.jsonObject
            assertTrue(argument in properties.keys, "$key: $tool takes no $argument")
        }
    }

    @Test
    fun `a list says what it holds and screenshot lists its modes`() {
        for (tool in tools) {
            val properties = tool["inputSchema"]!!.jsonObject["properties"]!!.jsonObject
            for ((argument, schema) in properties) {
                val spec = schema.jsonObject
                if (spec["type"]!!.jsonPrimitive.content == "array") {
                    assertTrue("items" in spec, "${tool["name"]!!.jsonPrimitive.content}.$argument is a list without items")
                }
            }
        }
        val screenshot = tools.first { it["name"]!!.jsonPrimitive.content == "screenshot" }
        val mode = screenshot["inputSchema"]!!.jsonObject["properties"]!!.jsonObject["mode"]!!.jsonObject
        assertEquals(listOf("editor", "preview", "preview_workbench"), (mode["enum"] as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun `every tool the guide and the prompts name exists`() {
        val verb = "get|set|list|open|create|save|add|copy|move|delete|rename|convert|measure|register|replace|find|select|close"
        val text =
            McpGuide.instructions +
                McpGuide.assistantPrompt("http://127.0.0.1:1/mcp", "t", "work-drive", "", emptyList()) +
                promptNames.joinToString("\n") { promptText(it) }
        val named = Regex("\\b(?:$verb)_[a-z_]+\\b").findAll(text).map { it.value }.toSet() + "editor_status"
        assertTrue(named.size > 20, "the tools should be found in the guide, got ${named.size}")
        val missing = named.filter { it !in names }
        assertTrue(missing.isEmpty(), "the guide names tools that do not exist: $missing")
    }

    @Test
    fun `the guide and the prompts are in English and have no blanks left`() {
        val cyrillic = Regex("[\\u0400-\\u04FF]")
        val texts =
            listOf(McpGuide.instructions) + promptNames.map { promptText(it) } + McpGuide.assistantPrompt("u", "t", "w", "g", listOf("m"))
        for (text in texts) {
            assertFalse(cyrillic.containsMatchIn(text), "a text has Russian words: ${text.take(60)}")
            assertFalse("{{" in text, "a blank is left in: ${text.take(60)}")
        }
    }

    @Test
    fun `the version of the server comes from the build`() {
        assertTrue(McpHost.serverVersion.matches(Regex("[0-9][0-9A-Za-z.+_-]*")), McpHost.serverVersion)
    }

    @Test
    fun `a prompt takes the default for an argument that is not given`() {
        val script = promptText("write_script")
        assertTrue("kind menu" in script, script)
        assertTrue("{{" !in script)
        val edit = promptText("edit_layout", buildJsonObject { put("task", "add a title") })
        assertTrue("add a title" in edit, edit)
    }

    @Test
    fun `the prompt for the assistant says where to look and how to connect`() {
        val prompt =
            McpGuide.assistantPrompt("http://127.0.0.1:39871/mcp", "secret", "work-drive", "game-folder", listOf("mod-one", "mod-two"))
        assertTrue("work-drive" in prompt && "game-folder" in prompt && "mod-one" in prompt && "mod-two" in prompt)
        assertTrue("claude mcp add" in prompt && "Bearer secret" in prompt && "http://127.0.0.1:39871/mcp" in prompt)
        assertTrue("editor_status" in prompt && "list_layouts" in prompt)
        assertTrue(prompt.endsWith("My task: "))
        assertFalse("\n\n\n" in prompt)
        // Without the token it does not carry the command that connects
        val plain = McpGuide.assistantPrompt("http://127.0.0.1:39871/mcp", null, "", "", emptyList())
        assertFalse("claude mcp add" in plain || "Bearer" in plain)
        assertTrue("not set" in plain && "none" in plain)
    }
}

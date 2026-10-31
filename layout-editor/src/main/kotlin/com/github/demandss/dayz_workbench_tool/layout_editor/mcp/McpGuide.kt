package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * What an assistant needs to know to work with the editor, sent when it connects (the instructions) and as prompts, which
 * Claude Code offers as commands. The texts are files in `resources/mcp`, in English because they are written for the assistant;
 * this object only reads them and fills in the blanks `{{name}}`. Every tool they name must exist: McpGuideTest checks it.
 */
internal object McpGuide {
    /** A text of `resources/mcp`, with the line breaks of every system made one. */
    private fun text(file: String): String {
        val stream = McpGuide::class.java.getResourceAsStream("/mcp/$file") ?: error("The resource mcp/$file is missing")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }.replace("\r\n", "\n")
    }

    /** [template] with every `{{name}}` replaced by its value; a name without a value is left as it is. */
    private fun fill(
        template: String,
        values: Map<String, String>,
    ): String = Regex("""\{\{(\w+)}}""").replace(template) { values[it.groupValues[1]] ?: it.value }

    /** The rules of the format and how to work, sent when the assistant connects. */
    val instructions: String by lazy { text("instructions.md").trim() }

    /**
     * A prompt for the person to paste into the chat of an assistant: what the server is and where to look, so that the assistant
     * does not have to search and the person does not have to explain. With [token] it also carries the command that connects it.
     */
    fun assistantPrompt(
        url: String,
        token: String?,
        workDrive: String,
        gameFolder: String,
        mods: List<String>,
    ): String {
        val connect = if (token == null) "" else fill(text("assistant-connect.md"), mapOf("url" to url, "token" to token))
        val folders = if (mods.isEmpty()) "  - none" else mods.joinToString("\n") { "  - $it" }
        return fill(
            text("assistant-prompt.md"),
            mapOf(
                "connect" to connect,
                "work_drive" to workDrive.ifBlank { "not set" },
                "game_folder" to gameFolder.ifBlank { "not set" },
                "mods" to folders,
            ),
        ).replace("\n\n\n", "\n\n")
    }

    private class PromptArgument(
        val name: String,
        val description: String,
        val required: Boolean,
        val default: String,
    )

    private class Prompt(
        val name: String,
        val description: String,
        val file: String,
        val arguments: List<PromptArgument>,
    )

    private val prompts: List<Prompt> by lazy {
        Json
            .parseToJsonElement(text("prompts.json"))
            .jsonArray
            .map { entry ->
                val item = entry.jsonObject
                Prompt(
                    item.getValue("name").jsonPrimitive.content,
                    item.getValue("description").jsonPrimitive.content,
                    item.getValue("file").jsonPrimitive.content,
                    item.getValue("arguments").jsonArray.map { argument ->
                        val a = argument.jsonObject
                        PromptArgument(
                            a.getValue("name").jsonPrimitive.content,
                            a.getValue("description").jsonPrimitive.content,
                            a["required"]?.jsonPrimitive?.booleanOrNull ?: false,
                            a["default"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        )
                    },
                )
            }
    }

    fun listPrompts(): JsonElement =
        buildJsonObject {
            put(
                "prompts",
                JsonArray(
                    prompts.map { p ->
                        buildJsonObject {
                            put("name", p.name)
                            put("description", p.description)
                            put(
                                "arguments",
                                buildJsonArray {
                                    p.arguments.forEach { argument ->
                                        add(
                                            buildJsonObject {
                                                put("name", argument.name)
                                                put("description", argument.description)
                                                put("required", argument.required)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                ),
            )
        }

    /** The messages of the prompt [name], or null when there is none. */
    fun getPrompt(
        name: String,
        arguments: JsonObject?,
    ): JsonElement? {
        val prompt = prompts.firstOrNull { it.name == name } ?: return null
        val given = arguments?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
        // An argument that was not given, or is blank, takes the default of the prompt
        val values = prompt.arguments.associate { it.name to (given[it.name]?.ifBlank { null } ?: it.default) }
        return buildJsonObject {
            put("description", prompt.description)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", fill(text(prompt.file), values).trim())
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}

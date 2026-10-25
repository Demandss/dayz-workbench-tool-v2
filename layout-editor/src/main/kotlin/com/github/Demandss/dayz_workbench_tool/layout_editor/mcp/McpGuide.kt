@file:Suppress("ktlint:standard:max-line-length")

package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What an assistant needs to know to work with the editor, sent when it connects (the instructions) and as prompts, which
 * Claude Code offers as commands. Written for the assistant, so in English.
 */
internal object McpGuide {
    val instructions: String =
        """
        You are connected to the DayZ Layout Editor. It edits .layout files of DayZ GUI and draws them the way the game does.

        Workflow
        1. editor_status shows what is open. open_layout opens a file when nothing is; create_layout makes a new file and opens it.
        2. get_tree (max_depth to keep it short) shows the widgets with ids, types, properties and the computed rectangle in pixels.
        3. Change things with set_properties, add_widget (add_widgets for many at once), copy_widget (a widget with its children, many times), rename_widget, move_widget, delete_widget. Every change can be undone with undo.
        4. Look at the result: screenshot with mode preview (as the game draws it); area screen gives only the screen of the layout in exactly its own pixels. Use preview_workbench only to compare with the Workbench of 1.30.
        5. save_layout writes the file. Do not leave a layout modified and unsaved when the task is done.
        6. get_problems lists what the editor thinks is wrong.

        Rules of the format
        - Property values are written as they stand in the .layout file: text and paths in quotes ("set:Icons image:Cross"), numbers and flags without.
        - A property name with a space (text color, exact text size) is given without quotes: the file writer quotes it. A name the editor does not know is written all the same, with a warning in the answer: read the warnings.
        - "exact text size" must be a whole number. Workbench 1.30 stops reading the layout after a widget with a fractional size and shows nothing of what follows, while this preview still draws it.
        - Text has no line breaks: \n shows as it is. Give each line its own TextWidget, or set wrap 1 so that it wraps by the width of the widget.
        - position and size are pixels when hexactpos/vexactpos and hexactsize/vexactsize are 1, otherwise shares of the parent (0.5 is half).
        - halign and valign anchor to the parent: left_ref, center_ref, right_ref (and top_ref, bottom_ref). A bare center does not centre.
        - color is r g b a from 0 to 1. The editor reads it as sRGB, like the game. Workbench 1.30 reads it as linear light and shows it lighter.
        - Names of widgets are Latin letters, digits and underscore, because scripts find a widget by name (FindAnyWidget).
        - Fonts: list_fonts gives the paths that exist; put one in quotes into font. The game has no monospaced font, and a name that is not in the list is drawn as Metron.
        - The built-in bitmap fonts (metron, metron-bold ...) come in the sizes 12, 14, 16, 22, 28, 48 and 58 only: exact text size does not scale them. For other sizes use an sdf_* font, or make a font of the size you need with convert_font. measure_text gives the width a text takes with a font and a size, so no screenshot is needed to fit it.
        - A text is cut at the left and right edge of its parent widget (measured in Workbench): a text placed left of its parent loses its first letters. Keep the text inside the parent.
        - Group the widgets by logical blocks (Header, ToolBar, MainInfo, Progress, RewardsList, Footer): every block is a Frame or Panel with the rectangle of the block, and its children are placed relative to it. Not a flat list in the root, no parent with more than about 9 children.
        - A text that is #KEY (#STR_Craft) is a key of a stringtable.csv. The editor draws the text of the key in its own language, and leaves the text as it is when the key or the language is not there. list_strings finds keys of the game and of the connected mods; prefer a key to a literal text in layouts of a mod that has a stringtable.
        - An icon from an imageset that has transparent parts needs mode blend and "src alpha" 1 on its ImageWidget, otherwise it is drawn with a black square behind it (as the game does). A tint is the widget color.
        - To change one property on many widgets (the states of repeated rows, the colours of a bar) use set_properties_many with names or name_regex, not one call each.
        - Icons come from imagesets: list_icons gives set:NAME image:IMAGE, which goes into image0. Textures go into imageTexture with a path from the mod folder.
        - Styles: list_styles for a widget class, then set the style property. A panel without a style draws nothing. For a flat fill of its color use style rover_sim_colorable on a panel: the style Colorable exists for buttons, windows, scrolls and spacers, and on a panel the game draws nothing for it. On a button Colorable colours the text too. A style that list_styles does not show for the class draws nothing in the game, and the tools warn about it.
        - Files inside the game's PBO are read-only.

        Scripts
        get_script_template returns an Enforce Script class for the active layout (kind menu, component or widgets): the widget members, the lookup,
        the events of buttons and inputs and the registration of a menu. Use it as the base of the script file instead of writing the lookup by hand.
        """.trimIndent()

    private class Prompt(
        val name: String,
        val description: String,
        val arguments: List<Pair<String, String>>,
        val text: (Map<String, String>) -> String,
    )

    private val prompts =
        listOf(
            Prompt(
                "edit_layout",
                "Change the open layout and check the result on a screenshot",
                listOf("task" to "What to change or build"),
            ) { a ->
                "Task in the DayZ Layout Editor: ${a["task"].orEmpty()}\n\n" +
                    "Start with editor_status and get_tree. Make the changes with the editing tools, take a screenshot with mode preview, " +
                    "fix what looks wrong, run get_problems, and save the layout when it is right."
            },
            Prompt(
                "write_script",
                "Write the Enforce Script class for the open layout",
                listOf("class_name" to "Name of the class (optional)", "kind" to "menu, component or widgets (default menu)"),
            ) { a ->
                "Write the Enforce Script for the layout that is open in the DayZ Layout Editor.\n\n" +
                    "Call get_script_template with kind ${a["kind"]?.ifBlank { null } ?: "menu"}" +
                    (a["class_name"]?.ifBlank { null }?.let { " and class_name $it" } ?: "") +
                    ", then save the class as a .c file in the right scripts folder of the mod (the mod's own 3_Game, 4_World or 5_Mission by its conventions), " +
                    "fill in the TODO branches of the events, and add the registration lines from the end of the template where they belong."
            },
            Prompt(
                "review_layout",
                "Look for mistakes in the open layout",
                emptyList(),
            ) { _ ->
                "Review the layout that is open in the DayZ Layout Editor. Run get_problems, take screenshots with mode preview at 1920x1080 and at 1280x720 " +
                    "(set_view resolution), look for widgets that are cut, overlap or leave the screen, bad anchors, missing styles or icons and names that a " +
                    "script cannot use. Report what you found; change nothing unless asked."
            },
        )

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
                                    p.arguments.forEach { (name, text) ->
                                        add(
                                            buildJsonObject {
                                                put("name", name)
                                                put("description", text)
                                                put(
                                                    "required",
                                                    p.name == "edit_layout",
                                                )
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
        val values = arguments?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
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
                                    put("text", prompt.text(values))
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}

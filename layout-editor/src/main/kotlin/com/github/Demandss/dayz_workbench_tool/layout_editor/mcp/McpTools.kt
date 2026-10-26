package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import com.github.demandss.dayz_workbench_tool.font.FontImporter
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout.LayoutSerializer
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.pathTo
import com.github.demandss.dayz_workbench_tool.layout_editor.removeNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TemplateKind
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.allNames
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.copiedWithNewIds
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.findProblems
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.measureText
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.sanitizeWidgetName
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.filterStrings
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.knownPropertyKeys
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import com.github.demandss.dayz_workbench_tool.stringtable.StringtableRegistry
import com.github.demandss.dayz_workbench_tool.stringtable.textIn
import com.github.demandss.dayz_workbench_tool.texture.EddsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

/** The tools of the MCP server. They run on the UI thread of the editor, so that the screen and the tools never disagree. */
internal class McpTools(
    val state: LayoutEditorState,
) {
    private class ToolFailure(
        message: String,
    ) : Exception(message)

    fun call(
        name: String,
        args: JsonObject,
    ): JsonElement {
        val started = System.currentTimeMillis()
        var failed = false
        try {
            val result =
                when (name) {
                    "screenshot" -> return screenshot(args)

                    // Waits for fonts that load in the background, so it must not hold the main thread
                    "measure_text" -> return textResult(measureTextTool(args))

                    else -> onMain { run(name, args) }
                }
            return textResult(result)
        } catch (e: ToolFailure) {
            failed = true
            return textResult(e.message.orEmpty(), isError = true)
        } catch (e: Exception) {
            failed = true
            return textResult("${e.javaClass.simpleName}: ${e.message}", isError = true)
        } finally {
            McpHost.log("$name  ${System.currentTimeMillis() - started} ms${if (failed) "  failed" else ""}")
        }
    }

    private fun <T> onMain(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }

    private fun textResult(
        text: String,
        isError: Boolean = false,
    ): JsonObject =
        buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text)
                        },
                    )
                },
            )
            if (isError) put("isError", true)
        }

    private fun run(
        name: String,
        args: JsonObject,
    ): String =
        when (name) {
            "editor_status" -> status().toString()
            "list_layouts" -> listLayouts(args)
            "open_layout" -> openLayout(args)
            "create_layout" -> createLayout(args)
            "close_layout" -> closeLayout(args)
            "save_layout" -> saveLayout()
            "undo" -> undoRedo(true, args)
            "redo" -> undoRedo(false, args)
            "get_tree" -> getTree(args)
            "get_widget" -> widgetJson(requireNode(args), withRect = true).toString()
            "find_widgets" -> findWidgets(args)
            "set_properties" -> setProperties(args)
            "set_properties_many" -> setPropertiesMany(args)
            "add_widget" -> addWidget(args)
            "add_widgets" -> addWidgets(args)
            "copy_widget" -> copyWidget(args)
            "delete_widget" -> deleteWidget(args)
            "rename_widget" -> renameWidget(args)
            "move_widget" -> moveWidget(args)
            "select_widgets" -> selectWidgets(args)
            "get_layout_text" -> layoutText()
            "get_script_template" -> scriptTemplate(args)
            "replace_layout_text" -> replaceLayoutText(args)
            "set_view" -> setView(args)
            "get_problems" -> problems()
            "list_icons" -> listIcons(args)
            "list_styles" -> listStyles(args)
            "list_imagesets" -> state.imageSetNames(state.activeFile?.path).joinToString("\n")
            "list_fonts" -> listFonts(args)
            "list_strings" -> listStrings(args)
            "open_file" -> openFile(args)
            "create_imageset" -> createImageset(args)
            "create_style" -> createStyle(args)
            "register_assets" -> registerAssets(args)
            "convert_texture" -> convertTexture(args)
            "convert_font" -> convertFont(args)
            else -> throw ToolFailure("Unknown tool: $name")
        }

    // ---- reading

    private fun requireDoc() = state.activeFile ?: throw ToolFailure("No layout is open: use open_layout")

    private fun status(): JsonObject =
        buildJsonObject {
            put(
                "open_layouts",
                buildJsonArray {
                    state.openFiles.forEachIndexed { i, d ->
                        add(
                            buildJsonObject {
                                put("index", i)
                                put("path", d.path)
                                put("modified", d.isModified)
                                put(
                                    "active",
                                    i == state.activeFileIndex,
                                )
                            },
                        )
                    }
                },
            )
            put(
                "selected",
                buildJsonArray {
                    val root = state.activeFile?.root
                    state.selectedIds.filter { root?.findNode(it) != null }.forEach { add(JsonPrimitive(it.toString())) }
                },
            )
            put("live_preview", state.livePreview)
            put("workbench_130_colors", state.workbenchColors)
            put("show_grid", state.showGrid)
            put("show_widget_names", state.showWidgetNames)
            put("grid_size", state.gridSize)
            put("snap_to_grid", state.snapToGrid)
            put("zoom", state.zoom.toDouble())
            put("resolution", state.selectedResolution.label)
            put("work_drive", state.workDrivePath)
            put("game_folder", state.gameFolderPath())
            put("mod_folders", JsonArray(state.modFolderPaths().map { JsonPrimitive(it) }))
        }

    private fun listLayouts(args: JsonObject): String {
        val query =
            args
                .string("query")
                ?.trim()
                ?.lowercase()
                .orEmpty()
        val limit = (args.int("limit") ?: 100).coerceIn(1, 500)
        val roots =
            args.string("folder")?.let { listOf(writable(it)) }
                ?: state.modFolderPaths().map { java.io.File(it) }.filter { it.isDirectory }.ifEmpty {
                    throw ToolFailure(
                        "No mod is connected: pass folder (the folder of a mod, inside the work drive) or connect the mods in the settings of the app",
                    )
                }
        val found =
            roots
                .asSequence()
                .flatMap { root ->
                    root
                        .walkTopDown()
                        .maxDepth(12)
                        .onEnter { !it.name.startsWith(".") && !it.name.equals("build", ignoreCase = true) }
                        .filter { it.isFile && it.extension.equals("layout", ignoreCase = true) }
                        .map { it.path }
                }.filter { query.isEmpty() || it.lowercase().contains(query) }
                .take(limit + 1)
                .toList()
        return buildJsonObject {
            put("count", minOf(found.size, limit))
            put("truncated", found.size > limit)
            put("layouts", JsonArray(found.take(limit).map { JsonPrimitive(it) }))
        }.toString()
    }

    private fun resolveNode(
        doc: com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutDocument,
        args: JsonObject,
        keyPrefix: String = "",
    ): EditorWidgetNode? {
        args.string(keyPrefix + "id")?.let { raw ->
            val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ToolFailure("Not a widget id: $raw")
            return doc.root.findNode(id)
        }
        args.string(keyPrefix + "path")?.let { path ->
            var current = doc.root
            val parts = path.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            if (parts.first() != current.node.name) return null
            for (part in parts.drop(1)) current = current.children.firstOrNull { it.node.name == part } ?: return null
            return current
        }
        args.string(keyPrefix + "name")?.let { name ->
            fun search(n: EditorWidgetNode): EditorWidgetNode? = if (n.node.name == name) n else n.children.firstNotNullOfOrNull(::search)
            return search(doc.root)
        }
        return null
    }

    private fun requireNode(
        args: JsonObject,
        keyPrefix: String = "",
    ): EditorWidgetNode {
        val doc = requireDoc()
        if (args.string(keyPrefix + "id") == null && args.string(keyPrefix + "name") == null && args.string(keyPrefix + "path") == null) {
            throw ToolFailure("Give the widget by ${keyPrefix}id, ${keyPrefix}name or ${keyPrefix}path")
        }
        return resolveNode(doc, args, keyPrefix) ?: throw ToolFailure("Widget not found")
    }

    private fun rectsOf(doc: com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutDocument): Map<UUID, Rect> =
        layoutTree(doc.root, screenRect(state)).rects

    private fun widgetJson(
        node: EditorWidgetNode,
        withRect: Boolean,
        rects: Map<UUID, Rect>? = null,
        withProps: Boolean = true,
    ): JsonObject {
        val doc = requireDoc()
        val all = rects ?: if (withRect) rectsOf(doc) else emptyMap()
        return buildJsonObject {
            put("id", node.id.toString())
            put("name", node.node.name)
            put(
                "type",
                node.node.originalClassName
                    .ifEmpty { node.node.type.className }
                    .removeSuffix("Class"),
            )
            doc.root.pathTo(node.id)?.let { put("path", it.joinToString("/") { n -> n.node.name }) }
            all[node.id]?.let { r ->
                put(
                    "rect",
                    buildJsonObject {
                        put("x", r.left.toDouble())
                        put("y", r.top.toDouble())
                        put("w", r.width.toDouble())
                        put("h", r.height.toDouble())
                    },
                )
            }
            if (withProps) put("properties", buildJsonObject { node.node.properties.forEach { (k, v) -> put(k, v) } })
            put("children", node.children.size)
        }
    }

    private fun treeJson(
        node: EditorWidgetNode,
        depth: Int,
        maxDepth: Int,
        rects: Map<UUID, Rect>,
        withProps: Boolean,
    ): JsonObject {
        val base = widgetJson(node, withRect = true, rects = rects, withProps = withProps)
        if (depth >= maxDepth || node.children.isEmpty()) return base
        return buildJsonObject {
            base.forEach { (k, v) -> if (k != "children") put(k, v) }
            put("children", JsonArray(node.children.map { treeJson(it, depth + 1, maxDepth, rects, withProps) }))
        }
    }

    private fun getTree(args: JsonObject): String {
        val doc = requireDoc()
        val start =
            if (args.string("id") != null || args.string("name") != null || args.string("path") != null) requireNode(args) else doc.root
        val maxDepth = args.int("max_depth") ?: Int.MAX_VALUE
        return treeJson(start, 0, maxDepth, rectsOf(doc), args.bool("include_properties") ?: true).toString()
    }

    private fun findWidgets(args: JsonObject): String {
        val doc = requireDoc()
        val query = args.string("query")?.lowercase()
        val type =
            args
                .string("type")
                ?.lowercase()
                ?.removeSuffix("class")
                ?.removeSuffix("widget")
        val limit = args.int("limit") ?: 50
        val found = mutableListOf<EditorWidgetNode>()

        fun visit(n: EditorWidgetNode) {
            val typeName =
                n.node.type.className
                    .lowercase()
                    .removeSuffix("class")
                    .removeSuffix("widget")
            val byName =
                query == null ||
                    n.node.name
                        .lowercase()
                        .contains(query) ||
                    n.node.properties.values
                        .any { it.lowercase().contains(query) }
            if (byName && (type == null || typeName == type)) found += n
            n.children.forEach(::visit)
        }
        visit(doc.root)
        val rects = rectsOf(doc)
        return JsonArray(found.take(limit).map { widgetJson(it, withRect = true, rects = rects, withProps = false) }).toString()
    }

    private fun layoutText(): String = LayoutSerializer.serialize(requireDoc().root.toWidgetNode())

    private fun scriptTemplate(args: JsonObject): String {
        requireDoc()
        val kind =
            when (args.string("kind")?.lowercase()) {
                null, "menu" -> TemplateKind.MENU
                "component" -> TemplateKind.COMPONENT
                "widgets" -> TemplateKind.WIDGETS
                else -> throw ToolFailure("kind is menu, component or widgets")
            }
        return state.scriptTemplateText(kind, args.string("class_name")) ?: throw ToolFailure("No layout is open")
    }

    private fun problems(): String {
        val doc = requireDoc()
        return JsonArray(
            findProblems(doc.root).map {
                buildJsonObject {
                    put("widget_id", it.nodeId.toString())
                    put("widget", it.widgetName)
                    put("severity", it.severity.name.lowercase())
                    put("message", it.message)
                }
            },
        ).toString()
    }

    private fun listIcons(args: JsonObject): String {
        val words =
            args
                .string("query")
                .orEmpty()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        val limit = args.int("limit") ?: 100
        val icons =
            state
                .allIcons(state.activeFile?.path)
                .filter { (set, image) -> words.all { it in "$set $image".lowercase() } }
        return buildJsonObject {
            put("total", icons.size)
            put("icons", JsonArray(icons.take(limit).map { (set, image) -> JsonPrimitive("set:$set image:$image") }))
        }.toString()
    }

    /** The keys of the string tables of the game and of the connected mods that fit a search, with the text in the editor language. */
    private fun listStrings(args: JsonObject): String {
        val query = args.string("query").orEmpty()
        val source = args.string("source")?.takeIf { it.isNotBlank() && it != "all" } ?: "*"
        val limit = (args.int("limit") ?: 50).coerceIn(1, 500)
        val found = filterStrings(StringtableRegistry.entries, source, query)
        val language = I18n.language
        return buildJsonObject {
            put("total", found.size)
            put("language", language.code)
            put(
                "sources",
                JsonArray(
                    StringtableRegistry.entries
                        .map { it.origin }
                        .distinct()
                        .map { JsonPrimitive(it) },
                ),
            )
            put(
                "strings",
                JsonArray(
                    found.take(limit).map { e ->
                        buildJsonObject {
                            put("key", e.key)
                            put("text", e.textIn(language) ?: e.texts["english"].orEmpty())
                            put("source", e.origin)
                        }
                    },
                ),
            )
            put(
                "note",
                "Put #KEY into the text property. The editor draws the text of the key in its own language; a key not found stays as it is.",
            )
        }.toString()
    }

    /** A name that a script and an imageset can use: Latin letters, digits and underscore. */
    private fun assetName(text: String): String =
        text
            .trim()
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .trim('_')
            .ifEmpty { "image" }

    private fun readPicture(file: java.io.File): java.awt.image.BufferedImage =
        if (file.extension.equals("edds", ignoreCase = true)) {
            EddsCodec.decode(file).toBufferedImage()
        } else {
            javax.imageio.ImageIO.read(file) ?: throw ToolFailure("Not a picture: ${file.path}")
        }

    /**
     * Makes an imageset from pictures: they are packed into one atlas (`<name>.edds`) and described in `<name>.imageset`, which
     * lies next to it. The answer lists every image as `set:NAME image:IMAGE`, ready for the image0 of an ImageWidget.
     */
    private fun createImageset(args: JsonObject): String {
        val output = java.io.File(args.string("output") ?: throw ToolFailure("output is required: the full path of the .imageset"))
        if (!output.name.endsWith(".imageset", ignoreCase = true)) throw ToolFailure("output must end with .imageset")
        writable(output.path)
        if (output.exists() && args.bool("overwrite") != true) throw ToolFailure("${output.path} exists: pass overwrite=true to replace it")
        val sources = mutableListOf<Pair<String, java.io.File>>()
        (args["images"] as? JsonArray)?.forEachIndexed { i, raw ->
            val spec = raw as? JsonObject ?: throw ToolFailure("images[$i] is not an object")
            val source = java.io.File(spec.string("source") ?: throw ToolFailure("images[$i].source is required"))
            sources += assetName(spec.string("name") ?: source.nameWithoutExtension) to source
        }
        args.string("folder")?.let { folder ->
            val dir = java.io.File(folder)
            if (!dir.isDirectory) throw ToolFailure("No such folder: $folder")
            dir
                .listFiles { f -> f.isFile && f.extension.lowercase() in setOf("png", "jpg", "jpeg", "bmp", "gif", "edds") }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .forEach { sources += assetName(it.nameWithoutExtension) to it }
        }
        if (sources.isEmpty()) throw ToolFailure("Give images (a list of {name, source}) or a folder with pictures")
        val masked = args.bool("mask") == true
        val builder = com.github.demandss.dayz_workbench_tool.assets.ImageSetBuilder
        val coloured = mutableListOf<String>()
        val pictures =
            sources.map { (name, file) ->
                if (!file.isFile) throw ToolFailure("No such file: ${file.path}")
                val picture = readPicture(file)
                if (!masked && builder.hasColor(picture)) coloured += name
                name to if (masked) builder.toMask(picture) else picture
            }
        val packed =
            try {
                com.github.demandss.dayz_workbench_tool.assets.ImageSetBuilder.pack(
                    pictures,
                    (args.int("padding") ?: 2).coerceIn(0, 32),
                    (args.int("max_size") ?: 4096).coerceIn(64, 8192),
                )
            } catch (e: IllegalArgumentException) {
                throw ToolFailure(e.message.orEmpty())
            }
        val setName = assetName(args.string("name") ?: output.nameWithoutExtension)
        val atlasFile = java.io.File(output.parentFile, output.nameWithoutExtension + ".edds")
        output.parentFile?.mkdirs()
        atlasFile.writeBytes(EddsCodec.encode(packed.atlas, true))
        // The path the game reads: from the folder that holds the mod folder
        val texturePath = state.fontPathFor(output.parentFile ?: java.io.File("."), output.nameWithoutExtension) + ".edds"
        val set =
            com.github.demandss.dayz_workbench_tool.assets.ImageSetBuilder
                .imageSet(setName, texturePath, packed)
        output.writeText(
            com.github.demandss.dayz_workbench_tool.imageset.ImageSetFormat
                .serialize(set),
            Charsets.UTF_8,
        )
        state.refreshAssets(output.path)
        return buildJsonObject {
            put("imageset", output.path)
            put("atlas", atlasFile.path)
            put("atlas_size", "${packed.atlas.width}x${packed.atlas.height}")
            put("texture_path", texturePath)
            put("set", setName)
            put(
                "images",
                JsonArray(
                    packed.images.map { i ->
                        buildJsonObject {
                            put("name", i.name)
                            put("rect", "${i.x} ${i.y} ${i.width} ${i.height}")
                            put("image0", "set:$setName image:${i.name}")
                        }
                    },
                ),
            )
            if (coloured.isNotEmpty()) {
                put(
                    "warning",
                    "These pictures have colour: ${coloured.take(30).joinToString()}. Shapes and icons should be white to black on a " +
                        "transparent background, so that the widget colour tints them like a mask; pass mask=true to turn them grey.",
                )
            }
            put(
                "note",
                "Next: register_assets writes the imageset into config.cpp (CfgMods defs imageSets), so that the game loads it. " +
                    "An ImageWidget with transparent pixels needs mode blend and \"src alpha\" 1.",
            )
        }.toString()
    }

    /**
     * Makes a style of a widget type, or adds it to an existing `.styles` file. The slots are images of an imageset: [fill] for
     * all nine, [slots] by slot name, [states] for a state of its own (a string is one image for the whole state).
     */
    private fun createStyle(args: JsonObject): String {
        val file = java.io.File(args.string("file") ?: throw ToolFailure("file is required: the full path of the .styles"))
        if (!file.name.endsWith(".styles", ignoreCase = true)) throw ToolFailure("file must end with .styles")
        writable(file.path)
        val widget = (args.string("widget") ?: "ButtonWidget").removeSuffix("Class")
        val name = assetName(args.string("name") ?: throw ToolFailure("name is required"))
        val imageSet = args.string("imageset") ?: throw ToolFailure("imageset is required: the name of the imageset the images are from")
        val builder = com.github.demandss.dayz_workbench_tool.assets.StyleBuilder

        fun slotMap(element: JsonElement?): Map<String, String> =
            when (element) {
                is JsonPrimitive -> {
                    builder.SLOTS.associateWith { element.content }
                }

                is JsonObject -> {
                    element.entries.associate { (slot, image) ->
                        val key =
                            builder.SLOTS.firstOrNull { it.equals(slot, true) } ?: throw ToolFailure("Unknown slot $slot: ${builder.SLOTS}")
                        key to ((image as? JsonPrimitive)?.content ?: throw ToolFailure("The image of $slot must be a name"))
                    }
                }

                else -> {
                    emptyMap()
                }
            }
        val normal = slotMap(args["fill"]) + slotMap(args["slots"])
        val perState = LinkedHashMap<String, Map<String, String>>()
        if (normal.isNotEmpty()) perState["Normal"] = normal
        (args["states"] as? JsonObject)?.forEach { (state, value) ->
            val stateName =
                com.github.demandss.dayz_workbench_tool.assets.StyleBuilder
                    .defaultStates("ButtonWidget")
                    .firstOrNull { it.equals(state, true) } ?: state
            // A state without its own slots keeps the ones of Normal for the nine it does not name
            perState[stateName] = normal + slotMap(value)
        }
        if (perState.isEmpty()) throw ToolFailure("Give fill (one image for all slots), slots or states")
        val used = perState.values.flatMap { it.values }.toSet()
        // The imageset is looked for in the mod of the style first (a new one is not in the editor's index yet), then in the editor
        val inMod =
            state
                .modRoot(file.path)
                ?.walkTopDown()
                ?.onEnter { !it.name.startsWith(".") }
                ?.filter { it.isFile && it.extension.equals("imageset", true) }
                ?.mapNotNull { f ->
                    runCatching {
                        com.github.demandss.dayz_workbench_tool.imageset.ImageSetFormat
                            .parse(f.readText(Charsets.UTF_8))
                    }.getOrNull()
                }?.firstOrNull { it.name == imageSet }
        val known = (inMod ?: state.imageSetOf(imageSet, state.activeFile?.path))?.all?.map { it.name }?.toSet()
        val warnings = mutableListOf<String>()
        if (known == null) {
            warnings += "Imageset $imageSet is not known to the editor (create_imageset makes one): the style is written all the same"
        } else {
            val missing = used.filter { it !in known }
            if (missing.isNotEmpty()) {
                throw ToolFailure(
                    "Imageset $imageSet has no images ${missing.joinToString()}; it has ${known.take(30).joinToString()}",
                )
            }
        }
        val color =
            try {
                builder.colorNumber(args.string("color") ?: "4294967295")
            } catch (e: IllegalArgumentException) {
                throw ToolFailure(e.message.orEmpty())
            }
        val sheet =
            if (file.isFile) {
                com.github.demandss.dayz_workbench_tool.styles.StylesFormat.parse(
                    file.readText(Charsets.UTF_8),
                )
            } else {
                null
            }
        if (file.isFile && sheet == null) throw ToolFailure("${file.path} is not a .styles file")
        val existing = sheet?.stylesFor(widget)?.any { it.name == name } == true
        if (existing &&
            args.bool("replace") != true
        ) {
            throw ToolFailure("Style $name of $widget is in ${file.name}: pass replace=true to replace it")
        }
        val style = builder.style(name, imageSet, perState, widget, args.string("font").orEmpty(), color)
        val merged = builder.withStyle(sheet, widget, style)
        file.parentFile?.mkdirs()
        file.writeText(
            com.github.demandss.dayz_workbench_tool.styles.StylesFormat
                .serialize(merged),
            Charsets.UTF_8,
        )
        state.refreshAssets(file.path)
        return withWarnings(
            buildJsonObject {
                put("file", file.path)
                put("widget", widget)
                put("style", name)
                put("states", JsonArray(style.states.map { JsonPrimitive(it.name) }))
                put("use", "style $name")
                put("note", "Register the file with register_assets so that the game loads it (CfgMods defs widgetStyles).")
            },
            warnings,
        )
    }

    /**
     * Writes every `.imageset` and `.styles` file of a mod into its `config.cpp` (`CfgMods > defs > imageSets` and `widgetStyles`).
     * With apply false only the lines to put there are returned.
     */
    private fun registerAssets(args: JsonObject): String {
        val mod =
            args.string("mod")?.let { java.io.File(it) }
                ?: state.activeFile?.path?.let { state.modRoot(it) }
                ?: throw ToolFailure("mod is required: the folder of the mod (or open a layout of it)")
        if (!mod.isDirectory) throw ToolFailure("No such folder: ${mod.path}")
        writable(mod.path)
        val config =
            mod.listFiles { f -> f.isFile && f.name.equals("config.cpp", ignoreCase = true) }?.firstOrNull()
                ?: throw ToolFailure("${mod.path} has no config.cpp")
        val sets = mutableListOf<String>()
        val styles = mutableListOf<String>()
        mod
            .walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile }
            .forEach { f ->
                // The path the game reads: the mod folder first, forward slashes
                val relative = mod.name + "/" + f.relativeTo(mod).path.replace('\\', '/')
                when (f.extension.lowercase()) {
                    "imageset" -> sets += relative
                    "styles" -> styles += relative
                }
            }
        val allSets = sets.toList()
        val allStyles = styles.toList()
        val patcher = com.github.demandss.dayz_workbench_tool.assets.ConfigPatcher
        var text = config.readText(Charsets.UTF_8)
        val added = mutableListOf<String>()
        var failed = false
        // A config without any CfgMods gets a whole one for this mod
        if (patcher.hasNoMods(text) && (sets.isNotEmpty() || styles.isNotEmpty())) {
            val eol = if ("\r\n" in text) "\r\n" else "\n"
            text = text.trimEnd() + eol + patcher.newMods(mod.name, sets, styles, eol)
            added += sets + styles
            sets.clear()
            styles.clear()
        }
        for ((kind, paths) in listOf("imageSets" to sets, "widgetStyles" to styles)) {
            if (paths.isEmpty()) continue
            val outcome = patcher.addFiles(text, kind, paths)
            if (outcome == null) {
                failed = true
            } else {
                text = outcome.text
                added += outcome.added
            }
        }
        val apply = args.bool("apply") != false && !failed
        if (apply && added.isNotEmpty()) config.writeText(text, Charsets.UTF_8)
        return buildJsonObject {
            put("config", config.path)
            put("applied", apply && added.isNotEmpty())
            put("added", JsonArray(added.map { JsonPrimitive(it) }))
            put("imagesets", JsonArray(allSets.map { JsonPrimitive(it) }))
            put("styles", JsonArray(allStyles.map { JsonPrimitive(it) }))
            if (failed) {
                put(
                    "note",
                    "config.cpp has no class defs inside CfgMods: add the lists by hand (class imageSets / widgetStyles with files[] = {...})",
                )
            }
        }.toString()
    }

    private fun listFonts(args: JsonObject): String {
        val query = args.string("query")?.lowercase()
        val fonts =
            state
                .fontInfos(state.activeFile?.path)
                .filter { query == null || it.path.lowercase().contains(query) || it.origin.lowercase().contains(query) }
        return buildJsonObject {
            put(
                "note",
                "Put the path in quotes into the font property. A path with a size at its end is that size; without a size the nearest size of the family is used. No font of the game is monospaced.",
            )
            put("total", fonts.size)
            put(
                "fonts",
                JsonArray(
                    fonts.map { f ->
                        buildJsonObject {
                            put("font", f.path)
                            put("kind", f.kind)
                            put("size", f.size)
                            put("origin", f.origin)
                            put("glyphs", f.glyphs)
                            put("latin", f.latin)
                            put("cyrillic", f.cyrillic)
                        }
                    },
                ),
            )
        }.toString()
    }

    // ---- conversions

    /** `.png` to `.edds` and `.edds` to `.png`, chosen by the extension of the source. */
    private fun convertTexture(args: JsonObject): String {
        val source = java.io.File(args.string("source") ?: throw ToolFailure("source is required"))
        if (!source.isFile) throw ToolFailure("No such file: ${source.path}")
        val toEdds = !source.extension.equals("edds", ignoreCase = true)
        val target =
            args.string("output")?.let { java.io.File(it) }
                ?: java.io.File(source.parentFile, source.nameWithoutExtension + if (toEdds) ".edds" else ".png")
        writable(target.path)
        if (target.exists() && args.bool("overwrite") != true) throw ToolFailure("${target.path} exists: pass overwrite=true to replace it")
        target.parentFile?.mkdirs()
        val info =
            try {
                if (toEdds) {
                    val image = EddsCodec.decodePng(source.readBytes())
                    target.writeBytes(EddsCodec.encode(image, args.bool("mips") ?: true))
                    "${image.width}x${image.height}"
                } else {
                    val decoded = EddsCodec.decode(source)
                    javax.imageio.ImageIO.write(decoded.toBufferedImage(), "png", target)
                    "${decoded.width}x${decoded.height}, ${decoded.mipCount} mips, ${decoded.format}"
                }
            } catch (e: com.github.demandss.dayz_workbench_tool.texture.EddsException) {
                throw ToolFailure(e.message.orEmpty())
            }
        return buildJsonObject {
            put("output", target.path)
            put("direction", if (toEdds) "image to edds" else "edds to png")
            put("info", info)
        }.toString()
    }

    /**
     * Makes bitmap fonts of the game from a TrueType or OpenType file: one `.fnt` with its `.edds` atlas for every size. The files
     * go into [output_dir]; the answer gives the path to write into `font`.
     */
    private fun convertFont(args: JsonObject): String {
        val source = java.io.File(args.string("source") ?: throw ToolFailure("source is required (a .ttf or .otf file)"))
        if (!source.isFile) throw ToolFailure("No such file: ${source.path}")
        val outDir =
            java.io.File(args.string("output_dir") ?: throw ToolFailure("output_dir is required, e.g. the gui/fonts folder of a mod"))
        writable(outDir.path)
        val name = sanitizeFontName(args.string("name") ?: source.nameWithoutExtension)
        val sizes =
            (args["sizes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }?.takeIf { it.isNotEmpty() }
                ?: listOf(12, 14, 16, 22, 28, 48)
        if (sizes.any { it !in 6..200 }) throw ToolFailure("sizes are pixels from 6 to 200")
        val existing = sizes.filter { java.io.File(outDir, "$name$it.fnt").exists() }
        if (existing.isNotEmpty() && args.bool("overwrite") != true) {
            throw ToolFailure("${outDir.path} already has $name for the sizes $existing: pass overwrite=true to replace them")
        }
        val font =
            try {
                FontImporter.loadFont(source)
            } catch (e: Exception) {
                throw ToolFailure("Cannot read the font: ${e.message}")
            }
        val made =
            try {
                FontImporter.write(font, name, sizes, outDir, FontImporter.charset(args.string("charset")))
            } catch (e: IllegalArgumentException) {
                throw ToolFailure(e.message.orEmpty())
            }
        state.forgetFonts()
        val fontPath = state.fontPathFor(outDir, name)
        return buildJsonObject {
            put("font", fontPath)
            put("files", JsonArray(made.map { JsonPrimitive("${outDir.path}/${it.name}.fnt") }))
            put("sizes", JsonArray(made.map { JsonPrimitive(it.size) }))
            put("glyphs", made.first().glyphs)
            put(
                "note",
                "Write the font property as \"font\": a path with a size at its end is that size, without a size the nearest one is used. The font must be inside the mod that is packed.",
            )
        }.toString()
    }

    private fun sanitizeFontName(text: String): String =
        text
            .trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_')
            .ifEmpty { "CustomFont" }

    private fun listStyles(args: JsonObject): String {
        val widgetClass = args.string("widget_class") ?: throw ToolFailure("widget_class is required, e.g. PanelWidget")
        return state
            .vanillaStylesFor(widgetClass, state.activeFile?.path)
            .joinToString("\n") { it.name }
    }

    // ---- files

    private fun openLayout(args: JsonObject): String {
        val path = args.string("path") ?: throw ToolFailure("path is required")
        // A file of the game (inside a PBO) is only read; any other file has to be in the work drive or a mod, because opening
        // a layout connects the mod above it
        if (com.github.demandss.dayz_workbench_tool.pbo.PboFile
                .resolve(path) == null
        ) {
            writable(path)
        }
        val before = state.openFiles.size
        state.openFile(path)
        val doc = state.activeFile
        if (doc == null || (state.openFiles.size == before && !doc.path.equals(java.io.File(path).canonicalPath, true))) {
            throw ToolFailure("Could not open $path")
        }
        return "Opened ${doc.path}"
    }

    /** Opens any file the editor has a tab for: a layout, an `.imageset`, a `.styles`, a texture or a font. */
    private fun openFile(args: JsonObject): String {
        val path = args.string("path") ?: throw ToolFailure("path is required")
        if (!java.io.File(path).isFile) throw ToolFailure("No such file: $path")
        writable(path)
        state.openAny(path)
        return "Opened $path"
    }

    /**
     * The file for [path] when it lies inside the work drive or a connected mod, resolved with `canonicalFile` so that `..` and
     * links cannot lead out of them. A tool writes nowhere else: a program that reaches the server must not be able to change
     * files of the user outside the mods.
     */
    private fun writable(path: String): java.io.File {
        val canonical =
            runCatching { java.io.File(path).canonicalFile }.getOrElse { throw ToolFailure("Not a usable path: $path") }
        val roots = state.writableRoots()
        if (roots.none { canonical.toPath().startsWith(it.toPath()) }) {
            throw ToolFailure(
                "$path is outside the work drive and the connected mods, tools write only there" +
                    if (roots.isEmpty()) {
                        " (set the work drive in the preferences of the editor)"
                    } else {
                        ": ${roots.joinToString {
                            it.path
                        }}"
                    },
            )
        }
        return canonical
    }

    private fun createLayout(args: JsonObject): String {
        val path = args.string("path") ?: throw ToolFailure("path is required")
        if (!path.endsWith(".layout", ignoreCase = true)) throw ToolFailure("path must end with .layout")
        val file = writable(path)
        if (file.exists() && args.bool("overwrite") != true) throw ToolFailure("$path exists: open_layout opens it, or pass overwrite=true")
        file.parentFile?.mkdirs()
        // The watcher of the editor must not take this write for a change from outside (it offered a reload that dropped the widgets)
        state.markOwnWrite(path)
        val root = args.string("root_type")?.let { typeOf(it) } ?: WidgetType.FRAME
        val rootName = sanitizeWidgetName(args.string("root_name") ?: "Root")
        val width = args.int("width") ?: 1920
        val height = args.int("height") ?: 1080
        // A root of exact pixels, so that what is put into it keeps the same size on every screen
        file.writeText(
            "${root.className} $rootName {\n size $width $height\n hexactpos 1\n vexactpos 1\n hexactsize 1\n vexactsize 1\n}\n",
            Charsets.UTF_8,
        )
        return openLayout(buildJsonObject { put("path", path) })
    }

    private fun closeLayout(args: JsonObject): String {
        val index = args.int("index") ?: state.activeFileIndex
        if (index !in state.openFiles.indices) throw ToolFailure("No layout with that index")
        if (state.openFiles[index].isModified && args.bool("discard") != true) {
            throw ToolFailure("The layout has unsaved changes: call save_layout first, or pass discard=true to drop them")
        }
        state.closeFile(index)
        state.selectedIds.clear()
        return "Closed"
    }

    private fun saveLayout(): String {
        val doc = requireDoc()
        if (com.github.demandss.dayz_workbench_tool.pbo.PboFile
                .resolve(doc.path) == null
        ) {
            writable(doc.path)
        }
        state.saveActiveFile()
        return if (state.activeFile?.isModified == false) "Saved ${doc.path}" else throw ToolFailure("Not saved (game files are read-only)")
    }

    private fun undoRedo(
        undo: Boolean,
        args: JsonObject,
    ): String {
        val steps = (args.int("steps") ?: 1).coerceIn(1, 100)
        repeat(steps) { if (undo) state.undo(state.activeFileIndex) else state.redo(state.activeFileIndex) }
        return "Done"
    }

    // ---- editing

    private fun edit(
        label: String,
        change: (EditorWidgetNode) -> EditorWidgetNode,
    ) {
        val idx = state.activeFileIndex
        val doc = state.openFiles.getOrNull(idx) ?: throw ToolFailure("No layout is open")
        state.pushUndo(idx, doc.root, label)
        state.updateActiveRoot(change(doc.root))
    }

    /** The name of a property as the file writes it: a name with a space is quoted by the writer, so quotes given here are dropped. */
    private fun cleanKey(key: String): String = key.trim().trim('"').trim()

    /** The names among [keys] that the editor does not know for [node], as a warning for the answer of a tool. */
    private fun warningsFor(
        node: EditorWidgetNode,
        keys: Collection<String>,
    ): List<String> {
        val known = knownPropertyKeys(node.node.type, node.node.originalClassName)
        return keys
            .filter { it.lowercase() !in known }
            .map {
                "Unknown property '$it' for ${node.node.type.className.removeSuffix(
                    "Class",
                )}: it is written to the file as it is (a typo?)"
            }
    }

    /** The properties that hold a text or a path, which the file writes in quotes. */
    private val quotedKeys = setOf("text", "font", "image0", "imagetexture", "scriptclass", "items", "layout", "caption")

    private fun unquotedWarnings(values: Map<String, String?>): List<String> =
        values
            .filter { (key, value) -> key.lowercase() in quotedKeys && !value.isNullOrEmpty() && !value.trim().startsWith("\"") }
            .map { (key, value) ->
                "Property '$key' is a text and needs quotes: write \"$value\" (without them the game cannot read the file)"
            }

    /** Workbench stops reading the layout after a widget whose text size is not a whole number. */
    private fun numberWarnings(values: Map<String, String?>): List<String> {
        val size = values["exact text size"]?.trim() ?: return emptyList()
        if (size.isEmpty() || size.toIntOrNull() != null) return emptyList()
        val whole = size.toDoubleOrNull()?.let { Math.round(it).toString() } ?: "an integer"
        return listOf("exact text size $size is not a whole number: Workbench stops reading the layout after this widget, use $whole")
    }

    /** A font name the game cannot find is drawn in its system font, a small pixel font without Cyrillic. */
    private fun fontWarnings(values: Map<String, String?>): List<String> {
        val value = values["font"]?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (state.fontExists(value, state.activeFile?.path)) return emptyList()
        return listOf(
            "Font ${value.trim()} is not among list_fonts (a family needs its .xml descriptor): the game draws its small system font instead",
        )
    }

    /** A style the game and the mods do not have paints nothing in Workbench. */
    private fun styleWarnings(node: EditorWidgetNode): List<String> {
        val name =
            node.node.properties["style"]
                ?.trim()
                ?.trim('"') ?: return emptyList()
        if (name.isEmpty() || state.styleExists(node, state.activeFile?.path)) return emptyList()
        val className =
            node.node.type.className
                .removeSuffix("Class")
        return listOf(
            "Style $name is not in list_styles for $className: Workbench draws nothing for a style it does not have " +
                "(flat fills: rover_sim_colorable on a panel, Colorable on a button)",
        )
    }

    private fun withWarnings(
        json: JsonObject,
        warnings: List<String>,
    ): String =
        if (warnings.isEmpty()) {
            json.toString()
        } else {
            buildJsonObject {
                json.forEach { (k, v) -> put(k, v) }
                put("warnings", JsonArray(warnings.map { JsonPrimitive(it) }))
            }.toString()
        }

    private fun setProperties(args: JsonObject): String {
        val node = requireNode(args)
        val values = args["properties"] as? JsonObject ?: throw ToolFailure("properties must be an object of name: value")
        val cleaned = values.entries.associate { (key, value) -> cleanKey(key) to value }
        edit("MCP: ${cleaned.keys.joinToString(", ")}") { root ->
            root.updateNode(node.id) { n ->
                val props = LinkedHashMap(n.node.properties)
                for ((key, value) in cleaned) {
                    if (value is JsonNull) props.remove(key) else props[key] = (value as? JsonPrimitive)?.content.orEmpty()
                }
                n.copy(node = n.node.copy(properties = props))
            }
        }
        val after = requireNode(buildJsonObject { put("id", node.id.toString()) })
        val given = cleaned.filterValues { it !is JsonNull }
        val texts = given.mapValues { (_, v) -> (v as? JsonPrimitive)?.content }
        return withWarnings(
            widgetJson(after, withRect = true),
            warningsFor(after, given.keys) + unquotedWarnings(texts) + fontWarnings(texts) + styleWarnings(after) + numberWarnings(texts),
        )
    }

    /** The same properties for many widgets in one step of the history: the widgets are given by names, by a regular expression or both. */
    private fun setPropertiesMany(args: JsonObject): String {
        val doc = requireDoc()
        val values = args["properties"] as? JsonObject ?: throw ToolFailure("properties must be an object of name: value")
        val names = (args["names"] as? JsonArray)?.map { (it as JsonPrimitive).content }?.toSet().orEmpty()
        val regex =
            args.string("name_regex")?.let { text ->
                runCatching { Regex(text) }.getOrElse { throw ToolFailure("name_regex is not a regular expression: ${it.message}") }
            }
        if (names.isEmpty() && regex == null) throw ToolFailure("Give names (a list) or name_regex")
        val matched = mutableListOf<EditorWidgetNode>()

        fun visit(n: EditorWidgetNode) {
            if (n.node.name in names || regex?.containsMatchIn(n.node.name) == true) matched += n
            n.children.forEach(::visit)
        }
        visit(doc.root)
        if (matched.isEmpty()) throw ToolFailure("No widget has such a name")
        val cleaned = values.entries.associate { (key, value) -> cleanKey(key) to value }
        edit("MCP: ${cleaned.keys.joinToString(", ")} on ${matched.size} widgets") { root ->
            var out = root
            for (m in matched) {
                out =
                    out.updateNode(m.id) { n ->
                        val props = LinkedHashMap(n.node.properties)
                        for ((key, value) in cleaned) {
                            if (value is JsonNull) props.remove(key) else props[key] = (value as? JsonPrimitive)?.content.orEmpty()
                        }
                        n.copy(node = n.node.copy(properties = props))
                    }
            }
            out
        }
        val given = cleaned.filterValues { it !is JsonNull }
        val texts = given.mapValues { (_, v) -> (v as? JsonPrimitive)?.content }
        val first = requireNode(buildJsonObject { put("id", matched.first().id.toString()) })
        return withWarnings(
            buildJsonObject {
                put("changed", matched.size)
                put("widgets", JsonArray(matched.take(40).map { JsonPrimitive(it.node.name) }))
            },
            warningsFor(first, given.keys) + unquotedWarnings(texts) + fontWarnings(texts) + styleWarnings(first) + numberWarnings(texts),
        )
    }

    private fun typeOf(text: String): WidgetType {
        val clean = text.trim()
        return WidgetType.entries.firstOrNull {
            it.className.equals(clean, true) ||
                it.className.removeSuffix("Class").equals(clean, true) ||
                it.className.removeSuffix("WidgetClass").equals(clean, true) ||
                it.name.equals(clean, true)
        } ?: throw ToolFailure("Unknown widget type: $text")
    }

    private fun findIn(
        root: EditorWidgetNode,
        args: JsonObject,
        prefix: String,
    ): EditorWidgetNode? {
        args.string(prefix + "id")?.let { raw ->
            val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ToolFailure("Not a widget id: $raw")
            return root.findNode(id)
        }
        args.string(prefix + "path")?.let { path ->
            var current = root
            val parts = path.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts.first() != current.node.name) return null
            for (part in parts.drop(1)) current = current.children.firstOrNull { it.node.name == part } ?: return null
            return current
        }
        args.string(prefix + "name")?.let { name ->
            fun search(n: EditorWidgetNode): EditorWidgetNode? = if (n.node.name == name) n else n.children.firstNotNullOfOrNull(::search)
            return search(root)
        }
        return root
    }

    /** One widget as `add_widget` builds it: a free name, 100 by 50 pixels at 0 0 unless the properties say otherwise. */
    private fun buildWidget(
        spec: JsonObject,
        taken: MutableSet<String>,
    ): EditorWidgetNode {
        val type = typeOf(spec.string("type") ?: throw ToolFailure("type is required, e.g. PanelWidget"))
        val wanted = sanitizeWidgetName(spec.string("name") ?: type.className.removeSuffix("WidgetClass"))
        var name = wanted
        var n = 1
        while (name in taken) name = wanted + (++n)
        taken += name
        val props =
            linkedMapOf(
                "position" to "0 0",
                "size" to "100 50",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "hexactsize" to "1",
                "vexactsize" to "1",
            )
        (spec["properties"] as? JsonObject)?.forEach { (k, v) -> props[cleanKey(k)] = (v as? JsonPrimitive)?.content.orEmpty() }
        return EditorWidgetNode(node = WidgetNode(type = type, originalClassName = type.className, name = name, properties = props))
    }

    private fun addWidget(args: JsonObject): String {
        val doc = requireDoc()
        val parent =
            if (args.string("parent_id") != null || args.string("parent_name") != null || args.string("parent_path") != null) {
                requireNode(args, "parent_")
            } else {
                doc.root
            }
        val created = buildWidget(args, doc.root.allNames().toMutableSet())
        val index = args.int("index")
        edit("MCP: add ${created.node.name}") { root ->
            root.updateNode(parent.id) { p ->
                val list = p.children.toMutableList()
                list.add(index?.coerceIn(0, list.size) ?: list.size, created)
                p.copy(children = list)
            }
        }
        state.selectedIds.clear()
        state.selectedIds.add(created.id)
        return withWarnings(
            widgetJson(created, withRect = false),
            warningsFor(created, created.node.properties.keys) + unquotedWarnings(created.node.properties) +
                fontWarnings(created.node.properties) + styleWarnings(created) +
                numberWarnings(created.node.properties),
        )
    }

    /** Many widgets in one call and one step of the history. A widget may name a parent that an earlier one of the list created. */
    private fun addWidgets(args: JsonObject): String {
        val doc = requireDoc()
        val specs = args["widgets"] as? JsonArray ?: throw ToolFailure("widgets must be an array of {type, name, parent_name, properties}")
        if (specs.isEmpty()) throw ToolFailure("widgets is empty")
        val taken = doc.root.allNames().toMutableSet()
        var root = doc.root
        val created = mutableListOf<EditorWidgetNode>()
        val warnings = mutableListOf<String>()
        specs.forEachIndexed { i, raw ->
            val spec = raw as? JsonObject ?: throw ToolFailure("widgets[$i] is not an object")
            try {
                val hasParent = listOf("parent_id", "parent_name", "parent_path").any { spec.string(it) != null }
                val parent = if (hasParent) findIn(root, spec, "parent_") else root
                parent ?: throw ToolFailure("parent not found")
                val widget = buildWidget(spec, taken)
                root =
                    root.updateNode(parent.id) { p ->
                        val list = p.children.toMutableList()
                        list.add(spec.int("index")?.coerceIn(0, list.size) ?: list.size, widget)
                        p.copy(children = list)
                    }
                created += widget
                warnings +=
                    (
                        warningsFor(
                            widget,
                            widget.node.properties.keys,
                        ) + unquotedWarnings(widget.node.properties) + fontWarnings(widget.node.properties) +
                            styleWarnings(widget) + numberWarnings(widget.node.properties)
                    ).map { "${widget.node.name}: $it" }
            } catch (e: ToolFailure) {
                throw ToolFailure("widgets[$i]: ${e.message}. Nothing was added")
            }
        }
        val idx = state.activeFileIndex
        state.pushUndo(idx, doc.root, "MCP: add ${created.size} widgets")
        state.updateActiveRoot(root)
        state.selectedIds.clear()
        return withWarnings(
            buildJsonObject {
                put("added", created.size)
                put(
                    "widgets",
                    JsonArray(
                        created.map { w ->
                            buildJsonObject {
                                put("id", w.id.toString())
                                put("name", w.node.name)
                            }
                        },
                    ),
                )
            },
            warnings,
        )
    }

    /**
     * Copies a widget with everything under it. Every name in the copy gets [name_prefix] before it and [name_suffix] after it
     * (`{n}` is the number of the copy), and the copy may be shifted by [offset] pixels, which grows with each of [count] copies.
     */
    private fun copyWidget(args: JsonObject): String {
        val doc = requireDoc()
        val source = requireNode(args)
        if (source.id == doc.root.id) throw ToolFailure("The root widget cannot be copied")
        val count = (args.int("count") ?: 1).coerceIn(1, 200)
        val prefix = args.string("name_prefix").orEmpty()
        val find = args.string("name_find")
        val replaceWith = args.string("name_replace")
        // With name_find the part is replaced in every name and nothing is added; otherwise the suffix is added
        val suffix = if (find != null) "" else args.string("name_suffix") ?: "_{n}"
        val firstNumber = args.int("number_start") ?: 1
        val shift =
            args
                .string("offset")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.size == 2 }
        val hasTarget = listOf("parent_id", "parent_name", "parent_path").any { args.string(it) != null }
        var root = doc.root
        val targetId =
            if (hasTarget) {
                requireNode(
                    args,
                    "parent_",
                ).id
            } else {
                (doc.root.pathTo(source.id)?.let { it.getOrNull(it.size - 2)?.id })
            }
        if (targetId == null) throw ToolFailure("The widget has no parent")
        val taken = doc.root.allNames().toMutableSet()
        val made = mutableListOf<EditorWidgetNode>()

        fun renamed(
            n: EditorWidgetNode,
            copyNumber: Int,
        ): EditorWidgetNode {
            val number = copyNumber + firstNumber - 1

            fun fill(text: String) = text.replace("{nn}", "%02d".format(number)).replace("{n}", number.toString())
            val base = if (find != null) n.node.name.replace(find, fill(replaceWith ?: "")) else n.node.name
            val wanted = sanitizeWidgetName(prefix + base + fill(suffix))
            var name = wanted
            var k = 1
            while (name in taken) name = wanted + (++k)
            taken += name
            return n.copy(node = n.node.copy(name = name), children = n.children.map { renamed(it, copyNumber) }.toMutableList())
        }

        for (copyNumber in 1..count) {
            var copy = renamed(source.copiedWithNewIds(), copyNumber)
            if (shift != null) {
                val props = LinkedHashMap(copy.node.properties)
                val parts = (props["position"] ?: "0 0").trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                if (parts.size == 2) {
                    fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
                    props["position"] = "${fmt(parts[0] + shift[0] * copyNumber)} ${fmt(parts[1] + shift[1] * copyNumber)}"
                    copy = copy.copy(node = copy.node.copy(properties = props))
                }
            }
            root =
                root.updateNode(targetId) { p ->
                    val list = p.children.toMutableList()
                    // Next to the original when it stays in its parent, otherwise at the end of the new parent
                    val at = list.indexOfFirst { it.id == source.id }
                    list.add(if (at >= 0) at + copyNumber else list.size, copy)
                    p.copy(children = list)
                }
            made += copy
        }
        state.pushUndo(state.activeFileIndex, doc.root, "MCP: copy ${source.node.name}")
        state.updateActiveRoot(root)
        state.selectedIds.clear()
        state.selectedIds.add(made.last().id)
        return buildJsonObject {
            put("copies", made.size)
            put(
                "widgets",
                JsonArray(
                    made.map { w ->
                        buildJsonObject {
                            put("id", w.id.toString())
                            put("name", w.node.name)
                        }
                    },
                ),
            )
        }.toString()
    }

    private fun deleteWidget(args: JsonObject): String {
        val doc = requireDoc()
        val node = requireNode(args)
        if (node.id == doc.root.id) throw ToolFailure("The root widget cannot be deleted")
        edit("MCP: delete ${node.node.name}") { it.removeNode(node.id) }
        state.selectedIds.remove(node.id)
        return "Deleted ${node.node.name}"
    }

    private fun renameWidget(args: JsonObject): String {
        val node = requireNode(args)
        val newName = sanitizeWidgetName(args.string("new_name") ?: throw ToolFailure("new_name is required"))
        edit("MCP: rename") { root -> root.updateNode(node.id) { it.copy(node = it.node.copy(name = newName)) } }
        return "Renamed to $newName"
    }

    private fun moveWidget(args: JsonObject): String {
        val node = requireNode(args)
        val parent = requireNode(args, "new_parent_")
        state.reparentNode(node.id, parent.id)
        return "Moved ${node.node.name} into ${parent.node.name}"
    }

    private fun selectWidgets(args: JsonObject): String {
        val doc = requireDoc()
        val ids = args["ids"] as? JsonArray ?: throw ToolFailure("ids must be an array of widget ids")
        state.selectedIds.clear()
        ids.forEach { raw ->
            val id = runCatching { UUID.fromString((raw as JsonPrimitive).content) }.getOrNull() ?: return@forEach
            if (doc.root.findNode(id) != null) state.selectedIds.add(id)
        }
        return "Selected ${state.selectedIds.size}"
    }

    private fun replaceLayoutText(args: JsonObject): String {
        requireDoc()
        val text =
            args.string("text")
                ?: args.string("path")?.let { p ->
                    runCatching { java.io.File(p).readText(Charsets.UTF_8) }.getOrElse { throw ToolFailure("Cannot read $p") }
                }
                ?: throw ToolFailure("text or path is required")
        val parsed = runCatching { LayoutParser.parse(text) }.getOrElse { throw ToolFailure("Not a layout: ${it.message}") }
        val idx = state.activeFileIndex
        val doc = state.openFiles[idx]
        state.pushUndo(idx, doc.root, "MCP: replace layout")
        state.updateActiveRoot(parsed.toEditorNode())
        state.selectedIds.clear()
        val root = state.openFiles[idx].root
        val warnings = mutableListOf<String>()
        var count = 0

        fun visit(w: EditorWidgetNode) {
            count++
            val props = w.node.properties
            warnings +=
                (warningsFor(w, props.keys) + unquotedWarnings(props) + fontWarnings(props) + styleWarnings(w) + numberWarnings(props))
                    .map { "${w.node.name}: $it" }
            w.children.forEach(::visit)
        }
        visit(root)
        return withWarnings(
            buildJsonObject {
                put("replaced", true)
                put("widgets", count)
            },
            warnings.take(40) + if (warnings.size > 40) listOf("... and ${warnings.size - 40} more warnings") else emptyList(),
        )
    }

    private fun countWidgets(n: EditorWidgetNode): Int = 1 + n.children.sumOf(::countWidgets)

    /** The width and height a text takes with a font and a size, so that no screenshot is needed to fit it to a reference. */
    private fun measureTextTool(args: JsonObject): String {
        val text = args.string("text") ?: throw ToolFailure("text is required")
        val font = args.string("font") ?: "gui/fonts/MetronBook"
        val size = args.int("size") ?: throw ToolFailure("size is required (the exact text size, a whole number)")
        val wrap = args.bool("wrap") == true
        val bold = args.bool("bold") == true
        val boxWidth = args.int("width")?.toFloat() ?: 100000f
        val props =
            linkedMapOf(
                "position" to "0 0",
                "size" to "$boxWidth ${size * 2}",
                "text" to "\"$text\"",
                "font" to "\"$font\"",
                "exact text" to "1",
                "exact text size" to size.toString(),
            )
        if (wrap) props["wrap"] = "1"
        if (bold) props["bold text"] = "1"
        val node =
            EditorWidgetNode(
                node = WidgetNode(type = WidgetType.TEXT, originalClassName = WidgetType.TEXT.className, name = "m", properties = props),
            )
        val path = onMain { state.activeFile?.path }

        // A font loads in the background: the first answer is null, so it is asked again for up to 5 seconds
        fun loaded(px: Float) =
            (0 until 25).firstNotNullOfOrNull { i ->
                onMain { state.gameFontFor(font, path, px) } ?: run {
                    Thread.sleep(200)
                    null
                }
            }
        val found = loaded(size.toFloat())
        val box =
            measureText(
                node,
                { px -> found.takeIf { px == size.toFloat() } ?: loaded(px) },
                boxWidth,
                size * 2f,
                font,
            )
        return buildJsonObject {
            put("width", Math.round(box.width))
            put("height", Math.round(box.height))
            put("lines", if (size > 0) Math.round(box.height / size) else 1)
            put("font_found", found != null)
            if (found == null) put("note", "The font could not be loaded: the number is a guess (about half of the size per letter)")
        }.toString()
    }

    private fun setView(args: JsonObject): String {
        args.bool("live_preview")?.let { state.livePreview = it }
        args.bool("workbench_130_colors")?.let { state.workbenchColors = it }
        args.bool("show_grid")?.let { state.showGrid = it }
        args.bool("show_widget_names")?.let { state.showWidgetNames = it }
        args.bool("snap_to_grid")?.let { state.snapToGrid = it }
        args.int("grid_size")?.let { state.gridSize = it.coerceIn(1, 512) }
        args.string("resolution")?.let { text ->
            val clean = text.lowercase().replace('×', 'x').replace(" ", "")
            state.selectedResolution =
                Resolution.entries.firstOrNull { it.label.lowercase().replace('×', 'x') == clean || it.name.lowercase() == clean }
                    ?: throw ToolFailure("Unknown resolution: $text (e.g. 1920x1080 or FitToWindow)")
        }
        if (args.bool("fit") == true) state.centerView()
        if (args.bool("actual_size") == true) state.zoomActual()
        args.string("zoom")?.toFloatOrNull()?.let { state.zoom = it.coerceIn(0.1f, 8f) }
        return status().toString()
    }

    // ---- screenshot

    /**
     * A picture of the canvas (or of the whole window). [mode] "editor" is the view as it is, "preview" the Live Preview as the
     * game draws it and "preview_workbench" the Live Preview as the Workbench of 1.30 draws it. The view is put back afterwards.
     */
    private fun screenshot(args: JsonObject): JsonElement {
        val mode = args.string("mode") ?: "editor"
        val area = args.string("area") ?: "canvas"
        val screenOnly = area == "screen"
        val screenWidth = if (state.selectedResolution == Resolution.FIT) state.screenSize.width.toInt() else state.selectedResolution.width
        val maxWidth = (args.int("max_width") ?: if (screenOnly) screenWidth else 1600).coerceIn(200, 4000)
        val saved = onMain { Triple(state.livePreview, state.workbenchColors, state.zoom to state.panOffset) }
        try {
            onMain {
                when (mode) {
                    "preview" -> {
                        state.livePreview = true
                        state.workbenchColors = false
                    }

                    "preview_workbench" -> {
                        state.livePreview = true
                        state.workbenchColors = true
                    }
                }
                if (args.bool("fit") != false) {
                    state.centerView()
                } else if (args.bool("actual_size") == true) {
                    state.zoomActual()
                }
            }
            // The frame with the new view has to be drawn before it is taken
            // A big layout needs longer to be drawn (600 ms for a small one, 3 ms more for each widget)
            val widgetCount = onMain { state.activeFile?.root?.let { countWidgets(it) } ?: 0 }
            Thread.sleep((600 + widgetCount * 3L).coerceAtMost(4000L))
            val image =
                onMain { capture(if (screenOnly) "window" else area) } ?: return textResult("The window cannot be captured", isError = true)
            // The screen of the layout alone is scaled to its own pixels, so that it can be compared with a reference one to one
            if (screenOnly) {
                val screenHeight =
                    if (state.selectedResolution == Resolution.FIT) state.screenSize.height.toInt() else state.selectedResolution.height
                val exact = BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB)
                val g = exact.createGraphics()
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                // The corner of the screen is at a fraction of a pixel in the window: the whole window is moved and scaled by
                // the exact amounts, so that a layout pixel does not land half a pixel off
                val bounds = state.canvasBoundsInWindow
                val scale = 1.0 / state.zoom.toDouble()
                val left = (bounds.left + state.panOffset.x).toDouble()
                val top = (bounds.top + state.panOffset.y).toDouble()
                g.drawImage(image, java.awt.geom.AffineTransform(scale, 0.0, 0.0, scale, -left * scale, -top * scale), null)
                g.dispose()
                return imageResult(
                    exact,
                    "${exact.width}x${exact.height}, the screen of the layout, mode $mode, resolution ${state.selectedResolution.label}",
                )
            }
            val scaled = if (image.width > maxWidth) scale(image, maxWidth) else image
            val out = ByteArrayOutputStream()
            ImageIO.write(scaled, "png", out)
            return buildJsonObject {
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "image")
                                put("data", Base64.getEncoder().encodeToString(out.toByteArray()))
                                put("mimeType", "image/png")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", "${scaled.width}x${scaled.height}, mode $mode, resolution ${state.selectedResolution.label}")
                            },
                        )
                    },
                )
            }
        } finally {
            onMain {
                state.livePreview = saved.first
                state.workbenchColors = saved.second
                state.zoom = saved.third.first
                state.panOffset = saved.third.second
            }
        }
    }

    private fun scale(
        image: BufferedImage,
        width: Int,
    ): BufferedImage {
        val height = (image.height * width.toDouble() / image.width).toInt().coerceAtLeast(1)
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return out
    }

    private fun imageResult(
        image: BufferedImage,
        caption: String,
    ): JsonObject {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "image")
                            put("data", Base64.getEncoder().encodeToString(out.toByteArray()))
                            put("mimeType", "image/png")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", caption)
                        },
                    )
                },
            )
        }
    }

    private fun capture(area: String): BufferedImage? {
        val window = state.hostWindow ?: return null
        val layer = findLayer(window) ?: return null
        val full = layer.screenshot()?.asComposeImageBitmap()?.toAwtImage() ?: return null
        if (area == "window") return full
        if (area == "screen") {
            // The screen of the layout inside the canvas: its corner is the pan, its size the resolution times the zoom
            val b = state.canvasBoundsInWindow
            val w = if (state.selectedResolution == Resolution.FIT) state.screenSize.width else state.selectedResolution.width.toFloat()
            val h = if (state.selectedResolution == Resolution.FIT) state.screenSize.height else state.selectedResolution.height.toFloat()
            val x = (b.left + state.panOffset.x).toInt().coerceIn(0, full.width - 1)
            val y = (b.top + state.panOffset.y).toInt().coerceIn(0, full.height - 1)
            val cw = (w * state.zoom).toInt().coerceIn(1, full.width - x)
            val ch = (h * state.zoom).toInt().coerceIn(1, full.height - y)
            return full.getSubimage(x, y, cw, ch)
        }
        val b = state.canvasBoundsInWindow
        val x = b.left.toInt().coerceIn(0, full.width - 1)
        val y = b.top.toInt().coerceIn(0, full.height - 1)
        val w = b.width.toInt().coerceIn(1, full.width - x)
        val h = b.height.toInt().coerceIn(1, full.height - y)
        return full.getSubimage(x, y, w, h)
    }

    private fun findLayer(c: Component): SkiaLayer? {
        if (c is SkiaLayer) return c
        if (c is Container) for (child in c.components) findLayer(child)?.let { return it }
        return null
    }

    companion object {
        private fun schema(
            properties: Map<String, Pair<String, String>> = emptyMap(),
            required: List<String> = emptyList(),
        ): JsonObject =
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        properties.forEach { (name, spec) ->
                            put(
                                name,
                                buildJsonObject {
                                    put("type", spec.first)
                                    put("description", spec.second)
                                },
                            )
                        }
                    },
                )
                if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
            }

        private fun tool(
            name: String,
            description: String,
            properties: Map<String, Pair<String, String>> = emptyMap(),
            required: List<String> = emptyList(),
        ): JsonObject =
            buildJsonObject {
                put("name", name)
                put("description", description)
                put("inputSchema", schema(properties, required))
                // A client can let the tools that only look run without asking each time
                put("annotations", buildJsonObject { put("readOnlyHint", name in READ_ONLY_TOOLS) })
            }

        /** The tools that change nothing in the editor, on disk or in the view. */
        private val READ_ONLY_TOOLS =
            setOf(
                "editor_status",
                "list_layouts",
                "get_tree",
                "get_widget",
                "find_widgets",
                "get_layout_text",
                "get_script_template",
                "get_problems",
                "screenshot",
                "list_icons",
                "list_styles",
                "list_imagesets",
                "list_strings",
                "list_fonts",
                "measure_text",
            )

        private val WIDGET_REF =
            mapOf(
                "id" to ("string" to "Widget id from get_tree"),
                "name" to ("string" to "Widget name (the first one with it)"),
                "path" to ("string" to "Path of names from the root, e.g. rootFrame/Header/CloseImg"),
            )

        fun definitions(): List<JsonObject> =
            listOf(
                tool(
                    "editor_status",
                    "Open layouts, the selection, the view flags, the zoom and the resolution, and where things are: work_drive, game_folder and mod_folders.",
                ),
                tool(
                    "list_layouts",
                    "Lists the .layout files of the connected mods (mod_folders of editor_status), or of a folder inside the work drive or a mod.",
                    mapOf(
                        "query" to ("string" to "Words to look for in the path, e.g. inventory"),
                        "folder" to ("string" to "A folder to look in instead of the connected mods"),
                        "limit" to ("integer" to "At most this many, 100 by default"),
                    ),
                ),
                tool(
                    "open_layout",
                    "Opens a .layout file in the editor and makes it the active one.",
                    mapOf("path" to ("string" to "Full path of the file")),
                    listOf("path"),
                ),
                tool(
                    "create_layout",
                    "Creates a new .layout file with an empty root (a Frame of 1920x1080 pixels by default) and opens it. Missing folders are made.",
                    mapOf(
                        "path" to ("string" to "Full path of the new file"),
                        "width" to ("integer" to "Width of the root in pixels, 1920 by default"),
                        "height" to ("integer" to "Height of the root in pixels, 1080 by default"),
                        "root_type" to ("string" to "Type of the root, FrameWidget by default"),
                        "root_name" to ("string" to "Name of the root, Root by default"),
                        "overwrite" to ("boolean" to "Replace a file that exists, false by default"),
                    ),
                    listOf("path"),
                ),
                tool(
                    "close_layout",
                    "Closes a layout tab (the active one when index is not given). A layout with unsaved changes is not closed unless discard is true.",
                    mapOf(
                        "index" to ("integer" to "Tab index from editor_status"),
                        "discard" to ("boolean" to "Close even when there are unsaved changes, false by default"),
                    ),
                ),
                tool("save_layout", "Saves the active layout to its file."),
                tool("undo", "Undoes the last edits of the active layout.", mapOf("steps" to ("integer" to "How many, 1 by default"))),
                tool("redo", "Redoes edits that were undone.", mapOf("steps" to ("integer" to "How many, 1 by default"))),
                tool(
                    "get_tree",
                    "The widget tree of the active layout with ids, types, properties and the computed rectangle (x, y, w, h in pixels of the chosen resolution).",
                    WIDGET_REF +
                        mapOf(
                            "max_depth" to ("integer" to "How deep to go below the start widget"),
                            "include_properties" to ("boolean" to "false leaves the properties out, true by default"),
                        ),
                ),
                tool("get_widget", "One widget with all its properties and its computed rectangle.", WIDGET_REF),
                tool(
                    "find_widgets",
                    "Widgets whose name or property values contain the query, optionally of one type.",
                    mapOf(
                        "query" to ("string" to "Text to look for"),
                        "type" to ("string" to "Widget type, e.g. ImageWidget"),
                        "limit" to ("integer" to "At most this many, 50 by default"),
                    ),
                ),
                tool(
                    "set_properties",
                    "Sets properties of a widget (undoable). A value of null removes the property. Values are written as they stand in the .layout file, with quotes for text, e.g. image0 = \"set:Name image:Img\". A name with a space is given without quotes (text color); the file writer quotes it. A name the editor does not know is still written, with a warning in the answer.",
                    WIDGET_REF + mapOf("properties" to ("object" to "Property name to value")),
                    listOf("properties"),
                ),
                tool(
                    "set_properties_many",
                    "Sets the same properties on many widgets in one undoable step: the widgets are given by a list of names, by name_regex (a regular expression found anywhere in the name, e.g. \"^ListRow\\d_Bg$\") or both. A value of null removes the property. Use it for the states of repeated elements instead of one set_properties call for each.",
                    mapOf(
                        "names" to ("array" to "Names of the widgets"),
                        "name_regex" to ("string" to "Regular expression for the names"),
                        "properties" to ("object" to "Property name to value, as in set_properties"),
                    ),
                    listOf("properties"),
                ),
                tool(
                    "add_widget",
                    "Adds a widget inside a parent (the root when no parent is given). Position and size are pixels unless hexactpos/vexactpos/hexactsize/vexactsize are 0: size \"1 1\" is one pixel, not the whole parent.",
                    mapOf(
                        "type" to ("string" to "Widget type, e.g. PanelWidget, TextWidget, ImageWidget"),
                        "name" to ("string" to "Name of the new widget"),
                        "parent_id" to ("string" to "Parent widget id"),
                        "parent_name" to ("string" to "Parent widget name"),
                        "parent_path" to ("string" to "Parent widget path"),
                        "properties" to ("object" to "Properties of the new widget; position and size default to 100x50 pixels at 0 0"),
                        "index" to ("integer" to "Place among the parent's children (the end by default)"),
                    ),
                    listOf("type"),
                ),
                tool(
                    "add_widgets",
                    "Adds many widgets in one call and one step of the history. Each item is what add_widget takes; an item may name a parent that an earlier item created. If one item is wrong nothing is added.",
                    mapOf("widgets" to ("array" to "Items of {type, name, parent_name or parent_id or parent_path, properties, index}")),
                    listOf("widgets"),
                ),
                tool(
                    "copy_widget",
                    "Copies a widget with everything under it, count times. Names in the copy get name_prefix before and name_suffix after them, so they stay unique and meaningful.",
                    WIDGET_REF +
                        mapOf(
                            "count" to ("integer" to "How many copies, 1 by default"),
                            "name_prefix" to ("string" to "Put before every name in the copy, empty by default"),
                            "name_suffix" to ("string" to "Put after every name; {n} is the number of the copy, _{n} by default"),
                            "name_find" to
                                ("string" to "Instead of a suffix: this part of every name is replaced, e.g. Card_01 becomes Card_02"),
                            "name_replace" to
                                (
                                    "string" to
                                        "What replaces name_find; {n} is the number of the copy, {nn} the same with two digits, e.g. Card_{nn}"
                                ),
                            "number_start" to
                                ("integer" to "The number of the first copy for {n}, 1 by default (2 to make Card_02 from Card_01)"),
                            "offset" to
                                (
                                    "string" to
                                        "Pixels added to the position of the copy for each copy, e.g. \"124 0\" for a column to the right"
                                ),
                            "parent_id" to ("string" to "Put the copies into this parent instead of the parent of the original"),
                            "parent_name" to ("string" to "Same, by name"),
                            "parent_path" to ("string" to "Same, by path"),
                        ),
                ),
                tool("delete_widget", "Deletes a widget with its children.", WIDGET_REF),
                tool(
                    "rename_widget",
                    "Renames a widget.",
                    WIDGET_REF + mapOf("new_name" to ("string" to "The new name")),
                    listOf("new_name"),
                ),
                tool(
                    "move_widget",
                    "Moves a widget with its children into another parent.",
                    WIDGET_REF +
                        mapOf(
                            "new_parent_id" to ("string" to "New parent id"),
                            "new_parent_name" to ("string" to "New parent name"),
                            "new_parent_path" to ("string" to "New parent path"),
                        ),
                ),
                tool("select_widgets", "Selects widgets on the canvas.", mapOf("ids" to ("array" to "Widget ids")), listOf("ids")),
                tool("get_layout_text", "The active layout as the text of a .layout file."),
                tool(
                    "replace_layout_text",
                    "Replaces the whole active layout by the given .layout text (undoable). Give the text, or the path of a file that holds it (better for a big layout).",
                    mapOf(
                        "text" to ("string" to "Text of a .layout file"),
                        "path" to ("string" to "Path of a file with that text, read instead of text"),
                    ),
                ),
                tool(
                    "get_script_template",
                    "The Enforce Script class for the active layout, as the editor copies it: the widget members, the lookup, the events of the buttons and inputs and, for a menu, its registration.",
                    mapOf(
                        "kind" to
                            (
                                "string" to
                                    "menu (UIScriptedMenu, default), component (ScriptedWidgetEventHandler) or widgets (members and lookup only)"
                            ),
                        "class_name" to ("string" to "Name of the class, made from the mod and the layout name by default"),
                    ),
                ),
                tool("get_problems", "What the editor thinks is wrong in the active layout."),
                tool(
                    "set_view",
                    "Changes the view of the canvas.",
                    mapOf(
                        "live_preview" to ("boolean" to "Clean view without borders and labels"),
                        "workbench_130_colors" to ("boolean" to "Draw colours like the Workbench of 1.30 instead of like the game"),
                        "show_grid" to ("boolean" to "Show the grid"),
                        "show_widget_names" to ("boolean" to "Write the type and name in the corner of every widget"),
                        "snap_to_grid" to ("boolean" to "Snap moved widgets to the grid"),
                        "grid_size" to ("integer" to "Grid step in pixels"),
                        "resolution" to ("string" to "e.g. 1920x1080 or FitToWindow"),
                        "zoom" to ("number" to "Zoom, 1 is 100%"),
                        "fit" to ("boolean" to "Fit the screen into the view"),
                        "actual_size" to ("boolean" to "Zoom to 100%"),
                    ),
                ),
                tool(
                    "screenshot",
                    "A picture of the canvas. The view is restored afterwards.",
                    mapOf(
                        "mode" to
                            (
                                "string" to
                                    "editor (as it is, default), preview (Live Preview like the game) or preview_workbench (Live Preview like Workbench 1.30)"
                            ),
                        "area" to
                            (
                                "string" to
                                    "canvas (default), window, or screen: only the screen of the layout, scaled to exactly the pixels of the resolution (1920x1080), for comparing with a reference one to one"
                            ),
                        "max_width" to
                            (
                                "integer" to
                                    "Scale down to this width, 1600 by default (ignored for area screen, which is always the size of the layout screen)"
                            ),
                        "fit" to ("boolean" to "Fit the screen into the view first, true by default"),
                        "actual_size" to ("boolean" to "With fit false: zoom to 100%"),
                    ),
                ),
                tool(
                    "list_icons",
                    "Images of the imagesets of the game and of the connected mods, as set:NAME image:IMAGE.",
                    mapOf(
                        "query" to ("string" to "Words to look for in the set or image name"),
                        "limit" to ("integer" to "At most this many, 100 by default"),
                    ),
                ),
                tool(
                    "list_styles",
                    "Style names of a widget class, from the game and the connected mods.",
                    mapOf("widget_class" to ("string" to "e.g. PanelWidget, ButtonWidget")),
                    listOf("widget_class"),
                ),
                tool("list_imagesets", "Names of the imagesets of the game and of the connected mods."),
                tool(
                    "convert_texture",
                    "Converts an image to the game's .edds texture (png, jpg, bmp, gif) or a .edds back to .png, by the extension of the source. The .edds is uncompressed BGRA with mip levels.",
                    mapOf(
                        "source" to ("string" to "Full path of the file to convert"),
                        "output" to ("string" to "Full path of the result, next to the source with the other extension by default"),
                        "mips" to ("boolean" to "Make mip levels when writing .edds, true by default"),
                        "overwrite" to ("boolean" to "Replace an existing result, false by default"),
                    ),
                    listOf("source"),
                ),
                tool(
                    "convert_font",
                    "Makes the game's bitmap fonts (a .fnt with its .edds atlas for each size) from a .ttf or .otf file, with Latin and Cyrillic letters by default. The answer gives the font path to write into the font property.",
                    mapOf(
                        "source" to ("string" to "Full path of the .ttf or .otf file"),
                        "output_dir" to ("string" to "Folder for the files, usually gui/fonts of the mod (made when missing)"),
                        "name" to ("string" to "Name of the font; the files are <name><size>.fnt, from the file name by default"),
                        "sizes" to ("array" to "Line heights in pixels, from 6 to 200; 12, 14, 16, 22, 28, 48 by default"),
                        "charset" to
                            ("string" to "ascii, latin, cyrillic (Latin and Cyrillic) or default (what a font usually needs, default)"),
                        "overwrite" to ("boolean" to "Replace existing files, false by default"),
                    ),
                    listOf("source", "output_dir"),
                ),
                tool(
                    "measure_text",
                    "The width and height in pixels that a text takes with a font and an exact text size, as the editor draws it. Use it to choose a size that makes a text as wide as in a reference, instead of screenshots. Give width and wrap to measure a wrapped text.",
                    mapOf(
                        "text" to ("string" to "The text"),
                        "font" to ("string" to "Font path as in list_fonts, MetronBook by default"),
                        "size" to ("integer" to "exact text size, a whole number"),
                        "bold" to ("boolean" to "bold text"),
                        "wrap" to ("boolean" to "Wrap the text at width"),
                        "width" to ("integer" to "Width of the box in pixels for wrapping"),
                    ),
                    listOf("text", "size"),
                ),
                tool(
                    "list_strings",
                    "The keys of the string tables (stringtable.csv) of the game and of the connected mods, with their text in the language of the editor. A text widget shows a #KEY as that text. source is game for the vanilla table, a mod folder name, or all.",
                    mapOf(
                        "query" to ("string" to "Words that are in the key or in the text"),
                        "source" to ("string" to "game, a mod folder name or all"),
                        "limit" to ("integer" to "How many to return, 50 by default"),
                    ),
                ),
                tool(
                    "create_imageset",
                    "Makes an imageset from pictures: packs png, jpg or edds files into one atlas (<name>.edds, sides a power of two) and writes <name>.imageset next to it with the path of the atlas from the mod folder. The answer lists every image as set:NAME image:IMAGE for image0. The editor finds the new set at once. Register it in config.cpp with register_assets.",
                    mapOf(
                        "output" to
                            (
                                "string" to
                                    "Full path of the .imageset to write, inside the mod (e.g. K:\\MF_Mods\\MyMod\\assets\\gui\\icons.imageset)"
                            ),
                        "images" to ("array" to "List of {name, source}: the name of the image and the full path of its picture"),
                        "folder" to ("string" to "A folder whose pictures are all added, named after their files"),
                        "name" to ("string" to "Name of the set as layouts write it (set:NAME), the file name by default"),
                        "mask" to
                            (
                                "boolean" to
                                    "Turn every picture into a grey mask of the same brightness (the transparency stays), false by default"
                            ),
                        "padding" to ("integer" to "Pixels between the pictures, 2 by default"),
                        "max_size" to ("integer" to "Biggest side of the atlas, 4096 by default"),
                        "overwrite" to ("boolean" to "Replace an existing imageset, false by default"),
                    ),
                    listOf("output"),
                ),
                tool(
                    "create_style",
                    "Makes a widget style (style NAME in a layout) in a .styles file, or adds it to an existing one (other styles stay). A style is nine slots (LeftTop, Top, RightTop, Right, RightBottom, Bottom, LeftBottom, Left, Center) of images from an imageset, for each state (a button has Normal, Highlight, Pushed, Disabled, Focus, other widgets Normal, Disabled). fill is one image for all slots (a flat colour: an image of one white pixel, tinted by color), slots names the images by slot, states overrides a state; states without slots use the ones of Normal. Register the file with register_assets.",
                    mapOf(
                        "file" to ("string" to "Full path of the .styles file (created when missing)"),
                        "widget" to ("string" to "Widget type, ButtonWidget by default (PanelWidget, TextWidget, EditBoxWidget ...)"),
                        "name" to ("string" to "Name of the style"),
                        "imageset" to ("string" to "Name of the imageset the images are from (create_imageset, list_imagesets)"),
                        "fill" to ("string" to "One image for all nine slots"),
                        "slots" to ("object" to "Slot name to image name; with fill they replace single slots"),
                        "states" to ("object" to "State name to a slots object or one image name"),
                        "color" to
                            ("string" to "Tint of the style: r g b a from 0 to 1, #RRGGBB, #AARRGGBB or the ARGB number; white by default"),
                        "font" to ("string" to "Font of the style, none by default"),
                        "replace" to ("boolean" to "Replace a style of the same name, false by default"),
                    ),
                    listOf("file", "name", "imageset"),
                ),
                tool(
                    "register_assets",
                    "Writes the .imageset and .styles files of a mod into its config.cpp (CfgMods > defs > imageSets and widgetStyles), without repeating what is there. Needed so that the game loads them. apply=false only returns what would be added.",
                    mapOf(
                        "mod" to ("string" to "Folder of the mod; the mod of the open layout by default"),
                        "apply" to ("boolean" to "Write config.cpp, true by default"),
                    ),
                ),
                tool(
                    "open_file",
                    "Opens a file in its own tab of the editor: a .layout, an .imageset, a .styles, a texture (.edds, .png) or a font (.fnt). The person watching sees it. For a layout you work on use open_layout.",
                    mapOf("path" to ("string" to "Full path of the file")),
                    listOf("path"),
                ),
                tool(
                    "list_fonts",
                    "The fonts of the game and of the connected mods (every .fnt with its atlas), as the path to put in quotes into the font property, with kind, size, origin and whether it has Latin and Cyrillic letters.",
                    mapOf("query" to ("string" to "Part of the path or of the mod name")),
                ),
            )
    }
}

package com.github.demandss.dayz_workbench_tool.layout_editor.mcp

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toAwtImage
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
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.findProblems
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.sanitizeWidgetName
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
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
            McpHost.log("$name  ${System.currentTimeMillis() - started} ms${if (failed) "  ошибка" else ""}")
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
            "open_layout" -> openLayout(args)
            "close_layout" -> closeLayout(args)
            "save_layout" -> saveLayout()
            "undo" -> undoRedo(true, args)
            "redo" -> undoRedo(false, args)
            "get_tree" -> getTree(args)
            "get_widget" -> widgetJson(requireNode(args), withRect = true).toString()
            "find_widgets" -> findWidgets(args)
            "set_properties" -> setProperties(args)
            "add_widget" -> addWidget(args)
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
            put("selected", buildJsonArray { state.selectedIds.forEach { add(JsonPrimitive(it.toString())) } })
            put("live_preview", state.livePreview)
            put("workbench_130_colors", state.workbenchColors)
            put("show_grid", state.showGrid)
            put("show_widget_names", state.showWidgetNames)
            put("grid_size", state.gridSize)
            put("snap_to_grid", state.snapToGrid)
            put("zoom", state.zoom.toDouble())
            put("resolution", state.selectedResolution.label)
            put("work_drive", state.workDrivePath)
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

    private fun listStyles(args: JsonObject): String {
        val widgetClass = args.string("widget_class") ?: throw ToolFailure("widget_class is required, e.g. PanelWidget")
        return state
            .vanillaStylesFor(widgetClass, state.activeFile?.path)
            .joinToString("\n") { it.name }
    }

    // ---- files

    private fun openLayout(args: JsonObject): String {
        val path = args.string("path") ?: throw ToolFailure("path is required")
        val before = state.openFiles.size
        state.openFile(path)
        val doc = state.activeFile
        if (doc == null || (state.openFiles.size == before && !doc.path.equals(java.io.File(path).canonicalPath, true))) {
            throw ToolFailure("Could not open $path")
        }
        return "Opened ${doc.path}"
    }

    private fun closeLayout(args: JsonObject): String {
        val index = args.int("index") ?: state.activeFileIndex
        if (index !in state.openFiles.indices) throw ToolFailure("No layout with that index")
        state.closeFile(index)
        return "Closed"
    }

    private fun saveLayout(): String {
        val doc = requireDoc()
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

    private fun setProperties(args: JsonObject): String {
        val node = requireNode(args)
        val values = args["properties"] as? JsonObject ?: throw ToolFailure("properties must be an object of name: value")
        edit("MCP: ${values.keys.joinToString(", ")}") { root ->
            root.updateNode(node.id) { n ->
                val props = LinkedHashMap(n.node.properties)
                for ((key, value) in values) {
                    if (value is JsonNull) props.remove(key) else props[key] = (value as? JsonPrimitive)?.content.orEmpty()
                }
                n.copy(node = n.node.copy(properties = props))
            }
        }
        return widgetJson(requireNode(buildJsonObject { put("id", node.id.toString()) }), withRect = true).toString()
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

    private fun addWidget(args: JsonObject): String {
        val doc = requireDoc()
        val parent =
            if (args.string("parent_id") != null || args.string("parent_name") != null || args.string("parent_path") != null) {
                requireNode(args, "parent_")
            } else {
                doc.root
            }
        val type = typeOf(args.string("type") ?: throw ToolFailure("type is required, e.g. PanelWidget"))
        val taken = mutableSetOf<String>()

        fun collect(n: EditorWidgetNode) {
            taken += n.node.name
            n.children.forEach(::collect)
        }
        collect(doc.root)
        val wanted = sanitizeWidgetName(args.string("name") ?: type.className.removeSuffix("WidgetClass"))
        var name = wanted
        var n = 1
        while (name in taken) name = wanted + (++n)
        val props =
            linkedMapOf(
                "position" to "0 0",
                "size" to "100 50",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "hexactsize" to "1",
                "vexactsize" to "1",
            )
        (args["properties"] as? JsonObject)?.forEach { (k, v) -> props[k] = (v as? JsonPrimitive)?.content.orEmpty() }
        val created = EditorWidgetNode(node = WidgetNode(type = type, originalClassName = type.className, name = name, properties = props))
        val index = args.int("index")
        edit("MCP: add $name") { root ->
            root.updateNode(parent.id) { p ->
                val list = p.children.toMutableList()
                list.add(index?.coerceIn(0, list.size) ?: list.size, created)
                p.copy(children = list)
            }
        }
        state.selectedIds.clear()
        state.selectedIds.add(created.id)
        return widgetJson(created, withRect = false).toString()
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
        val text = args.string("text") ?: throw ToolFailure("text is required")
        val parsed = runCatching { LayoutParser.parse(text) }.getOrElse { throw ToolFailure("Not a layout: ${it.message}") }
        val idx = state.activeFileIndex
        val doc = state.openFiles[idx]
        state.pushUndo(idx, doc.root, "MCP: replace layout")
        state.updateActiveRoot(parsed.toEditorNode())
        state.selectedIds.clear()
        return "Replaced"
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
        val maxWidth = (args.int("max_width") ?: 1600).coerceIn(200, 4000)
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
            Thread.sleep(600)
            val image = onMain { capture(area) } ?: return textResult("The window cannot be captured", isError = true)
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

    private fun capture(area: String): BufferedImage? {
        val window = state.hostWindow ?: return null
        val layer = findLayer(window) ?: return null
        val full = layer.screenshot()?.asComposeImageBitmap()?.toAwtImage() ?: return null
        if (area == "window") return full
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
            }

        private val WIDGET_REF =
            mapOf(
                "id" to ("string" to "Widget id from get_tree"),
                "name" to ("string" to "Widget name (the first one with it)"),
                "path" to ("string" to "Path of names from the root, e.g. rootFrame/Header/CloseImg"),
            )

        fun definitions(): List<JsonObject> =
            listOf(
                tool("editor_status", "Open layouts, the selection, the view flags, the zoom and the resolution."),
                tool(
                    "open_layout",
                    "Opens a .layout file in the editor and makes it the active one.",
                    mapOf("path" to ("string" to "Full path of the file")),
                    listOf("path"),
                ),
                tool(
                    "close_layout",
                    "Closes a layout tab (the active one when index is not given).",
                    mapOf("index" to ("integer" to "Tab index from editor_status")),
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
                    "Sets properties of a widget (undoable). A value of null removes the property. Values are written as they stand in the .layout file, with quotes for text, e.g. image0 = \"set:Name image:Img\".",
                    WIDGET_REF + mapOf("properties" to ("object" to "Property name to value")),
                    listOf("properties"),
                ),
                tool(
                    "add_widget",
                    "Adds a widget inside a parent (the root when no parent is given).",
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
                    "Replaces the whole active layout by the given .layout text (undoable).",
                    mapOf("text" to ("string" to "Text of a .layout file")),
                    listOf("text"),
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
                        "area" to ("string" to "canvas (default) or window"),
                        "max_width" to ("integer" to "Scale down to this width, 1600 by default"),
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
            )
    }
}

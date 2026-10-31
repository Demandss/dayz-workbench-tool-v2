package com.github.demandss.dayz_workbench_tool.layout

import kotlin.collections.iterator

object LayoutSerializer {
    fun serialize(root: WidgetNode): String = serializeNode(root, depth = 0, usedIds = mutableSetOf())

    private fun serializeNode(
        node: WidgetNode,
        depth: Int,
        usedIds: MutableSet<String>,
    ): String =
        buildString {
            val indent = "\t".repeat(depth)
            val innerIndent = "\t".repeat(depth + 1)

            val className = if (node.type == WidgetType.UNKNOWN) node.originalClassName else node.type.className
            val resourceId = node.resourceId
            require(resourceId == null || usedIds.add(resourceId.uppercase())) {
                "Duplicate widget resource identifier: $resourceId"
            }
            val namePart = resourceId?.let { " \"$it\"" }.orEmpty()

            append("$indent$className$namePart {\n")

            val escapedName = node.name.replace("\\", "\\\\").replace("\"", "\\\"")
            append("${innerIndent}Name \"$escapedName\"\n")

            for ((key, value) in node.properties) {
                if (key == "Name") continue
                val quotedKey = if (' ' in key) "\"$key\"" else key
                append("$innerIndent$quotedKey $value\n")
            }

            if (node.children.isNotEmpty()) {
                append("$innerIndent{\n")
                for (child in node.children) {
                    append(serializeNode(child, depth + 2, usedIds))
                }
                append("$innerIndent}\n")
            }

            node.scriptParams?.let { params ->
                append("$innerIndent{\n")
                append("$innerIndent\tScriptParamsClass {\n")
                for ((key, value) in params) append("$innerIndent\t\t$key $value\n")
                append("$innerIndent\t}\n")
                append("$innerIndent}\n")
            }

            append("$indent}\n")
        }
}

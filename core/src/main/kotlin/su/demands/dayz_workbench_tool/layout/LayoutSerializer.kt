package su.demands.dayz_workbench_tool.layout

object LayoutSerializer {
    fun serialize(root: WidgetNode): String = serializeNode(root, depth = 0)

    private fun serializeNode(node: WidgetNode, depth: Int): String = buildString {
        val indent = "\t".repeat(depth)
        val innerIndent = "\t".repeat(depth + 1)

        val className = if (node.type == WidgetType.UNKNOWN) node.originalClassName else node.type.className
        val namePart = if (node.name.isNotEmpty()) " ${node.name}" else ""

        append("$indent$className$namePart {\n")

        for ((key, value) in node.properties) {
            val quotedKey = if (' ' in key) "\"$key\"" else key
            append("$innerIndent$quotedKey $value\n")
        }

        if (node.children.isNotEmpty()) {
            append("$innerIndent{\n")
            for (child in node.children) {
                append(serializeNode(child, depth + 2))
            }
            append("$innerIndent}\n")
        }

        append("$indent}\n")
    }
}
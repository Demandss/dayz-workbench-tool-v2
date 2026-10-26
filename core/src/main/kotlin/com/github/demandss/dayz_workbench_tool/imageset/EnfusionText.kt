package com.github.demandss.dayz_workbench_tool.imageset

/**
 * One block of the curly-brace text format of Enfusion resources (`.imageset` and the like): a class name, an optional
 * instance name, plain `key value` lines and nested blocks. Values are kept as the text that stood in the file.
 */
class EnfusionNode(
    val className: String,
    val instanceName: String? = null,
    val props: LinkedHashMap<String, String> = LinkedHashMap(),
    val children: MutableList<EnfusionNode> = mutableListOf(),
) {
    fun child(className: String): EnfusionNode? = children.firstOrNull { it.className == className }

    fun childrenOf(className: String): List<EnfusionNode> = children.filter { it.className == className }
}

object EnfusionText {
    /**
     * Reads the text. Blocks start on a line that ends with `{` and end on a line that is `}`; every other line is
     * a property. Blank lines are skipped. Returns null when there is no block or the braces do not match.
     */
    fun parse(text: String): EnfusionNode? {
        val stack = ArrayList<EnfusionNode>()
        var root: EnfusionNode? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.endsWith("{") -> {
                    val tokens = tokens(line.dropLast(1))
                    if (tokens.isEmpty()) return null
                    val node = EnfusionNode(tokens[0], tokens.getOrNull(1))
                    if (stack.isEmpty()) {
                        if (root != null) return null
                        root = node
                    } else {
                        stack.last().children += node
                    }
                    stack += node
                }

                line == "}" -> {
                    if (stack.isEmpty()) return null
                    stack.removeAt(stack.lastIndex)
                }

                else -> {
                    val parent = stack.lastOrNull() ?: return null
                    val key = line.takeWhile { !it.isWhitespace() }
                    parent.props[key] = line.drop(key.length).trim()
                }
            }
        }
        return if (stack.isEmpty()) root else null
    }

    /** Words of a header line; a quoted word keeps its spaces and loses its quotes. */
    private fun tokens(header: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < header.length) {
            val c = header[i]
            when {
                c.isWhitespace() -> {
                    i++
                }

                c == '"' -> {
                    val end = header.indexOf('"', i + 1).let { if (it < 0) header.length else it }
                    out += header.substring(i + 1, end)
                    i = end + 1
                }

                else -> {
                    val start = i
                    while (i < header.length && !header[i].isWhitespace()) i++
                    out += header.substring(start, i)
                }
            }
        }
        return out
    }

    /** Writes the tree the way Workbench does: one space of indent per level, `Class Name {`, `key value`, `}`. */
    fun serialize(root: EnfusionNode): String {
        val sb = StringBuilder()

        fun write(
            node: EnfusionNode,
            depth: Int,
        ) {
            val pad = " ".repeat(depth)
            sb.append(pad).append(node.className)
            node.instanceName?.let { sb.append(' ').append(it) }
            sb.append(" {\n")
            for ((key, value) in node.props) {
                sb.append(pad).append(' ').append(key)
                if (value.isNotEmpty()) sb.append(' ').append(value)
                sb.append('\n')
            }
            for (child in node.children) write(child, depth + 1)
            sb.append(pad).append("}\n")
        }
        write(root, 0)
        return sb.toString()
    }
}

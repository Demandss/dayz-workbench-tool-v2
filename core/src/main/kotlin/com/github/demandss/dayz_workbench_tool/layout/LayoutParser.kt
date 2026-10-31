package com.github.demandss.dayz_workbench_tool.layout

object LayoutParser {
    fun parse(input: String): WidgetNode = ParserState(input.removePrefix("\uFEFF")).parseWidget()
}

private class ParserState(
    private val input: String,
) {
    var pos = 0

    fun parseWidget(): WidgetNode {
        skipWhitespaceAndComments()
        val className = readBareWord()
        skipWhitespaceAndComments()

        val quotedHeader = pos < input.length && input[pos] == '"'
        val header =
            when {
                quotedHeader -> readQuotedString()
                pos < input.length && input[pos] != '{' -> readBareWord()
                else -> ""
            }
        skipWhitespaceAndComments()
        expectChar('{')

        val props = LinkedHashMap<String, String>()
        val children = mutableListOf<WidgetNode>()
        var scriptParams: LinkedHashMap<String, String>? = null

        while (true) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            when (input[pos]) {
                '}' -> {
                    pos++
                    break
                }

                '{' -> {
                    pos++
                    skipWhitespaceAndComments()
                    if (input.startsWith("ScriptParamsClass", pos)) {
                        scriptParams = parseScriptParams()
                    } else {
                        parseChildrenInto(children)
                    }
                }

                else -> {
                    parsePropInto(props)
                }
            }
        }

        // Detect the new syntax from its Name property or resource identifier before exposing the node.
        val resourceId = header.takeIf { quotedHeader && it.matches(Regex("\\{[0-9a-fA-F]{16}\\}")) }
        val name =
            props.remove("Name")?.let { value ->
                require(value.startsWith('"')) { "Expected quoted Name at offset $pos" }
                ParserState(value).readQuotedString()
            } ?: if (resourceId == null) header else ""
        return WidgetNode(
            WidgetType.fromClassName(className),
            className,
            name,
            props,
            children,
            scriptParams,
            resourceId ?: newWidgetResourceId(),
        )
    }

    /** Reads `ScriptParamsClass { key value ... }` and the brace that closes the block around it. */
    private fun parseScriptParams(): LinkedHashMap<String, String> {
        readBareWord()
        skipInlineWhitespace()
        expectChar('{')
        val params = LinkedHashMap<String, String>()
        while (true) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            if (input[pos] == '}') {
                pos++
                break
            }
            parsePropInto(params)
        }
        skipWhitespaceAndComments()
        expectChar('}')
        return params
    }

    private fun parseChildrenInto(children: MutableList<WidgetNode>) {
        while (true) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break
            if (input[pos] == '}') {
                pos++
                break
            }
            children.add(parseWidget())
        }
    }

    private fun parsePropInto(props: LinkedHashMap<String, String>) {
        val key = if (input[pos] == '"') readQuotedString() else readBareWord()
        skipInlineWhitespace()
        val value = readToEndOfLine()
        if (key.isNotEmpty()) props[key] = value
    }

    private fun skipWhitespaceAndComments() {
        while (pos < input.length) {
            when {
                input[pos].isWhitespace() -> {
                    pos++
                }

                pos + 1 < input.length && input[pos] == '/' && input[pos + 1] == '/' -> {
                    while (pos < input.length && input[pos] != '\n') pos++
                }

                pos + 1 < input.length && input[pos] == '/' && input[pos + 1] == '*' -> {
                    pos += 2
                    while (pos + 1 < input.length && !(input[pos] == '*' && input[pos + 1] == '/')) pos++
                    if (pos + 1 < input.length) pos += 2
                }

                else -> {
                    break
                }
            }
        }
    }

    private fun skipInlineWhitespace() {
        while (pos < input.length && (input[pos] == ' ' || input[pos] == '\t')) pos++
    }

    private fun readBareWord(): String {
        val start = pos
        while (pos < input.length && !input[pos].isWhitespace() &&
            input[pos] != '{' && input[pos] != '}' && input[pos] != '"'
        ) {
            pos++
        }
        return input.substring(start, pos)
    }

    private fun readQuotedString(): String {
        pos++ // skip opening "
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != '"' && input[pos] != '\n') {
            val char = input[pos++]
            if (char == '\\' && pos < input.length && input[pos] in "\\\"") {
                sb.append(input[pos++])
            } else {
                sb.append(char)
            }
        }
        if (pos < input.length && input[pos] == '"') pos++ // skip closing "
        return sb.toString()
    }

    private fun readToEndOfLine(): String {
        val start = pos
        while (pos < input.length && input[pos] != '\n' && input[pos] != '\r') pos++
        return input.substring(start, pos).trimEnd()
    }

    private fun expectChar(c: Char) {
        require(pos < input.length && input[pos] == c) { "Expected '$c' at offset $pos" }
        pos++
    }
}

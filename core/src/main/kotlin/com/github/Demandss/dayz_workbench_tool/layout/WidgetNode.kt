package com.github.demandss.dayz_workbench_tool.layout

data class WidgetNode(
    val type: WidgetType,
    val originalClassName: String,
    var name: String,
    val properties: LinkedHashMap<String, String> = LinkedHashMap(),
    val children: MutableList<WidgetNode> = mutableListOf(),
    // The `{ ScriptParamsClass { ... } }` block some widgets carry after their children (the gap of a spacer, for example)
    val scriptParams: LinkedHashMap<String, String>? = null,
    val resourceId: String? = newWidgetResourceId(),
)

fun newWidgetResourceId(): String =
    "{" +
        java.util.UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(16)
            .uppercase() + "}"

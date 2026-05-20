package com.github.demandss.dayz_workbench_tool.layout

data class WidgetNode(
    val type: WidgetType,
    val originalClassName: String,
    var name: String,
    val properties: LinkedHashMap<String, String> = LinkedHashMap(),
    val children: MutableList<WidgetNode> = mutableListOf()
)
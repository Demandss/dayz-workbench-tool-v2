package com.github.demandss.dayz_workbench_tool.layout_editor.state

enum class ToastKind { INFO, SUCCESS, ERROR }

/** A short message in the corner that goes away on its own. */
data class Toast(
    val id: Long,
    val text: String,
    val kind: ToastKind,
)

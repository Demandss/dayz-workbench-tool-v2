package com.github.demandss.dayz_workbench_tool.layout_editor.state

/** What the window asks before an action that cannot be undone. */
class Confirmation(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val danger: Boolean = false,
    val onConfirm: () -> Unit,
)

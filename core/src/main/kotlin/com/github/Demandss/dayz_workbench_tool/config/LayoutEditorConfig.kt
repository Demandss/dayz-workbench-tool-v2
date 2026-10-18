package com.github.demandss.dayz_workbench_tool.config

import kotlinx.serialization.Serializable

@Serializable
data class LayoutEditorConfig(
    val undoLimit: Int = 5,
    val defaultResolution: String = "1920x1080",
    // What was open when the editor was closed, per work drive (lower-case path without a trailing slash)
    val sessions: Map<String, EditorSession> = emptyMap(),
)

@Serializable
data class EditorSession(
    val layouts: List<String> = emptyList(),
    val textures: List<String> = emptyList(),
    val active: String = "",
    val activeIsTexture: Boolean = false,
    val expandedDirs: List<String> = emptyList(),
    val selectedPath: String = "",
)

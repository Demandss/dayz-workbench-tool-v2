package com.github.demandss.dayz_workbench_tool.config

import kotlinx.serialization.Serializable

@Serializable
data class LayoutEditorConfig(
    val undoLimit: Int = 5,
    val defaultResolution: String = "1920x1080",
    // The preferences window
    val mcpEnabled: Boolean = true,
    val mcpPort: Int = 39871,
    val workbenchColors: Boolean = false,
    val showGrid: Boolean = false,
    val snapToGrid: Boolean = false,
    val gridSize: Int = 10,
    val showWidgetNames: Boolean = true,
    // What was open when the editor was closed, per work drive (lower-case path without a trailing slash)
    val sessions: Map<String, EditorSession> = emptyMap(),
)

@Serializable
data class EditorSession(
    val layouts: List<String> = emptyList(),
    val textures: List<String> = emptyList(),
    val imageSets: List<String> = emptyList(),
    val activeIsImageSet: Boolean = false,
    val styles: List<String> = emptyList(),
    val activeIsStyles: Boolean = false,
    val active: String = "",
    val activeIsTexture: Boolean = false,
    val expandedDirs: List<String> = emptyList(),
    val selectedPath: String = "",
    val referencePath: String = "",
    val referenceOpacity: Float = 0.5f,
    val referenceShown: Boolean = false,
    val recent: List<String> = emptyList(),
)

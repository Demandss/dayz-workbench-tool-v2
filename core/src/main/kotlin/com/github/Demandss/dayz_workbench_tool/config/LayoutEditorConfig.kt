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
    // The language of the interface: `system` (the language of the computer) or the code of a language
    val language: String = "system",
    // How a layout is shown when it is opened: `actual` is 100%, `fit` puts the whole screen into the window
    val zoomOnOpen: String = "actual",
    // Whether the editor opens in the size it was closed in
    val rememberWindow: Boolean = true,
    // The window of the editor when it was last changed: its size in dp, and whether it was maximised
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,
    val windowMaximized: Boolean = false,
    // What was open when the editor was closed, per work drive (lower-case path without a trailing slash)
    val sessions: Map<String, EditorSession> = emptyMap(),
)

/**
 * [other]'s settings on top of this config; the window size, `windowMaximized` and the `sessions` stay as they are, because the
 * editor writes those itself while it runs and a copy kept in the settings window of the app must not bring old ones back.
 */
fun LayoutEditorConfig.withSettingsFrom(other: LayoutEditorConfig) =
    copy(
        undoLimit = other.undoLimit,
        defaultResolution = other.defaultResolution,
        mcpEnabled = other.mcpEnabled,
        mcpPort = other.mcpPort,
        workbenchColors = other.workbenchColors,
        showGrid = other.showGrid,
        snapToGrid = other.snapToGrid,
        gridSize = other.gridSize,
        showWidgetNames = other.showWidgetNames,
        language = other.language,
        zoomOnOpen = other.zoomOnOpen,
        rememberWindow = other.rememberWindow,
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

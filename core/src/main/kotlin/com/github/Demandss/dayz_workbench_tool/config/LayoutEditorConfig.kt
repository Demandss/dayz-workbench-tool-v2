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
        zoomOnOpen = other.zoomOnOpen,
        rememberWindow = other.rememberWindow,
    )

/**
 * This config with only the settings that [edited] changed against [base]: what the person did not touch in the settings window
 * stays as it is on the disk, so a toggle changed in the editor while the window was open is not put back by Save.
 */
fun LayoutEditorConfig.withChanges(
    base: LayoutEditorConfig,
    edited: LayoutEditorConfig,
): LayoutEditorConfig {
    fun <T> pick(
        old: T,
        new: T,
        current: T,
    ) = if (new != old) new else current
    return copy(
        undoLimit = pick(base.undoLimit, edited.undoLimit, undoLimit),
        defaultResolution = pick(base.defaultResolution, edited.defaultResolution, defaultResolution),
        mcpEnabled = pick(base.mcpEnabled, edited.mcpEnabled, mcpEnabled),
        mcpPort = pick(base.mcpPort, edited.mcpPort, mcpPort),
        workbenchColors = pick(base.workbenchColors, edited.workbenchColors, workbenchColors),
        showGrid = pick(base.showGrid, edited.showGrid, showGrid),
        snapToGrid = pick(base.snapToGrid, edited.snapToGrid, snapToGrid),
        gridSize = pick(base.gridSize, edited.gridSize, gridSize),
        showWidgetNames = pick(base.showWidgetNames, edited.showWidgetNames, showWidgetNames),
        zoomOnOpen = pick(base.zoomOnOpen, edited.zoomOnOpen, zoomOnOpen),
        rememberWindow = pick(base.rememberWindow, edited.rememberWindow, rememberWindow),
    )
}

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

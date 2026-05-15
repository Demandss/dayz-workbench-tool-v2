package su.demands.dayz_workbench_tool.config

import kotlinx.serialization.Serializable

@Serializable
data class LayoutEditorConfig(
    val undoLimit: Int = 5,
    val defaultResolution: String = "1920x1080"
)
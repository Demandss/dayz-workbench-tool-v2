package com.github.demandss.dayz_workbench_tool.model

import java.nio.file.Path

data class Modification(
    val path: String,
    val side: ModSide,
    val type: ModType = if ("@" in path) ModType.WORKSHOP else ModType.LOCAL,
    val enabled: Boolean = true,
    val priority: Int = 0
) {
    val name: String get() = Path.of(path).fileName?.toString() ?: path
}
package com.github.demandss.dayz_workbench_tool.layout_editor.state

import java.awt.image.BufferedImage

/** A texture (.edds or a plain image) opened in a viewer tab. [source] is null while loading or after an error. */
data class TextureDocument(
    val path: String,
    val source: BufferedImage? = null,
    val format: String = "",
    val mipCount: Int = 0,
    val fileSize: Long = 0,
    val error: String? = null,
    val loading: Boolean = true,
) {
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
}

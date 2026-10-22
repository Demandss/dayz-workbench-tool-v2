package com.github.demandss.dayz_workbench_tool.layout_editor.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ModResourcesTest {
    private val source = File("K:/gui")

    @Test
    fun stylesAndImageSetsOfAConnectedModAreFound() {
        val styles = File(source, "looknfeel/dayzwidgets.styles")
        val sets = File(source, "imagesets").listFiles { f -> f.extension == "imageset" }.orEmpty()
        if (!styles.isFile || sets.isEmpty()) return
        val mod =
            kotlin.io.path
                .createTempDirectory("mod")
                .toFile()
        File(mod, "gui").mkdirs()
        styles.copyTo(File(mod, "gui/my.styles"))
        sets.first().copyTo(File(mod, "gui/my.imageset"))
        val state = LayoutEditorState("", CoroutineScope(SupervisorJob() + Dispatchers.Default), "", listOf(mod.path))
        var found = false
        repeat(50) {
            if (!found) {
                found = state.vanillaStylesFor("ButtonWidget", null).isNotEmpty() && state.imageSetNames(null).isNotEmpty()
                if (!found) Thread.sleep(100)
            }
        }
        mod.deleteRecursively()
        assertTrue(found)
    }
}

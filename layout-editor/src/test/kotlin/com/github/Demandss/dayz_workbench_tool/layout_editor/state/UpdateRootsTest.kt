package com.github.demandss.dayz_workbench_tool.layout_editor.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateRootsTest {
    private fun folder(name: String): File = createTempDirectory(name).toFile()

    @Test
    fun theTreeAndTheWorkDriveFollowTheSettingsOfTheMainWindow() {
        val first = folder("modA")
        val second = folder("modB")
        val state = LayoutEditorState("", CoroutineScope(SupervisorJob() + Dispatchers.Default), "", listOf(first.path))
        assertEquals(listOf(first.name), state.treeRoots?.map { it.label })

        // A mod is enabled in the main window, and the work drive is set
        state.updateRoots("D:/work", "", listOf(first.path, second.path))
        assertEquals(listOf(first.name, second.name), state.treeRoots?.map { it.label })
        assertEquals("D:/work", state.workDrivePath)

        // Switched off again: only the first one is left
        state.updateRoots("D:/work", "", listOf(first.path))
        assertEquals(listOf(first.name), state.treeRoots?.map { it.label })

        first.deleteRecursively()
        second.deleteRecursively()
    }
}

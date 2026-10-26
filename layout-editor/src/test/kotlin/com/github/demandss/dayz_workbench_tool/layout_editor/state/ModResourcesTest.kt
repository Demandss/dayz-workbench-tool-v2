package com.github.demandss.dayz_workbench_tool.layout_editor.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ModResourcesTest {
    // A style sheet and an imageset of a few lines, written for the test: it reads nothing from the disk of the person who runs it
    private val styles =
        """
        <WidgetStyles>
            <Widget Name="ButtonWidget">
                <Style Name="Default" Font="" ImageSet="my_set" Color="4294967295">
                    <State Name="Normal">
                        <Item Name="Center" Image="ButtonCenter" />
                    </State>
                </Style>
            </Widget>
        </WidgetStyles>
        """.trimIndent()

    private val imageSet =
        """
        ImageSetClass {
         Name "my_set"
         RefSize 64 64
         Textures {
          ImageSetTextureClass {
           mpix 0
           path "gui/my_set.edds"
          }
         }
         Images {
          ImageSetDefClass ButtonCenter {
           Name "ButtonCenter"
           Pos 0 0
           Size 32 32
           Flags 0
          }
         }
        }
        """.trimIndent()

    @Test
    fun stylesAndImageSetsOfAConnectedModAreFound() {
        val mod =
            kotlin.io.path
                .createTempDirectory("mod")
                .toFile()
        File(mod, "gui").mkdirs()
        File(mod, "gui/my.styles").writeText(styles)
        File(mod, "gui/my.imageset").writeText(imageSet)
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

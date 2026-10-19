package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState

/** The keys that work while the canvas has the keyboard. Returns true when the key was used. */
internal fun handleCanvasKey(
    state: LayoutEditorState,
    ev: KeyEvent,
): Boolean {
    if (ev.type != KeyEventType.KeyDown) return false
    val ctrl = ev.isCtrlPressed
    val shift = ev.isShiftPressed
    val step = if (shift) 10f else 1f
    return when {
        ev.key == Key.Delete && !ctrl -> {
            state.deleteSelected()
            true
        }

        ev.key == Key.DirectionLeft && !ctrl -> {
            state.nudgeSelected(-step, 0f)
            true
        }

        ev.key == Key.DirectionRight && !ctrl -> {
            state.nudgeSelected(step, 0f)
            true
        }

        ev.key == Key.DirectionUp && !ctrl -> {
            state.nudgeSelected(0f, -step)
            true
        }

        ev.key == Key.DirectionDown && !ctrl -> {
            state.nudgeSelected(0f, step)
            true
        }

        ev.key == Key.F2 -> {
            state.requestRename()
            true
        }

        ctrl && !ev.isAltPressed && ev.key == Key.C -> {
            state.copySelected()
            true
        }

        ctrl && !ev.isAltPressed && ev.key == Key.X -> {
            state.cutSelected()
            true
        }

        ctrl && !ev.isAltPressed && ev.key == Key.V -> {
            state.paste()
            true
        }

        else -> {
            false
        }
    }
}

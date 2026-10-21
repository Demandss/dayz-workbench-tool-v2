package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
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

        ctrl && (ev.key == Key.Equals || ev.key == Key.Plus || ev.key == Key.NumPadAdd) -> {
            state.zoomLayoutBy(1.25f)
            true
        }

        ctrl && (ev.key == Key.Minus || ev.key == Key.NumPadSubtract) -> {
            state.zoomLayoutBy(0.8f)
            true
        }

        ctrl && (ev.key == Key.Zero || ev.key == Key.NumPad0) -> {
            state.zoomActual()
            true
        }

        ev.key == Key.F2 -> {
            state.requestRename()
            true
        }

        ctrl && ev.isAltPressed && ev.key == Key.C -> {
            state.copyAppearance()
            true
        }

        ctrl && ev.isAltPressed && ev.key == Key.V -> {
            state.pasteAppearance()
            true
        }

        ctrl && shift && ev.key == Key.G -> {
            state.ungroupSelected()
            true
        }

        ctrl && ev.key == Key.G -> {
            state.groupSelected()
            true
        }

        shift && !ctrl && ev.key == Key.A -> {
            state.groupSelected(WidgetType.WRAP_SPACER, "Auto")
            true
        }

        ev.key == Key.RightBracket -> {
            state.reorderSelected(if (ctrl) Reorder.FRONT else Reorder.FORWARD)
            true
        }

        ev.key == Key.LeftBracket -> {
            state.reorderSelected(if (ctrl) Reorder.BACK else Reorder.BACKWARD)
            true
        }

        shift && !ctrl && ev.key == Key.One -> {
            state.centerView()
            true
        }

        shift && !ctrl && ev.key == Key.Two -> {
            state.zoomToSelection()
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

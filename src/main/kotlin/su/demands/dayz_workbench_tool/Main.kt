package su.demands.dayz_workbench_tool

import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import su.demands.dayz_workbench_tool.manager.ModificationManager
import su.demands.dayz_workbench_tool.manager.ProcessManager
import su.demands.dayz_workbench_tool.state.AppState
import su.demands.dayz_workbench_tool.ui.MainWindow

fun main() = application {
    val state = remember { AppState() }

    remember {
        val hook = Thread {
            runCatching {
                ModificationManager.removeAll(state.mods.toList(), state.config.gamePath)
                ProcessManager.killTasks(state.config.autoKillTasks)
            }
        }
        Runtime.getRuntime().addShutdownHook(hook)
    }

    MainWindow(state, onCloseRequest = ::exitApplication)
}
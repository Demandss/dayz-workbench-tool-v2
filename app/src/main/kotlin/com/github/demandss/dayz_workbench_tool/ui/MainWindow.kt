package com.github.demandss.dayz_workbench_tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.manager.ModificationManager
import com.github.demandss.dayz_workbench_tool.manager.ProcessManager
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.ModsPanel
import com.github.demandss.dayz_workbench_tool.ui.components.PoweredByFooter
import com.github.demandss.dayz_workbench_tool.ui.components.ToolsPanel
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import com.github.demandss.dayz_workbench_tool.ui.theme.resolveTheme
import com.github.demandss.dayz_workbench_tool.util.SteamDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.imageio.ImageIO

private val appIcon: BitmapPainter by lazy {
    BitmapPainter(
        ImageIO
            .read(
                Thread
                    .currentThread()
                    .contextClassLoader!!
                    .getResourceAsStream("assets/logo/logo.png"),
            ).toComposeImageBitmap(),
    )
}

@Composable
fun MainWindow(
    state: AppState,
    onCloseRequest: () -> Unit,
) {
    val windowTitle =
        when (state.currentScreen) {
            Screen.MAIN -> "DayZ Workbench Tool"
            Screen.SETTINGS -> "DayZ Workbench Tool - ${stringResource(Res.string.settings)}"
            Screen.MODS -> "DayZ Workbench Tool - ${stringResource(Res.string.mods_setting)}"
        }
    val themeColors =
        remember(state.config.selectedTheme, state.config.customThemes) {
            resolveTheme(state.config.selectedTheme, state.config.customThemes)
        }

    val windowState =
        rememberWindowState(
            width = state.config.windowWidth.dp,
            height = state.config.windowHeight.dp,
        )

    LaunchedEffect(state.config.windowWidth, state.config.windowHeight) {
        val target = DpSize(state.config.windowWidth.dp, state.config.windowHeight.dp)
        if (windowState.size != target) windowState.size = target
    }

    Window(
        onCloseRequest = {
            val w = windowState.size.width.value
            val h = windowState.size.height.value
            if (w > 0f && h > 0f) {
                ConfigManager.App.save(state.config.copy(windowWidth = w, windowHeight = h))
            }
            onCloseRequest()
        },
        title = windowTitle,
        icon = appIcon,
        state = windowState,
        resizable = !state.config.lockWindowSize,
    ) {
        // The strings of the resources are read once per composition: a new language draws the content again, not the window
        key(I18n.language) {
            ScaledContent(enabled = state.config.adaptiveScale) {
                AppTheme(themeColors) {
                    Column(modifier = Modifier.fillMaxSize().background(FrameBackground)) {
                        when (state.currentScreen) {
                            Screen.MAIN -> MainContent(state)
                            Screen.SETTINGS -> SettingsScreen(state)
                            Screen.MODS -> ModsScreen(state)
                        }
                    }
                    if (!state.config.setupDone) {
                        InitialSetupDialog(state)
                    } else {
                        WorkDriveCheck(state, onCloseRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaledContent(
    enabled: Boolean = true,
    baseWidth: Float = 400f,
    baseHeight: Float = 400f,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val sysDensity = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val scale =
        if (containerSize.width > 0 && containerSize.height > 0) {
            val wDp = containerSize.width / sysDensity.density
            val hDp = containerSize.height / sysDensity.density
            minOf(wDp / baseWidth, hDp / baseHeight)
        } else {
            1f
        }
    CompositionLocalProvider(
        LocalDensity provides Density(sysDensity.density * scale, sysDensity.fontScale),
    ) {
        content()
    }
}

@Composable
private fun WorkDriveCheck(
    state: AppState,
    onClose: () -> Unit,
) {
    var missing by remember { mutableStateOf(false) }

    LaunchedEffect(state.config.workDrivePath) {
        val path = state.config.workDrivePath
        missing =
            if (path.isBlank()) {
                false
            } else {
                withContext(Dispatchers.IO) { !File(path).exists() }
            }
    }

    if (missing) {
        WorkDriveMissingDialog(
            path = state.config.workDrivePath,
            onSelect = { newPath ->
                state.updateConfig(state.config.copy(workDrivePath = newPath))
                missing = false
            },
            onClose = onClose,
        )
    }
}

@Composable
private fun WorkDriveMissingDialog(
    path: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = {}) {
        Column(
            modifier =
                Modifier
                    .width(320.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(str(Res.string.work_drive_not_found), color = TextForeground, fontSize = 13.sp)
            Text(
                str(Res.string.x_is_missing_or_not_mounted_select, path),
                color = GhostlyText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(str(Res.string.select_another), modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val picked = pickDirectory(str(Res.string.select_work_drive_folder), null)
                        picked?.let { onSelect(it) }
                    }
                })
                DarkButton(str(Res.string.close), modifier = Modifier.weight(1f), onClick = onClose)
            }
        }
    }
}

@Composable
private fun InitialSetupDialog(state: AppState) {
    var gamePath by remember { mutableStateOf(state.config.gamePath) }
    var workbenchPath by remember { mutableStateOf(state.config.workbenchPath) }
    var workDrive by remember { mutableStateOf(state.config.workDrivePath.ifBlank { "P:" }) }
    var scriptsPath by remember { mutableStateOf(state.config.scriptsPath.ifBlank { "P:\\scripts" }) }
    val detecting = stringResource(Res.string.detecting_steam_installations)
    var statusText by remember { mutableStateOf(detecting) }

    LaunchedEffect(Unit) {
        val detected = withContext(Dispatchers.IO) { SteamDetector.detect() }
        var found = false
        if (gamePath.isBlank() && detected.gamePath.isNotEmpty()) {
            gamePath = detected.gamePath
            found = true
        }
        if (workbenchPath.isBlank() && detected.workbenchPath.isNotEmpty()) {
            workbenchPath = detected.workbenchPath
            found = true
        }
        statusText = if (found) str(Res.string.paths_auto_detected_from_steam) else str(Res.string.steam_not_found_enter_paths_manually)
    }

    Dialog(onDismissRequest = {}) {
        Column(
            modifier =
                Modifier
                    .width(360.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(str(Res.string.initial_setup), color = TextForeground, fontSize = 13.sp)
            Text(statusText, color = GhostlyText, fontSize = 11.sp, lineHeight = 14.sp)

            SetupField(str(Res.string.game_folder_dayz), gamePath, isFile = false) { gamePath = it }
            SetupField(str(Res.string.workbench_folder_dayz_tools_bin), workbenchPath, isFile = false) { workbenchPath = it }
            SetupField(str(Res.string.work_drive_e_g_p), workDrive, isFile = false) { workDrive = it }
            SetupField(str(Res.string.scripts_path_e_g_p_scripts), scriptsPath, isFile = false) { scriptsPath = it }

            DarkButton(str(Res.string.confirm), modifier = Modifier.fillMaxWidth(), onClick = {
                state.updateConfig(
                    state.config.copy(
                        gamePath = gamePath,
                        workbenchPath = workbenchPath,
                        workDrivePath = workDrive,
                        scriptsPath = scriptsPath,
                        setupDone = true,
                    ),
                )
            })
        }
    }
}

@Composable
private fun SetupField(
    label: String,
    value: String,
    isFile: Boolean,
    onChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = GhostlyText, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .border(1.dp, SubSelectColor)
                        .background(ElementsBackground)
                        .padding(start = 4.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
                singleLine = true,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text("path..", color = GhostlyText, fontSize = 11.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(3.dp))
            val browseSource = remember { MutableInteractionSource() }
            val isBrowsePressed by browseSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .background(if (isBrowsePressed) SelectColor else ElementsBackground)
                        .border(1.dp, SubSelectColor)
                        .clickable(interactionSource = browseSource, indication = null) {
                            scope.launch {
                                val dir =
                                    value.takeIf { it.isNotBlank() }?.let {
                                        if (isFile) File(it).parent else it
                                    }
                                val picked =
                                    if (isFile) {
                                        pickFile(
                                            str(Res.string.select),
                                            "exe",
                                            dir,
                                        )
                                    } else {
                                        pickDirectory(str(Res.string.select_directory), dir)
                                    }
                                picked?.let { onChange(it) }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                AppIconView(AppIcon.FOLDER_OPEN, size = 14.dp, tint = TextForeground)
            }
        }
    }
}

@Composable
internal fun MainContent(state: AppState) {
    var toolsOpen by remember { mutableStateOf(false) }
    var showPluginDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DarkMenuBar(state) { toolsOpen = true }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                ToolsPanel(state)
                ModsPanel(state)
            }

            Spacer(Modifier.weight(1f))
            PoweredByFooter()
        }

        if (toolsOpen) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            toolsOpen = false
                        },
            )
            Column(
                modifier =
                    Modifier
                        .absoluteOffset(x = 0.dp, y = 24.dp)
                        .background(FrameBackground)
                        .border(1.dp, DarkenedBorder)
                        .width(160.dp),
            ) {
                ToolsMenuItem(stringResource(Res.string.stop_workbench)) {
                    ProcessManager.killTasks(listOf("workbenchApp.exe"))
                    toolsOpen = false
                }
                ToolsMenuItem(stringResource(Res.string.stop_all)) {
                    ProcessManager.killTasks(state.config.autoKillTasks)
                    toolsOpen = false
                }
                ToolsMenuItem(stringResource(Res.string.workbench_plugin)) {
                    toolsOpen = false
                    showPluginDialog = true
                }
                ToolsMenuItem(stringResource(Res.string.remove_mod_symlinks)) {
                    runCatching { ModificationManager.removeAll(state.mods.toList(), state.config.gamePath) }
                    toolsOpen = false
                }
            }
        }
    }

    if (showPluginDialog) {
        WorkbenchPluginDialog(state) { showPluginDialog = false }
    }
}

@Composable
private fun DarkMenuBar(
    state: AppState,
    onToolsOpen: () -> Unit,
) {
    val darkenedBorder = DarkenedBorder
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(FrameBackground)
                .drawBehind {
                    drawLine(darkenedBorder, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                },
    ) {
        MenuBarItem(stringResource(Res.string.settings)) { state.navigate(Screen.SETTINGS) }
        MenuBarItem(stringResource(Res.string.tools_title)) { onToolsOpen() }
    }
}

@Composable
private fun ToolsMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(if (isPressed) SelectColor else FrameBackground)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = TextForeground, fontSize = 10.sp)
    }
}

@Composable
private fun WorkbenchPluginDialog(
    state: AppState,
    onDismiss: () -> Unit,
) {
    // workDrivePath may point to a folder on the drive (P:\Modifications), the default lives in the drive root
    val driveRoot = runCatching { File(state.config.workDrivePath).toPath().root?.toString() }.getOrNull() ?: "P:\\"
    val defaultPath = driveRoot.trimEnd('\\', '/') + "\\DayZWorkbenchMod"
    var pluginPath by remember { mutableStateOf(state.config.workbenchPluginPath.ifBlank { defaultPath }) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(360.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(str(Res.string.workbench_plugin), color = TextForeground, fontSize = 13.sp)
            if (status.isNotBlank()) Text(status, color = GhostlyText, fontSize = 11.sp, lineHeight = 14.sp)

            SetupField(str(Res.string.plugin_mod_folder_config_cpp_scripts), pluginPath, isFile = false) { pluginPath = it }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(str(Res.string.install), modifier = Modifier.weight(1f), onClick = {
                    if (pluginPath.isBlank()) {
                        status = str(Res.string.select_a_folder_first)
                        return@DarkButton
                    }
                    status = str(Res.string.working)
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { installWorkbenchPlugin(File(pluginPath)) }
                        withContext(Dispatchers.Main) {
                            result
                                .onSuccess {
                                    state.updateConfig(state.config.copy(workbenchPluginPath = pluginPath))
                                    onDismiss()
                                }.onFailure { status = str(Res.string.failed_x, it.message.orEmpty()) }
                        }
                    }
                })
                DarkButton(str(Res.string.cancel), modifier = Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

private val workbenchPluginFiles =
    listOf(
        "StartDebugAll.c",
        "StartDebugClient.c",
        "StartDebugServer.c",
        "StopDebugAll.c",
    )

private val workbenchPluginConfig =
    """
    class CfgPatches
    {
    	class DayZWorkbenchTool
    	{
    		units[] = {};
    		weapons[] = {};
    		requiredVersion = 0.1;
    		requiredAddons[] = {"DZ_Scripts"};
    	};
    };
    """.trimIndent()

// Creates an unpacked mod (config.cpp + scripts) that is passed to Workbench via -mod
private fun installWorkbenchPlugin(modDir: File) {
    val pluginDir = File(modDir, "scripts\\editor\\plugins\\DayZWorkbenchTool")
    check(pluginDir.mkdirs() || pluginDir.isDirectory) { str(Res.string.cannot_create_x, pluginDir.path) }
    File(modDir, "config.cpp").writeText(workbenchPluginConfig)
    val cl = ProcessManager::class.java.classLoader
    for (name in workbenchPluginFiles) {
        val src = checkNotNull(cl?.getResourceAsStream("assets/DayZWorkbenchTool/$name")) { str(Res.string.missing_resource_x, name) }
        src.use { File(pluginDir, name).outputStream().use { dst -> it.copyTo(dst) } }
    }
}

@Composable
private fun MenuBarItem(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .background(if (isPressed) SelectColor else FrameBackground)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextForeground, fontSize = 10.sp)
    }
}

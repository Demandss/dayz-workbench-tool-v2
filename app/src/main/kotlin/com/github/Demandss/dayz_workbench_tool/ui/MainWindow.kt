package com.github.demandss.dayz_workbench_tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
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
import com.github.demandss.dayz_workbench_tool.manager.ModificationManager
import com.github.demandss.dayz_workbench_tool.manager.ProcessManager
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.ModsPanel
import com.github.demandss.dayz_workbench_tool.ui.components.PoweredByFooter
import com.github.demandss.dayz_workbench_tool.ui.components.ToolsPanel
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import com.github.demandss.dayz_workbench_tool.ui.theme.resolveTheme
import com.github.demandss.dayz_workbench_tool.util.SteamDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            Screen.SETTINGS -> "DayZ Workbench Tool - Settings"
            Screen.MODS -> "DayZ Workbench Tool - Mods Setting"
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
            Text("Work drive not found", color = TextForeground, fontSize = 13.sp)
            Text(
                "\"$path\" is missing or not mounted.\nSelect another folder or close the app.",
                color = GhostlyText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Select another", modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val picked = pickDirectory("Select work drive folder", null)
                        picked?.let { onSelect(it) }
                    }
                })
                DarkButton("Close", modifier = Modifier.weight(1f), onClick = onClose)
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
    var statusText by remember { mutableStateOf("Detecting Steam installations...") }

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
        statusText = if (found) "Paths auto-detected from Steam" else "Steam not found — enter paths manually"
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
            Text("Initial Setup", color = TextForeground, fontSize = 13.sp)
            Text(statusText, color = GhostlyText, fontSize = 11.sp, lineHeight = 14.sp)

            SetupField("Game folder (DayZ)", gamePath, isFile = false) { gamePath = it }
            SetupField("Workbench folder (DayZ Tools/Bin/Workbench)", workbenchPath, isFile = false) { workbenchPath = it }
            SetupField("Work drive (e.g. P:)", workDrive, isFile = false) { workDrive = it }
            SetupField("Scripts path (e.g. P:\\scripts)", scriptsPath, isFile = false) { scriptsPath = it }

            DarkButton("Confirm", modifier = Modifier.fillMaxWidth(), onClick = {
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
                                val picked = if (isFile) pickFile("Select", "exe", dir) else pickDirectory("Select directory", dir)
                                picked?.let { onChange(it) }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextForeground, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun MainContent(state: AppState) {
    var toolsOpen by remember { mutableStateOf(false) }
    var showFixScripts by remember { mutableStateOf(false) }

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
                ToolsMenuItem("stopWorkbench") {
                    ProcessManager.killTasks(listOf("workbenchApp.exe"))
                    toolsOpen = false
                }
                ToolsMenuItem("stopAll") {
                    ProcessManager.killTasks(state.config.autoKillTasks)
                    toolsOpen = false
                }
                ToolsMenuItem("fixScripts") {
                    toolsOpen = false
                    showFixScripts = true
                }
                ToolsMenuItem("remove mods mklink") {
                    runCatching { ModificationManager.removeAll(state.mods.toList(), state.config.gamePath) }
                    toolsOpen =
                        false
                }
            }
        }
    }

    if (showFixScripts) {
        FixScriptsDialog(state) { showFixScripts = false }
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
        MenuBarItem("Settings") { state.navigate(Screen.SETTINGS) }
        MenuBarItem("Tools") { onToolsOpen() }
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
private fun FixScriptsDialog(
    state: AppState,
    onDismiss: () -> Unit,
) {
    val defaultRoot = state.config.workDrivePath.trimEnd('\\', '/') + "\\scripts"
    var scriptsRoot by remember { mutableStateOf(defaultRoot) }
    var prefix by remember { mutableStateOf("DayZ") }
    var copyPlugin by remember { mutableStateOf(true) }
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
            Text("Fix Scripts", color = TextForeground, fontSize = 13.sp)
            if (status.isNotBlank()) Text(status, color = GhostlyText, fontSize = 11.sp, lineHeight = 14.sp)

            SetupField("Scripts folder", scriptsRoot, isFile = false) { scriptsRoot = it }
            SetupField("Prefix folder name", prefix, isFile = false) { prefix = it }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { copyPlugin = !copyPlugin },
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(13.dp)
                            .border(1.dp, SubSelectColor)
                            .background(if (copyPlugin) SelectColor else FrameBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    if (copyPlugin) Text("✓", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text("Install DayZWorkbenchTool plugin", color = TextForeground, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Fix", modifier = Modifier.weight(1f), onClick = {
                    status = "Working..."
                    scope.launch(Dispatchers.IO) {
                        runFixScripts(scriptsRoot, prefix, copyPlugin)
                        withContext(Dispatchers.Main) { onDismiss() }
                    }
                })
                DarkButton("Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
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

private fun runFixScripts(
    rootDir: String,
    prefix: String,
    copyPlugin: Boolean,
) {
    val subdirs =
        listOf(
            "1_Core",
            "2_GameLib",
            "3_Game",
            "4_World",
            "5_Mission",
            "editor\\Workbench",
            "editor\\plugins",
        )
    for (subdir in subdirs) {
        val dir = File(rootDir, subdir)
        if (!dir.exists()) continue
        val prefixDir = File(dir, prefix)
        prefixDir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (file.name != prefix) file.renameTo(File(prefixDir, file.name))
        }
    }
    if (copyPlugin) {
        val pluginDir = File(rootDir, "editor\\plugins\\DayZWorkbenchTool")
        pluginDir.mkdirs()
        val cl = ModificationManager::class.java.classLoader
        for (name in workbenchPluginFiles) {
            cl?.getResourceAsStream("assets/DayZWorkbenchTool/$name")?.use { src ->
                File(pluginDir, name).outputStream().use { dst -> src.copyTo(dst) }
            }
        }
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

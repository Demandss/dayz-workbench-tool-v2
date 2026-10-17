package com.github.demandss.dayz_workbench_tool.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.components.ColorPickerDialog
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkTabRow
import com.github.demandss.dayz_workbench_tool.ui.components.PoweredByFooter
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.stream.Collectors

@Composable
fun SettingsScreen(state: AppState) {
    var localConfig by remember { mutableStateOf(state.config) }
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: ← Back | Save | Reset
        val borderColor = DarkenedBorder
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(FrameBackground)
                    .drawBehind {
                        drawLine(borderColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                    },
        ) {
            SettingsMenuButton("← Back") { state.navigate(Screen.MAIN) }
            SettingsMenuButton("Save") {
                state.updateConfig(localConfig)
                state.navigate(Screen.MAIN)
            }
            SettingsMenuButton("Reset") { localConfig = AppConfig() }
        }
        HorizontalDivider(color = DarkenedBorder)

        DarkTabRow(
            tabs = listOf("Global", "Advanced", "Theme"),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        HorizontalDivider(color = DarkenedBorder)

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> GlobalTab(localConfig) { localConfig = it }
                1 -> AdvancedTab(localConfig) { localConfig = it }
                2 -> ThemeTab(localConfig) { localConfig = it }
            }
        }

        PoweredByFooter()
    }
}

@Composable
private fun SettingsMenuButton(
    text: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .background(if (isPressed) SelectColor else FrameBackground)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextForeground, fontSize = 10.sp)
    }
}

@Composable
private fun GlobalTab(
    config: AppConfig,
    onChange: (AppConfig) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SettingRow("Game folder") {
            PathField(value = config.gamePath, isFile = false) { onChange(config.copy(gamePath = it)) }
        }
        SettingRow("Work drive path") {
            PathField(value = config.workDrivePath, isFile = false) { onChange(config.copy(workDrivePath = it)) }
        }
        SettingRow("Workbench folder") {
            PathField(value = config.workbenchPath, isFile = false) { onChange(config.copy(workbenchPath = it)) }
        }
        SettingRow("Scripts path") {
            PathField(value = config.scriptsPath, isFile = false) { onChange(config.copy(scriptsPath = it)) }
        }
        SettingRow("Workbench plugin mod") {
            PathField(value = config.workbenchPluginPath, isFile = false) { onChange(config.copy(workbenchPluginPath = it)) }
        }
        CheckRow("Lock window size", config.lockWindowSize) { onChange(config.copy(lockWindowSize = it)) }
        CheckRow("Adaptive scaling", config.adaptiveScale) { onChange(config.copy(adaptiveScale = it)) }
        SettingRow("Window size") {
            DarkButton(
                text = "Reset to default (400 × 400)",
                onClick = { onChange(config.copy(windowWidth = 400f, windowHeight = 400f)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AdvancedTab(
    config: AppConfig,
    onChange: (AppConfig) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AdvancedRow("Client args", config.clientArgs, "exe args") { onChange(config.copy(clientArgs = it)) }
        AdvancedRow("Sandbox args", config.sandboxArgs, "exe args") { onChange(config.copy(sandboxArgs = it)) }
        AdvancedRow("Server args", config.serverArgs, "exe args") { onChange(config.copy(serverArgs = it)) }
        AdvancedRow("Workbench args", config.workbenchArgs, "exe args") { onChange(config.copy(workbenchArgs = it)) }
        CheckRow("Auto-skip Workbench assert dialogs", config.skipWorkbenchAsserts) { onChange(config.copy(skipWorkbenchAsserts = it)) }
        KillListRow(config.autoKillTasks) { onChange(config.copy(autoKillTasks = it)) }
        ScriptListRow(config.customWorkbenchScripts) { onChange(config.copy(customWorkbenchScripts = it)) }
    }
}

@Composable
private fun SettingRow(
    label: String,
    field: @Composable RowScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextForeground,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier.width(110.dp).padding(start = 5.dp),
        )
        field()
    }
}

@Composable
private fun RowScope.PathField(
    value: String,
    isFile: Boolean = true,
    onChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val browseSource = remember { MutableInteractionSource() }
    val isBrowsePressed by browseSource.collectIsPressedAsState()
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier =
                Modifier
                    .weight(1f)
                    .height(20.dp)
                    .border(1.dp, SubSelectColor)
                    .background(FrameBackground)
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
                            val picked = if (isFile) pickFile("Select executable", "exe", dir) else pickDirectory("Select directory", dir)
                            picked?.let { onChange(it) }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextForeground, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AdvancedRow(
    label: String,
    value: String,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingRow(label) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .border(1.dp, SubSelectColor)
                        .background(FrameBackground)
                        .padding(start = 4.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
                singleLine = true,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = GhostlyText, fontSize = 11.sp)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(3.dp))
            val editSource = remember { MutableInteractionSource() }
            val isEditPressed by editSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .background(if (isEditPressed) SelectColor else ElementsBackground)
                        .border(1.dp, SubSelectColor)
                        .clickable(interactionSource = editSource, indication = null) { showDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("<>", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
            }
        }
    }

    if (showDialog) {
        TextEditDialog(
            title = label,
            value = value,
            onDismiss = { showDialog = false },
            onSave = {
                onChange(it)
                showDialog = false
            },
        )
    }
}

@Composable
private fun KillListRow(
    tasks: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingRow("Auto kill list") {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .border(1.dp, SubSelectColor)
                        .background(FrameBackground)
                        .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (tasks.isEmpty()) "No tasks" else tasks.joinToString(", "),
                    color = if (tasks.isEmpty()) GhostlyText else TextForeground,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(3.dp))
            val editSource = remember { MutableInteractionSource() }
            val isEditPressed by editSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .background(if (isEditPressed) SelectColor else ElementsBackground)
                        .border(1.dp, SubSelectColor)
                        .clickable(interactionSource = editSource, indication = null) { showDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("<>", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
            }
        }
    }

    if (showDialog) {
        KillListDialog(
            tasks = tasks,
            onDismiss = { showDialog = false },
            onSave = {
                onChange(it)
                showDialog = false
            },
        )
    }
}

@Composable
private fun KillListDialog(
    tasks: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var localTasks by remember { mutableStateOf(tasks) }
    var showProcessPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Auto kill list", color = TextForeground, fontSize = 12.sp)

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 180.dp)
                        .border(1.dp, SubSelectColor)
                        .background(ElementsBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (localTasks.isEmpty()) {
                    Text("No tasks added", color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                } else {
                    localTasks.forEach { task ->
                        KillTaskItem(name = task, onRemove = { localTasks = localTasks - task })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("+ Browse .exe", modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val picked = pickFile("Select executable", "exe", null)
                        picked?.let {
                            val name = File(it).name
                            if (name !in localTasks) localTasks = localTasks + name
                        }
                    }
                })
                DarkButton("+ Running", modifier = Modifier.weight(1f), onClick = {
                    showProcessPicker = true
                })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Save", onClick = { onSave(localTasks) }, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showProcessPicker) {
        ProcessPickerDialog(
            existing = localTasks,
            onDismiss = { showProcessPicker = false },
            onSelect = { name ->
                if (name !in localTasks) localTasks = localTasks + name
                showProcessPicker = false
            },
        )
    }
}

@Composable
private fun KillTaskItem(
    name: String,
    onRemove: () -> Unit,
) {
    val rowSource = remember { MutableInteractionSource() }
    val isHovered by rowSource.collectIsHoveredAsState()
    val removeSource = remember { MutableInteractionSource() }
    val isRemovePressed by removeSource.collectIsPressedAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isHovered) SelectColor.copy(alpha = 0.3f) else Color.Transparent)
                .hoverable(rowSource)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            color = TextForeground,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .background(if (isRemovePressed) SelectColor else Color.Transparent)
                    .border(1.dp, SubSelectColor)
                    .clickable(interactionSource = removeSource, indication = null, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = TextForeground, fontSize = 12.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun ScriptListRow(
    scripts: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingRow("Workbench scripts") {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .border(1.dp, SubSelectColor)
                        .background(FrameBackground)
                        .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text =
                        if (scripts.isEmpty()) {
                            "Default (workbenchApp.exe)"
                        } else {
                            scripts.joinToString(", ") { File(it).name }
                        },
                    color = if (scripts.isEmpty()) GhostlyText else TextForeground,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(3.dp))
            val editSource = remember { MutableInteractionSource() }
            val isEditPressed by editSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .background(if (isEditPressed) SelectColor else ElementsBackground)
                        .border(1.dp, SubSelectColor)
                        .clickable(interactionSource = editSource, indication = null) { showDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("<>", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
            }
        }
    }

    if (showDialog) {
        ScriptListDialog(
            scripts = scripts,
            onDismiss = { showDialog = false },
            onSave = {
                onChange(it)
                showDialog = false
            },
        )
    }
}

@Composable
private fun ScriptListDialog(
    scripts: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var localScripts by remember { mutableStateOf(scripts) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Workbench scripts", color = TextForeground, fontSize = 12.sp)
            Text(
                "These launch alongside workbenchApp.exe when Workbench is clicked.",
                color = GhostlyText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 180.dp)
                        .border(1.dp, SubSelectColor)
                        .background(ElementsBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (localScripts.isEmpty()) {
                    Text("No scripts added", color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                } else {
                    localScripts.forEach { path ->
                        KillTaskItem(name = File(path).name, onRemove = { localScripts = localScripts - path })
                    }
                }
            }

            DarkButton("+ Browse .bat / .exe", modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    val picked = pickFile("Select script", "bat;exe", null)
                    picked?.let { if (it !in localScripts) localScripts = localScripts + it }
                }
            })

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Save", onClick = { onSave(localScripts) }, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProcessPickerDialog(
    existing: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var processes by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                processes =
                    ProcessHandle
                        .allProcesses()
                        .filter { it.info().command().isPresent }
                        .map { File(it.info().command().get()).name }
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList())
            } catch (_: Exception) {
            }
        }
    }

    val filtered = if (filter.isBlank()) processes else processes.filter { it.contains(filter, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Running processes", color = TextForeground, fontSize = 12.sp)

            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .border(1.dp, SubSelectColor)
                        .background(ElementsBackground)
                        .padding(start = 4.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
                singleLine = true,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (filter.isEmpty()) Text("Filter...", color = GhostlyText, fontSize = 11.sp)
                        inner()
                    }
                },
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, SubSelectColor)
                        .background(ElementsBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (processes.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Text("Loading...", color = GhostlyText, fontSize = 11.sp)
                    }
                } else {
                    filtered.forEach { proc ->
                        val alreadyAdded = proc in existing
                        val source = remember(proc) { MutableInteractionSource() }
                        val isHovered by source.collectIsHoveredAsState()
                        Text(
                            text = if (alreadyAdded) "$proc  ✓" else proc,
                            color = if (alreadyAdded) GhostlyText else TextForeground,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (isHovered && !alreadyAdded) SelectColor.copy(alpha = 0.5f) else Color.Transparent)
                                    .hoverable(source)
                                    .clickable(
                                        interactionSource = source,
                                        indication = null,
                                        enabled = !alreadyAdded,
                                        onClick = { onSelect(proc) },
                                    ).padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(label) {
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .border(1.dp, SubSelectColor)
                    .background(if (checked) SelectColor else FrameBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onChange(!checked) },
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
        }
    }
}

@Composable
private fun ThemeTab(
    config: AppConfig,
    onChange: (AppConfig) -> Unit,
) {
    var editingTheme by remember { mutableStateOf<ThemeDefinition?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Built-in", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(2.dp))
        BuiltinThemes.all.forEach { theme ->
            ThemeRow(
                theme = theme,
                isSelected = config.selectedTheme == theme.id,
                onSelect = { onChange(config.copy(selectedTheme = theme.id)) },
                onDuplicate = {
                    editingTheme =
                        theme.copy(
                            id = "custom_${System.currentTimeMillis()}",
                            name = "${theme.name} Copy",
                        )
                },
            )
        }

        Spacer(Modifier.height(6.dp))
        Text("Custom", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(2.dp))

        if (config.customThemes.isEmpty()) {
            Text("No custom themes", color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        }
        config.customThemes.forEach { theme ->
            ThemeRow(
                theme = theme,
                isSelected = config.selectedTheme == theme.id,
                onSelect = { onChange(config.copy(selectedTheme = theme.id)) },
                onDuplicate = {
                    editingTheme =
                        theme.copy(
                            id = "custom_${System.currentTimeMillis()}",
                            name = "${theme.name} Copy",
                        )
                },
                onEdit = { editingTheme = theme },
                onDelete = {
                    val remaining = config.customThemes - theme
                    val newSelected = if (config.selectedTheme == theme.id) "dark" else config.selectedTheme
                    onChange(config.copy(customThemes = remaining, selectedTheme = newSelected))
                },
            )
        }

        Spacer(Modifier.height(4.dp))
        DarkButton("+ New Theme", onClick = {
            val base =
                BuiltinThemes.findById(config.selectedTheme)
                    ?: config.customThemes.find { it.id == config.selectedTheme }
                    ?: BuiltinThemes.Dark
            editingTheme = base.copy(id = "custom_${System.currentTimeMillis()}", name = "My Theme")
        })
    }

    editingTheme?.let { theme ->
        val isNew = config.customThemes.none { it.id == theme.id }
        ThemeEditorDialog(
            initial = theme,
            onDismiss = { editingTheme = null },
            onSave = { updated ->
                val newThemes =
                    if (isNew) {
                        config.customThemes + updated
                    } else {
                        config.customThemes.map { if (it.id == updated.id) updated else it }
                    }
                onChange(config.copy(customThemes = newThemes))
                editingTheme = null
            },
        )
    }
}

@Composable
private fun ThemeRow(
    theme: ThemeDefinition,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val rowSource = remember { MutableInteractionSource() }
    val isHovered by rowSource.collectIsHoveredAsState()
    val themeColors = remember(theme) { theme.toAppColors() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .border(1.dp, if (isSelected) SubSelectColor else DarkenedBorder)
                .background(if (isHovered) ElementsBackground else FrameBackground)
                .hoverable(rowSource)
                .clickable(interactionSource = rowSource, indication = null, onClick = onSelect)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeSwatches(themeColors)
        Spacer(Modifier.width(4.dp))
        Text(theme.name, color = TextForeground, fontSize = 11.sp, modifier = Modifier.weight(1f))
        if (isSelected) Text("✓", color = SubSelectColor, fontSize = 10.sp)
        onDuplicate?.let { dup ->
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .background(if (pressed) SelectColor else Color.Transparent)
                        .clickable(interactionSource = src, indication = null, onClick = dup),
                contentAlignment = Alignment.Center,
            ) { Text("⧉", color = GhostlyText, fontSize = 10.sp) }
        }
        onEdit?.let { edit ->
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .background(if (pressed) SelectColor else Color.Transparent)
                        .clickable(interactionSource = src, indication = null, onClick = edit),
                contentAlignment = Alignment.Center,
            ) { Text("✎", color = GhostlyText, fontSize = 10.sp) }
        }
        onDelete?.let { del ->
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .background(if (pressed) SelectColor else Color.Transparent)
                        .clickable(interactionSource = src, indication = null, onClick = del),
                contentAlignment = Alignment.Center,
            ) { Text("×", color = GhostlyText, fontSize = 12.sp, lineHeight = 13.sp) }
        }
    }
}

@Composable
private fun ThemeSwatches(colors: AppColors) {
    listOf(
        colors.frameBackground,
        colors.elementsBackground,
        colors.selectColor,
        colors.subSelectColor,
        colors.textForeground,
    ).forEach { c ->
        Box(Modifier.size(12.dp).background(c).border(0.5.dp, DarkenedBorder))
    }
}

@Composable
private fun ThemeEditorDialog(
    initial: ThemeDefinition,
    onDismiss: () -> Unit,
    onSave: (ThemeDefinition) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var frameBackground by remember { mutableStateOf(initial.frameBackground) }
    var elementsBackground by remember { mutableStateOf(initial.elementsBackground) }
    var selectColor by remember { mutableStateOf(initial.selectColor) }
    var subSelectColor by remember { mutableStateOf(initial.subSelectColor) }
    var darkenedBorder by remember { mutableStateOf(initial.darkenedBorder) }
    var textForeground by remember { mutableStateOf(initial.textForeground) }
    var ghostlyText by remember { mutableStateOf(initial.ghostlyText) }
    var modIconClientSide by remember { mutableStateOf(initial.modIconClientSide) }
    var modIconServerSide by remember { mutableStateOf(initial.modIconServerSide) }
    var modIconEnabled by remember { mutableStateOf(initial.modIconEnabled) }
    var modIconDisabled by remember { mutableStateOf(initial.modIconDisabled) }
    var modIconWorkshop by remember { mutableStateOf(initial.modIconWorkshop) }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxDialogHeight = with(density) { (windowInfo.containerSize.height * 0.8f).toDp() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(300.dp)
                    .heightIn(max = maxDialogHeight)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Theme Editor", color = TextForeground, fontSize = 12.sp)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Name", color = GhostlyText, fontSize = 11.sp, modifier = Modifier.width(120.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(20.dp)
                            .border(1.dp, SubSelectColor)
                            .background(ElementsBackground)
                            .padding(start = 4.dp),
                    textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
                    singleLine = true,
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CollapsibleSection("UI Colors") {
                    ThemeColorRow("Frame Background", frameBackground) { frameBackground = it }
                    ThemeColorRow("Elements Background", elementsBackground) { elementsBackground = it }
                    ThemeColorRow("Selection", selectColor) { selectColor = it }
                    ThemeColorRow("Accent", subSelectColor) { subSelectColor = it }
                    ThemeColorRow("Border", darkenedBorder) { darkenedBorder = it }
                    ThemeColorRow("Text", textForeground) { textForeground = it }
                    ThemeColorRow("Dim Text", ghostlyText) { ghostlyText = it }
                }
                CollapsibleSection("Icons") {
                    ThemeColorRow("Client Side", modIconClientSide, "assets/images/mod_client_side.svg") { modIconClientSide = it }
                    ThemeColorRow("Server Side", modIconServerSide, "assets/images/mod_server_side.svg") { modIconServerSide = it }
                    ThemeColorRow("Enabled", modIconEnabled, "assets/images/mod_enabled.svg") { modIconEnabled = it }
                    ThemeColorRow("Disabled", modIconDisabled, "assets/images/mod_disebled.svg") { modIconDisabled = it }
                    ThemeColorRow("Workshop", modIconWorkshop, "assets/images/workshop.svg") { modIconWorkshop = it }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Save", onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { initial.name },
                            frameBackground = frameBackground,
                            elementsBackground = elementsBackground,
                            selectColor = selectColor,
                            subSelectColor = subSelectColor,
                            darkenedBorder = darkenedBorder,
                            textForeground = textForeground,
                            ghostlyText = ghostlyText,
                            modIconClientSide = modIconClientSide,
                            modIconServerSide = modIconServerSide,
                            modIconEnabled = modIconEnabled,
                            modIconDisabled = modIconDisabled,
                            modIconWorkshop = modIconWorkshop,
                        ),
                    )
                }, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        expanded = !expanded
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(if (expanded) "▾" else "▸", color = TextForeground, fontSize = 13.sp, lineHeight = 14.sp)
            Text(title, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = DarkenedBorder)
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun ThemeColorRow(
    label: String,
    colorLong: Long,
    iconRes: String? = null,
    onChange: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val color = remember(colorLong) { Color(colorLong.toInt()) }
    val swatchSource = remember { MutableInteractionSource() }
    val isSwatchPressed by swatchSource.collectIsPressedAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(120.dp))
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(if (iconRes == null) color else ElementsBackground)
                    .border(1.dp, if (isSwatchPressed) SubSelectColor else DarkenedBorder)
                    .clickable(interactionSource = swatchSource, indication = null) { showPicker = true },
            contentAlignment = Alignment.Center,
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text("#%06X".format(colorLong.toInt() and 0xFFFFFF), color = TextForeground, fontSize = 10.sp)
    }

    if (showPicker) {
        ColorPickerDialog(
            title = label,
            initial = color,
            onDismiss = { showPicker = false },
            onSave = { newColor ->
                onChange(newColor.toArgb().toLong() and 0xFFFFFFFFL)
                showPicker = false
            },
        )
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = TextForeground, fontSize = 12.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .border(1.dp, SubSelectColor)
                        .background(ElementsBackground)
                        .padding(4.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Save", onClick = { onSave(text) }, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

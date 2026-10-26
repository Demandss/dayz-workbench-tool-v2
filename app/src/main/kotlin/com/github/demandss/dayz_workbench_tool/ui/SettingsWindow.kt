package com.github.demandss.dayz_workbench_tool.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
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
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.config.withSettingsFrom
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.i18n.Language
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.DropdownField
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.layoutEditorSettingsSections
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.components.CheckRow
import com.github.demandss.dayz_workbench_tool.ui.components.ColorPickerDialog
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.PoweredByFooter
import com.github.demandss.dayz_workbench_tool.ui.components.SettingItem
import com.github.demandss.dayz_workbench_tool.ui.components.SettingRow
import com.github.demandss.dayz_workbench_tool.ui.components.SettingsGroup
import com.github.demandss.dayz_workbench_tool.ui.components.SettingsPages
import com.github.demandss.dayz_workbench_tool.ui.components.SettingsSection
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.util.stream.Collectors

@Composable
fun SettingsScreen(state: AppState) {
    var localConfig by remember { mutableStateOf(state.config) }
    // A local copy of the settings of the layout editor, stored by Save like the rest
    val editorBase = remember { ConfigManager.LayoutEditor.load() }
    var localEditor by remember { mutableStateOf(editorBase) }

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
            SettingsMenuButton(stringResource(Res.string.back)) { state.navigate(Screen.MAIN) }
            SettingsMenuButton(stringResource(Res.string.save)) {
                state.updateConfig(localConfig)
                state.saveLayoutEditorSettings(editorBase, localEditor)
                state.navigate(Screen.MAIN)
            }
            SettingsMenuButton(stringResource(Res.string.reset)) {
                localConfig = AppConfig()
                localEditor = editorBase.withSettingsFrom(LayoutEditorConfig())
            }
        }
        HorizontalDivider(color = DarkenedBorder)

        SettingsPages(
            groups = settingsGroups(localConfig, { localConfig = it }, localEditor, { localEditor = it }),
            group = state.settingsGroup,
            onGroup = { state.settingsGroup = it },
            section = state.settingsSection,
            onSection = { state.settingsSection = it },
            modifier = Modifier.weight(1f),
        )

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
private fun settingsGroups(
    config: AppConfig,
    onConfig: (AppConfig) -> Unit,
    editor: LayoutEditorConfig,
    onEditor: (LayoutEditorConfig) -> Unit,
): List<SettingsGroup> =
    listOf(
        SettingsGroup(stringResource(Res.string.settings_global), globalSections(config, onConfig)),
        SettingsGroup(stringResource(Res.string.settings_app), appSections(config, onConfig)),
        SettingsGroup("Layout Editor", layoutEditorSettingsSections(editor, onEditor)),
    )

/** What both programs use: the language, the theme and the folders of the game and of the work drive. */
@Composable
private fun globalSections(
    config: AppConfig,
    onChange: (AppConfig) -> Unit,
): List<SettingsSection> {
    val language = stringResource(Res.string.language)
    val theme = stringResource(Res.string.theme)
    val game = stringResource(Res.string.game_folder)
    val workDrive = stringResource(Res.string.work_drive)
    val system = Language.ofSystem()
    val sameAsSystem = stringResource(Res.string.same_as_the_system_x, system.nativeName)
    return listOf(
        SettingsSection(
            id = "interface",
            title = stringResource(Res.string.section_interface),
            items =
                listOf(
                    SettingItem(Res.string.language) {
                        SettingRow(language) {
                            DropdownField(
                                config.language ?: I18n.SYSTEM,
                                listOf(I18n.SYSTEM) + Language.entries.map { it.code },
                                { code -> if (code == I18n.SYSTEM) sameAsSystem else Language.byCode(code)?.nativeName ?: code },
                            ) { onChange(config.copy(language = it)) }
                        }
                    },
                ),
        ),
        SettingsSection(
            id = "theme",
            title = theme,
            items =
                listOf(
                    SettingItem(Res.string.theme, listOf(Res.string.built_in, Res.string.custom)) {
                        ThemeTab(config, onChange)
                    },
                ),
        ),
        SettingsSection(
            id = "folders",
            title = stringResource(Res.string.section_folders),
            items =
                listOf(
                    SettingItem(Res.string.game_folder) {
                        SettingRow(game) { PathField(value = config.gamePath, isFile = false) { onChange(config.copy(gamePath = it)) } }
                    },
                    SettingItem(Res.string.work_drive) {
                        SettingRow(workDrive) {
                            PathField(value = config.workDrivePath, isFile = false) { onChange(config.copy(workDrivePath = it)) }
                        }
                    },
                ),
        ),
    )
}

/** The launcher itself: Workbench, the game, the processes to close and the window. */
@Composable
private fun appSections(
    config: AppConfig,
    onChange: (AppConfig) -> Unit,
): List<SettingsSection> {
    val workbench = stringResource(Res.string.workbench_folder)
    val scripts = stringResource(Res.string.scripts_path)
    val plugin = stringResource(Res.string.workbench_plugin_mod)
    val workbenchArgs = stringResource(Res.string.workbench_args)
    val asserts = stringResource(Res.string.auto_skip_workbench_assert_dialogs)
    val workbenchScripts = stringResource(Res.string.workbench_scripts)
    val clientArgs = stringResource(Res.string.client_args)
    val sandboxArgs = stringResource(Res.string.sandbox_args)
    val serverArgs = stringResource(Res.string.server_args)
    val exeArgs = stringResource(Res.string.exe_args)
    val killList = stringResource(Res.string.auto_kill_list)
    val lock = stringResource(Res.string.lock_window_size)
    val adaptive = stringResource(Res.string.adaptive_scaling)
    val size = stringResource(Res.string.window_size)
    val updates = stringResource(Res.string.check_for_updates)
    return listOf(
        SettingsSection(
            id = "workbench",
            title = "Workbench",
            items =
                listOf(
                    SettingItem(Res.string.workbench_folder) {
                        SettingRow(workbench) {
                            PathField(value = config.workbenchPath, isFile = false) { onChange(config.copy(workbenchPath = it)) }
                        }
                    },
                    SettingItem(Res.string.scripts_path) {
                        SettingRow(scripts) {
                            PathField(value = config.scriptsPath, isFile = false) { onChange(config.copy(scriptsPath = it)) }
                        }
                    },
                    SettingItem(Res.string.workbench_plugin_mod) {
                        SettingRow(plugin) {
                            PathField(
                                value = config.workbenchPluginPath,
                                isFile = false,
                            ) { onChange(config.copy(workbenchPluginPath = it)) }
                        }
                    },
                    SettingItem(Res.string.workbench_args) {
                        AdvancedRow(workbenchArgs, config.workbenchArgs, exeArgs) { onChange(config.copy(workbenchArgs = it)) }
                    },
                    SettingItem(Res.string.auto_skip_workbench_assert_dialogs) {
                        CheckRow(asserts, config.skipWorkbenchAsserts) { onChange(config.copy(skipWorkbenchAsserts = it)) }
                    },
                    SettingItem(Res.string.workbench_scripts) {
                        ScriptListRow(config.customWorkbenchScripts) { onChange(config.copy(customWorkbenchScripts = it)) }
                    },
                ),
        ),
        SettingsSection(
            id = "game",
            title = stringResource(Res.string.section_game),
            items =
                listOf(
                    SettingItem(Res.string.client_args) {
                        AdvancedRow(clientArgs, config.clientArgs, exeArgs) { onChange(config.copy(clientArgs = it)) }
                    },
                    SettingItem(Res.string.sandbox_args) {
                        AdvancedRow(sandboxArgs, config.sandboxArgs, exeArgs) { onChange(config.copy(sandboxArgs = it)) }
                    },
                    SettingItem(Res.string.server_args) {
                        AdvancedRow(serverArgs, config.serverArgs, exeArgs) { onChange(config.copy(serverArgs = it)) }
                    },
                ),
        ),
        SettingsSection(
            id = "processes",
            title = stringResource(Res.string.section_processes),
            items =
                listOf(
                    SettingItem(
                        Res.string.auto_kill_list,
                    ) { KillListRow(config.autoKillTasks) { onChange(config.copy(autoKillTasks = it)) } },
                ),
        ),
        SettingsSection(
            id = "updates",
            title = stringResource(Res.string.section_updates),
            items =
                listOf(
                    SettingItem(
                        Res.string.check_for_updates,
                    ) { CheckRow(updates, config.checkForUpdates) { onChange(config.copy(checkForUpdates = it)) } },
                ),
        ),
        SettingsSection(
            id = "window",
            title = stringResource(Res.string.window),
            items =
                listOf(
                    SettingItem(
                        Res.string.lock_window_size,
                    ) { CheckRow(lock, config.lockWindowSize) { onChange(config.copy(lockWindowSize = it)) } },
                    SettingItem(
                        Res.string.adaptive_scaling,
                    ) { CheckRow(adaptive, config.adaptiveScale) { onChange(config.copy(adaptiveScale = it)) } },
                    SettingItem(Res.string.window_size) {
                        SettingRow(size) {
                            DarkButton(
                                text = stringResource(Res.string.reset_to_default_400_400),
                                onClick = { onChange(config.copy(windowWidth = 400f, windowHeight = 400f)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    },
                ),
        ),
    )
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
                            val picked =
                                if (isFile) {
                                    pickFile(
                                        str(Res.string.select_executable),
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

    SettingRow(stringResource(Res.string.auto_kill_list)) {
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
                    text = if (tasks.isEmpty()) stringResource(Res.string.no_tasks) else tasks.joinToString(", "),
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
            Text(str(Res.string.auto_kill_list), color = TextForeground, fontSize = 12.sp)

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
                    Text(str(Res.string.no_tasks_added), color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                } else {
                    localTasks.forEach { task ->
                        KillTaskItem(name = task, onRemove = { localTasks = localTasks - task })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(str(Res.string.browse_exe), modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val picked = pickFile(str(Res.string.select_executable), "exe", null)
                        picked?.let {
                            val name = File(it).name
                            if (name !in localTasks) localTasks = localTasks + name
                        }
                    }
                })
                DarkButton(str(Res.string.running), modifier = Modifier.weight(1f), onClick = {
                    showProcessPicker = true
                })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(str(Res.string.save), onClick = { onSave(localTasks) }, modifier = Modifier.weight(1f))
                DarkButton(str(Res.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
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

    SettingRow(stringResource(Res.string.workbench_scripts)) {
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
                            stringResource(Res.string.default_workbenchapp_exe)
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
            Text(str(Res.string.workbench_scripts), color = TextForeground, fontSize = 12.sp)
            Text(
                str(Res.string.these_launch_alongside_workbenchapp_exe),
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
                    Text(str(Res.string.no_scripts_added), color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                } else {
                    localScripts.forEach { path ->
                        KillTaskItem(name = File(path).name, onRemove = { localScripts = localScripts - path })
                    }
                }
            }

            DarkButton(str(Res.string.browse_bat_exe), modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    val picked = pickFile(str(Res.string.select_script), "bat;exe", null)
                    picked?.let { if (it !in localScripts) localScripts = localScripts + it }
                }
            })

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(str(Res.string.save), onClick = { onSave(localScripts) }, modifier = Modifier.weight(1f))
                DarkButton(str(Res.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
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
            Text(str(Res.string.running_processes), color = TextForeground, fontSize = 12.sp)

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
                        if (filter.isEmpty()) Text(str(Res.string.filter), color = GhostlyText, fontSize = 11.sp)
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
                        Text(str(Res.string.loading), color = GhostlyText, fontSize = 11.sp)
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

            DarkButton(str(Res.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(stringResource(Res.string.built_in), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
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
                            name = str(Res.string.x_copy, theme.name),
                        )
                },
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.custom), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(2.dp))

        if (config.customThemes.isEmpty()) {
            Text(
                stringResource(Res.string.no_custom_themes),
                color = GhostlyText,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
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
                            name = str(Res.string.x_copy, theme.name),
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
        DarkButton(stringResource(Res.string.new_theme), onClick = {
            val base =
                BuiltinThemes.findById(config.selectedTheme)
                    ?: config.customThemes.find { it.id == config.selectedTheme }
                    ?: BuiltinThemes.Dark
            editingTheme = base.copy(id = "custom_${System.currentTimeMillis()}", name = str(Res.string.my_theme))
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
            Text(str(Res.string.theme_editor), color = TextForeground, fontSize = 12.sp)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(str(Res.string.name), color = GhostlyText, fontSize = 11.sp, modifier = Modifier.width(120.dp))
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
                CollapsibleSection(str(Res.string.ui_colors)) {
                    ThemeColorRow(str(Res.string.frame_background), frameBackground) { frameBackground = it }
                    ThemeColorRow(str(Res.string.elements_background), elementsBackground) { elementsBackground = it }
                    ThemeColorRow(str(Res.string.selection), selectColor) { selectColor = it }
                    ThemeColorRow(str(Res.string.accent), subSelectColor) { subSelectColor = it }
                    ThemeColorRow(str(Res.string.border), darkenedBorder) { darkenedBorder = it }
                    ThemeColorRow(str(Res.string.text), textForeground) { textForeground = it }
                    ThemeColorRow(str(Res.string.dim_text), ghostlyText) { ghostlyText = it }
                }
                CollapsibleSection(str(Res.string.icons)) {
                    ThemeColorRow(
                        str(Res.string.client_side),
                        modIconClientSide,
                        "assets/images/mod_client_side.svg",
                    ) { modIconClientSide = it }
                    ThemeColorRow(
                        str(Res.string.server_side),
                        modIconServerSide,
                        "assets/images/mod_server_side.svg",
                    ) { modIconServerSide = it }
                    ThemeColorRow(str(Res.string.enabled), modIconEnabled, "assets/images/mod_enabled.svg") { modIconEnabled = it }
                    ThemeColorRow(str(Res.string.disabled), modIconDisabled, "assets/images/mod_disebled.svg") { modIconDisabled = it }
                    ThemeColorRow("Workshop", modIconWorkshop, "assets/images/workshop.svg") { modIconWorkshop = it }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(str(Res.string.save), onClick = {
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
                DarkButton(str(Res.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
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
                DarkButton(str(Res.string.save), onClick = { onSave(text) }, modifier = Modifier.weight(1f))
                DarkButton(str(Res.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

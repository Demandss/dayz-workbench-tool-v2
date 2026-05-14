package su.demands.dayz_workbench_tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.util.stream.Collectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.demands.dayz_workbench_tool.config.AppConfig
import su.demands.dayz_workbench_tool.state.AppState
import su.demands.dayz_workbench_tool.state.Screen
import su.demands.dayz_workbench_tool.ui.components.DarkButton
import su.demands.dayz_workbench_tool.ui.components.DarkTabRow
import su.demands.dayz_workbench_tool.ui.components.PoweredByFooter
import su.demands.dayz_workbench_tool.ui.theme.*

@Composable
fun SettingsScreen(state: AppState) {
    var localConfig by remember { mutableStateOf(state.config) }
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: ← Back | Save | Reset
        val borderColor = DarkenedBorder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(FrameBackground)
                .drawBehind {
                    drawLine(borderColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                }
        ) {
            SettingsMenuButton("← Back")  { state.navigate(Screen.MAIN) }
            SettingsMenuButton("Save")    { state.updateConfig(localConfig); state.navigate(Screen.MAIN) }
            SettingsMenuButton("Reset")   { localConfig = AppConfig() }
        }
        HorizontalDivider(color = DarkenedBorder)

        DarkTabRow(
            tabs = listOf("Global", "Advanced"),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )
        HorizontalDivider(color = DarkenedBorder)

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> GlobalTab(localConfig) { localConfig = it }
                1 -> AdvancedTab(localConfig) { localConfig = it }
            }
        }

        PoweredByFooter()
    }
}

@Composable
private fun SettingsMenuButton(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(if (isPressed) SelectColor else FrameBackground)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextForeground, fontSize = 10.sp)
    }
}

@Composable
private fun GlobalTab(config: AppConfig, onChange: (AppConfig) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        SettingRow("Game path") {
            PathField(value = config.gamePath, isFile = true) { onChange(config.copy(gamePath = it)) }
        }
        SettingRow("Work drive path") {
            PathField(value = config.workDrivePath, isFile = false) { onChange(config.copy(workDrivePath = it)) }
        }
        SettingRow("Workbench path") {
            PathField(value = config.workbenchPath, isFile = true) { onChange(config.copy(workbenchPath = it)) }
        }
    }
}

@Composable
private fun AdvancedTab(config: AppConfig, onChange: (AppConfig) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AdvancedRow("Client args", config.clientArgs, "exe args") { onChange(config.copy(clientArgs = it)) }
        AdvancedRow("Sandbox args", config.sandboxArgs, "exe args") { onChange(config.copy(sandboxArgs = it)) }
        AdvancedRow("Server args", config.serverArgs, "exe args") { onChange(config.copy(serverArgs = it)) }
        KillListRow(config.autoKillTasks) { onChange(config.copy(autoKillTasks = it)) }
    }
}

@Composable
private fun SettingRow(label: String, field: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextForeground,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier.width(110.dp).padding(start = 5.dp)
        )
        field()
    }
}

@Composable
private fun RowScope.PathField(value: String, isFile: Boolean = true, onChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val browseSource = remember { MutableInteractionSource() }
    val isBrowsePressed by browseSource.collectIsPressedAsState()
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
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
            }
        )
        Spacer(Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (isBrowsePressed) SelectColor else ElementsBackground)
                .border(1.dp, SubSelectColor)
                .clickable(interactionSource = browseSource, indication = null) {
                    scope.launch {
                        val dir = value.takeIf { it.isNotBlank() }?.let {
                            if (isFile) File(it).parent else it
                        }
                        val picked = if (isFile) pickFile("Select executable", "exe", dir) else pickDirectory("Select directory", dir)
                        picked?.let { onChange(it) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextForeground, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AdvancedRow(label: String, value: String, placeholder: String = "", onChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    SettingRow(label) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
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
                }
            )
            Spacer(Modifier.width(3.dp))
            val editSource = remember { MutableInteractionSource() }
            val isEditPressed by editSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (isEditPressed) SelectColor else ElementsBackground)
                    .border(1.dp, SubSelectColor)
                    .clickable(interactionSource = editSource, indication = null) { showDialog = true },
                contentAlignment = Alignment.Center
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
            onSave = { onChange(it); showDialog = false }
        )
    }
}

@Composable
private fun KillListRow(tasks: List<String>, onChange: (List<String>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    SettingRow("Auto kill list") {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .border(1.dp, SubSelectColor)
                    .background(FrameBackground)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (tasks.isEmpty()) "No tasks" else tasks.joinToString(", "),
                    color = if (tasks.isEmpty()) GhostlyText else TextForeground,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(3.dp))
            val editSource = remember { MutableInteractionSource() }
            val isEditPressed by editSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (isEditPressed) SelectColor else ElementsBackground)
                    .border(1.dp, SubSelectColor)
                    .clickable(interactionSource = editSource, indication = null) { showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text("<>", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
            }
        }
    }

    if (showDialog) {
        KillListDialog(
            tasks = tasks,
            onDismiss = { showDialog = false },
            onSave = { onChange(it); showDialog = false }
        )
    }
}

@Composable
private fun KillListDialog(tasks: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var localTasks by remember { mutableStateOf(tasks) }
    var showProcessPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Auto kill list", color = TextForeground, fontSize = 12.sp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 180.dp)
                    .border(1.dp, SubSelectColor)
                    .background(ElementsBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
            }
        )
    }
}

@Composable
private fun KillTaskItem(name: String, onRemove: () -> Unit) {
    val rowSource = remember { MutableInteractionSource() }
    val isHovered by rowSource.collectIsHoveredAsState()
    val removeSource = remember { MutableInteractionSource() }
    val isRemovePressed by removeSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHovered) SelectColor.copy(alpha = 0.3f) else Color.Transparent)
            .hoverable(rowSource)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = TextForeground,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(if (isRemovePressed) SelectColor else Color.Transparent)
                .border(1.dp, SubSelectColor)
                .clickable(interactionSource = removeSource, indication = null, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = TextForeground, fontSize = 12.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun ProcessPickerDialog(existing: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var processes by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                processes = ProcessHandle.allProcesses()
                    .filter { it.info().command().isPresent }
                    .map { File(it.info().command().get()).name }
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList())
            } catch (_: Exception) {}
        }
    }

    val filtered = if (filter.isBlank()) processes else processes.filter { it.contains(filter, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Running processes", color = TextForeground, fontSize = 12.sp)

            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
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
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, SubSelectColor)
                    .background(ElementsBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isHovered && !alreadyAdded) SelectColor.copy(alpha = 0.5f) else Color.Transparent)
                                .hoverable(source)
                                .clickable(
                                    interactionSource = source,
                                    indication = null,
                                    enabled = !alreadyAdded,
                                    onClick = { onSelect(proc) }
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TextEditDialog(title: String, value: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = TextForeground, fontSize = 12.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .border(1.dp, SubSelectColor)
                    .background(ElementsBackground)
                    .padding(4.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Save",   onClick = { onSave(text) },  modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss,         modifier = Modifier.weight(1f))
            }
        }
    }
}
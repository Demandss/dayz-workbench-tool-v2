package su.demands.dayz_workbench_tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.demands.dayz_workbench_tool.model.Modification
import su.demands.dayz_workbench_tool.model.ModSide
import su.demands.dayz_workbench_tool.state.AppState
import su.demands.dayz_workbench_tool.state.Screen
import su.demands.dayz_workbench_tool.ui.components.DarkButton
import su.demands.dayz_workbench_tool.ui.components.ModListPanel
import su.demands.dayz_workbench_tool.ui.components.PoweredByFooter
import su.demands.dayz_workbench_tool.ui.theme.*
import java.io.File

private enum class ModFilter { ALL, CLIENT, SERVER }

private data class PendingAction(
    val title: String,
    val mods: List<Modification>,
    val onConfirm: () -> Unit,
)

@Composable
fun ModsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(ModFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var filterMenuOpen by remember { mutableStateOf(false) }

    var selectedMods by remember { mutableStateOf(emptySet<Modification>()) }
    var selectionAnchor by remember { mutableStateOf<Modification?>(null) }
    var infoMod by remember { mutableStateOf<Modification?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    var missingPaths by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(state.mods.toList()) {
        missingPaths = withContext(Dispatchers.IO) {
            state.mods.filter { !File(it.path).exists() }.map { it.path }.toSet()
        }
    }

    val visibleMods = remember(state.mods.toList(), filter, search) {
        state.mods
            .filter {
                when (filter) {
                    ModFilter.ALL    -> true
                    ModFilter.CLIENT -> it.side == ModSide.CLIENT
                    ModFilter.SERVER -> it.side == ModSide.SERVER
                }
            }
            .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
    }

    LaunchedEffect(filter, search) {
        selectedMods = emptySet()
        selectionAnchor = null
    }

    fun handleSelect(mod: Modification, isShift: Boolean, isCtrl: Boolean) {
        when {
            isShift && selectionAnchor != null -> {
                val a = visibleMods.indexOfFirst { it.path == selectionAnchor!!.path && it.side == selectionAnchor!!.side }
                val b = visibleMods.indexOfFirst { it.path == mod.path && it.side == mod.side }
                if (a >= 0 && b >= 0) {
                    val lo = minOf(a, b); val hi = maxOf(a, b)
                    selectedMods = visibleMods.subList(lo, hi + 1).toSet()
                }
            }
            isCtrl -> {
                selectedMods = if (mod in selectedMods) selectedMods - mod else selectedMods + mod
                selectionAnchor = mod
            }
            else -> {
                selectedMods = if (selectedMods.size == 1 && mod in selectedMods) emptySet() else setOf(mod)
                selectionAnchor = mod
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FrameBackground)) {
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
            val backSource = remember { MutableInteractionSource() }
            val isBackPressed by backSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(if (isBackPressed) SelectColor else FrameBackground)
                    .clickable(interactionSource = backSource, indication = null) { state.navigate(Screen.MAIN) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("← Back", color = TextForeground, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))

            Box {
                val filterSource = remember { MutableInteractionSource() }
                val isFilterPressed by filterSource.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (isFilterPressed || filterMenuOpen) SelectColor else FrameBackground)
                        .clickable(interactionSource = filterSource, indication = null) { filterMenuOpen = true }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Filter: ${filter.name}", color = TextForeground, fontSize = 10.sp)
                }
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false },
                    modifier = Modifier.background(FrameBackground).border(1.dp, DarkenedBorder)
                ) {
                    ModFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.name, color = TextForeground, fontSize = 10.sp) },
                            onClick = { filter = f; filterMenuOpen = false },
                            modifier = Modifier.background(FrameBackground).height(28.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = DarkenedBorder)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, SubSelectColor)
                    .background(FrameBackground)
                    .padding(start = 5.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 10.sp),
                singleLine = true,
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (search.isEmpty()) Text("search mods..", color = GhostlyText, fontSize = 10.sp)
                        inner()
                    }
                }
            )
        }
        HorizontalDivider(color = DarkenedBorder)

        ModListPanel(
            mods = visibleMods,
            onToggleEnabled = { state.toggleEnabled(it) },
            onSwitchSide = { state.switchSide(it) },
            modifier = Modifier.weight(1f).padding(horizontal = 5.dp, vertical = 3.dp),
            selectedMods = selectedMods,
            onSelect = { mod, isShift, isCtrl -> handleSelect(mod, isShift, isCtrl) },
            onDoubleClick = { mod -> infoMod = state.mods.find { it.path == mod.path && it.side == mod.side } },
            missingPaths = missingPaths,
            contextMenuContent = { clickedMod, dismiss ->
                val targets = if (clickedMod in selectedMods) selectedMods else setOf(clickedMod)
                val liveMods = state.mods.filter { m -> targets.any { t -> t.path == m.path && t.side == m.side } }
                val showEnable = liveMods.any { !it.enabled }
                val showDisable = liveMods.any { it.enabled }

                if (showEnable) {
                    DropdownMenuItem(
                        text = { Text("Enable", color = TextForeground, fontSize = 10.sp) },
                        onClick = {
                            dismiss()
                            val toEnable = liveMods.filter { !it.enabled }
                            pendingAction = PendingAction(
                                title = "Enable mods",
                                mods = toEnable,
                                onConfirm = {
                                    toEnable.forEach { state.toggleEnabled(it) }
                                    selectedMods = emptySet()
                                }
                            )
                        },
                        modifier = Modifier.background(FrameBackground).height(28.dp)
                    )
                }
                if (showDisable) {
                    DropdownMenuItem(
                        text = { Text("Disable", color = TextForeground, fontSize = 10.sp) },
                        onClick = {
                            dismiss()
                            val toDisable = liveMods.filter { it.enabled }
                            pendingAction = PendingAction(
                                title = "Disable mods",
                                mods = toDisable,
                                onConfirm = {
                                    toDisable.forEach { state.toggleEnabled(it) }
                                    selectedMods = emptySet()
                                }
                            )
                        },
                        modifier = Modifier.background(FrameBackground).height(28.dp)
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove", color = TextForeground, fontSize = 10.sp) },
                    onClick = {
                        dismiss()
                        pendingAction = PendingAction(
                            title = "Remove mods",
                            mods = liveMods,
                            onConfirm = {
                                liveMods.forEach { state.removeMod(it) }
                                selectedMods = emptySet()
                            }
                        )
                    },
                    modifier = Modifier.background(FrameBackground).height(28.dp)
                )
                if (targets.size == 1) {
                    val singleMod = liveMods.firstOrNull()
                    if (singleMod != null && File(singleMod.path).exists()) {
                        DropdownMenuItem(
                            text = { Text("Open location", color = TextForeground, fontSize = 10.sp) },
                            onClick = {
                                dismiss()
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        if (java.awt.Desktop.isDesktopSupported())
                                            java.awt.Desktop.getDesktop().open(File(singleMod.path))
                                    }
                                }
                            },
                            modifier = Modifier.background(FrameBackground).height(28.dp)
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Info", color = TextForeground, fontSize = 10.sp) },
                        onClick = { infoMod = liveMods.firstOrNull(); dismiss() },
                        modifier = Modifier.background(FrameBackground).height(28.dp)
                    )
                }
            }
        )

        PoweredByFooter()
    }

    pendingAction?.let { action ->
        ConfirmDialog(action = action, onDismiss = { pendingAction = null })
    }

    infoMod?.let { mod ->
        ModInfoDialog(
            mod = mod,
            isMissing = mod.path in missingPaths,
            onDismiss = { infoMod = null },
            onToggleEnabled = {
                state.mods.find { it.path == mod.path && it.side == mod.side }?.let { live ->
                    infoMod = null
                    pendingAction = PendingAction(
                        title = if (live.enabled) "Disable mod" else "Enable mod",
                        mods = listOf(live),
                        onConfirm = { state.toggleEnabled(live) }
                    )
                }
            },
            onSwitchSide = {
                state.mods.find { it.path == mod.path && it.side == mod.side }?.let { live ->
                    infoMod = null
                    pendingAction = PendingAction(
                        title = if (live.side == ModSide.CLIENT) "Move to server" else "Move to client",
                        mods = listOf(live),
                        onConfirm = { state.switchSide(live) }
                    )
                }
            },
            onRemove = {
                state.mods.find { it.path == mod.path && it.side == mod.side }?.let { live ->
                    infoMod = null
                    pendingAction = PendingAction(
                        title = "Remove mod",
                        mods = listOf(live),
                        onConfirm = { state.removeMod(live); selectedMods = emptySet() }
                    )
                }
            },
            onOpenLocation = {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        if (java.awt.Desktop.isDesktopSupported())
                            java.awt.Desktop.getDesktop().open(File(mod.path))
                    }
                }
            }
        )
    }
}

@Composable
private fun ConfirmDialog(action: PendingAction, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(action.title, color = TextForeground, fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                action.mods.forEach { mod ->
                    Text("• ${mod.name}", color = GhostlyText, fontSize = 10.sp, lineHeight = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DarkButton("Confirm", onClick = { action.onConfirm(); onDismiss() }, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModInfoDialog(
    mod: Modification,
    isMissing: Boolean,
    onDismiss: () -> Unit,
    onToggleEnabled: () -> Unit,
    onSwitchSide: () -> Unit,
    onRemove: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(mod.name, color = TextForeground, fontSize = 12.sp)
            Text(mod.path, color = GhostlyText, fontSize = 10.sp, lineHeight = 14.sp)
            Text(
                if (isMissing) "✗ Directory not found" else "✓ Directory found",
                color = if (isMissing) GhostlyText else TextForeground,
                fontSize = 10.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DarkButton(
                    text = if (mod.enabled) "Disable" else "Enable",
                    onClick = onToggleEnabled,
                    modifier = Modifier.weight(1f)
                )
                DarkButton(
                    text = if (mod.side == ModSide.CLIENT) "→ Server" else "→ Client",
                    onClick = onSwitchSide,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DarkButton("Open", onClick = onOpenLocation, modifier = Modifier.weight(1f), enabled = !isMissing)
                DarkButton("Remove", onClick = onRemove, modifier = Modifier.weight(1f))
                DarkButton("Close", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModControlButton(label: String? = null, onClick: () -> Unit, content: (@Composable () -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(if (isPressed) SelectColor else ElementsBackground)
            .border(1.dp, SubSelectColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content()
        else Text(label ?: "", color = TextForeground, fontSize = 11.sp, lineHeight = 12.sp)
    }
}
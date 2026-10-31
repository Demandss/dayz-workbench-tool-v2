package com.github.demandss.dayz_workbench_tool.ui

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
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.ModListPanel
import com.github.demandss.dayz_workbench_tool.ui.components.PoweredByFooter
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File

/** A line of the filter menu: a tick when the filter is set, its name, and how many mods it stands for. */
@Composable
private fun FilterMenuItem(
    text: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(modifier = Modifier.widthIn(min = 150.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(14.dp)) {
                    if (checked) Text("✓", color = TextForeground, fontSize = 10.sp)
                }
                Text(text, color = TextForeground, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text(count.toString(), color = GhostlyText, fontSize = 10.sp)
            }
        },
        onClick = onClick,
        modifier = Modifier.background(FrameBackground).height(26.dp),
    )
}

private data class PendingAction(
    val title: String,
    val mods: List<Modification>,
    val onConfirm: () -> Unit,
)

@Composable
fun ModsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var filters by remember { mutableStateOf(ModFilters()) }
    var search by remember { mutableStateOf("") }
    var filtersMenuOpen by remember { mutableStateOf(false) }

    var selectedMods by remember { mutableStateOf(emptySet<Modification>()) }
    var selectionAnchor by remember { mutableStateOf<Modification?>(null) }
    var infoMod by remember { mutableStateOf<Modification?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    var missingPaths by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(state.mods.toList()) {
        missingPaths =
            withContext(Dispatchers.IO) {
                state.mods
                    .filter { !File(it.path).exists() }
                    .map { it.path }
                    .toSet()
            }
    }

    val visibleMods =
        remember(state.mods.toList(), filters, search, missingPaths) {
            filterMods(state.mods.toList(), filters, search, missingPaths)
        }

    LaunchedEffect(filters, search) {
        selectedMods = emptySet()
        selectionAnchor = null
    }

    fun handleSelect(
        mod: Modification,
        isShift: Boolean,
        isCtrl: Boolean,
    ) {
        when {
            isShift && selectionAnchor != null -> {
                val a = visibleMods.indexOfFirst { it.path == selectionAnchor!!.path && it.side == selectionAnchor!!.side }
                val b = visibleMods.indexOfFirst { it.path == mod.path && it.side == mod.side }
                if (a >= 0 && b >= 0) {
                    val lo = minOf(a, b)
                    val hi = maxOf(a, b)
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(FrameBackground)
                    .drawBehind {
                        drawLine(borderColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                    },
        ) {
            val backSource = remember { MutableInteractionSource() }
            val isBackPressed by backSource.collectIsPressedAsState()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .background(if (isBackPressed) SelectColor else FrameBackground)
                        .clickable(interactionSource = backSource, indication = null) { state.navigate(Screen.MAIN) }
                        .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.back), color = TextForeground, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))

            // Drops every filter and the search, and says how many mods are shown
            if (filters.isActive || search.isNotEmpty()) {
                val resetSource = remember { MutableInteractionSource() }
                val isResetPressed by resetSource.collectIsPressedAsState()
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .background(if (isResetPressed) SelectColor else FrameBackground)
                            .clickable(interactionSource = resetSource, indication = null) {
                                filters = ModFilters()
                                search = ""
                            }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.reset), color = TextForeground, fontSize = 10.sp)
                }
            }
            Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.mods_shown_x_x, visibleMods.size, state.mods.size),
                    color = GhostlyText,
                    fontSize = 10.sp,
                )
            }
        }
        HorizontalDivider(color = DarkenedBorder)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, SubSelectColor)
                        .background(FrameBackground)
                        .padding(start = 5.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 10.sp),
                singleLine = true,
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (search.isEmpty()) Text(stringResource(Res.string.search_mods), color = GhostlyText, fontSize = 10.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(4.dp))

            // One button instead of a row of switches: it is lit and counts the filters while some are set
            Box {
                val filtersSource = remember { MutableInteractionSource() }
                val isFiltersPressed by filtersSource.collectIsPressedAsState()
                val lit = filters.isActive || filtersMenuOpen
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .border(1.dp, if (lit) SelectColor else SubSelectColor)
                            .background(if (isFiltersPressed || lit) SelectColor else FrameBackground)
                            .clickable(interactionSource = filtersSource, indication = null) { filtersMenuOpen = true }
                            .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val title = stringResource(Res.string.filters)
                    Text(if (filters.isActive) "$title · ${filters.activeCount}" else title, color = TextForeground, fontSize = 10.sp)
                }
                DropdownMenu(
                    expanded = filtersMenuOpen,
                    onDismissRequest = { filtersMenuOpen = false },
                    modifier = Modifier.background(FrameBackground).border(1.dp, DarkenedBorder),
                ) {
                    // The menu stays open, so that several filters can be set one after another; the numbers are of the whole list
                    val all = state.mods
                    FilterMenuItem(
                        stringResource(Res.string.filter_client),
                        all.count { it.side == ModSide.CLIENT },
                        filters.side == ModSide.CLIENT,
                    ) { filters = filters.toggleSide(ModSide.CLIENT) }
                    FilterMenuItem(
                        stringResource(Res.string.filter_server),
                        all.count { it.side == ModSide.SERVER },
                        filters.side == ModSide.SERVER,
                    ) { filters = filters.toggleSide(ModSide.SERVER) }
                    HorizontalDivider(color = DarkenedBorder)
                    FilterMenuItem(stringResource(Res.string.enabled), all.count { it.enabled }, filters.enabled == true) {
                        filters = filters.toggleEnabled(true)
                    }
                    FilterMenuItem(stringResource(Res.string.disabled), all.count { !it.enabled }, filters.enabled == false) {
                        filters = filters.toggleEnabled(false)
                    }
                    HorizontalDivider(color = DarkenedBorder)
                    FilterMenuItem(
                        stringResource(Res.string.filter_workshop),
                        all.count { it.type == ModType.WORKSHOP },
                        filters.type == ModType.WORKSHOP,
                    ) { filters = filters.toggleType(ModType.WORKSHOP) }
                    FilterMenuItem(
                        stringResource(Res.string.local),
                        all.count { it.type == ModType.LOCAL },
                        filters.type == ModType.LOCAL,
                    ) { filters = filters.toggleType(ModType.LOCAL) }
                    HorizontalDivider(color = DarkenedBorder)
                    FilterMenuItem(
                        stringResource(Res.string.filter_missing),
                        all.count { it.path in missingPaths },
                        filters.onlyMissing,
                    ) { filters = filters.toggleMissing() }
                }
            }
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
                        text = { Text(stringResource(Res.string.enable), color = TextForeground, fontSize = 10.sp) },
                        onClick = {
                            dismiss()
                            val toEnable = liveMods.filter { !it.enabled }
                            pendingAction =
                                PendingAction(
                                    title = str(Res.string.enable_mods),
                                    mods = toEnable,
                                    onConfirm = {
                                        toEnable.forEach { state.toggleEnabled(it) }
                                        selectedMods = emptySet()
                                    },
                                )
                        },
                        modifier = Modifier.background(FrameBackground).height(28.dp),
                    )
                }
                if (showDisable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.disable), color = TextForeground, fontSize = 10.sp) },
                        onClick = {
                            dismiss()
                            val toDisable = liveMods.filter { it.enabled }
                            pendingAction =
                                PendingAction(
                                    title = str(Res.string.disable_mods),
                                    mods = toDisable,
                                    onConfirm = {
                                        toDisable.forEach { state.toggleEnabled(it) }
                                        selectedMods = emptySet()
                                    },
                                )
                        },
                        modifier = Modifier.background(FrameBackground).height(28.dp),
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.delete), color = TextForeground, fontSize = 10.sp) },
                    onClick = {
                        dismiss()
                        pendingAction =
                            PendingAction(
                                title = str(Res.string.remove_mods),
                                mods = liveMods,
                                onConfirm = {
                                    liveMods.forEach { state.removeMod(it) }
                                    selectedMods = emptySet()
                                },
                            )
                    },
                    modifier = Modifier.background(FrameBackground).height(28.dp),
                )
                if (targets.size == 1) {
                    val singleMod = liveMods.firstOrNull()
                    if (singleMod != null && File(singleMod.path).exists()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.open_location), color = TextForeground, fontSize = 10.sp) },
                            onClick = {
                                dismiss()
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        if (Desktop.isDesktopSupported()) {
                                            Desktop.getDesktop().open(File(singleMod.path))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.background(FrameBackground).height(28.dp),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.info), color = TextForeground, fontSize = 10.sp) },
                        onClick = {
                            infoMod = liveMods.firstOrNull()
                            dismiss()
                        },
                        modifier = Modifier.background(FrameBackground).height(28.dp),
                    )
                }
            },
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
                    pendingAction =
                        PendingAction(
                            title = if (live.enabled) str(Res.string.disable_mod) else str(Res.string.enable_mod),
                            mods = listOf(live),
                            onConfirm = { state.toggleEnabled(live) },
                        )
                }
            },
            onSwitchSide = {
                state.mods.find { it.path == mod.path && it.side == mod.side }?.let { live ->
                    infoMod = null
                    pendingAction =
                        PendingAction(
                            title = if (live.side == ModSide.CLIENT) str(Res.string.move_to_server) else str(Res.string.move_to_client),
                            mods = listOf(live),
                            onConfirm = { state.switchSide(live) },
                        )
                }
            },
            onRemove = {
                state.mods.find { it.path == mod.path && it.side == mod.side }?.let { live ->
                    infoMod = null
                    pendingAction =
                        PendingAction(
                            title = str(Res.string.remove_mod),
                            mods = listOf(live),
                            onConfirm = {
                                state.removeMod(live)
                                selectedMods = emptySet()
                            },
                        )
                }
            },
            onOpenLocation = {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(File(mod.path))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ConfirmDialog(
    action: PendingAction,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(280.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(action.title, color = TextForeground, fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                action.mods.forEach { mod ->
                    Text("• ${mod.name}", color = GhostlyText, fontSize = 10.sp, lineHeight = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DarkButton(str(Res.string.confirm), onClick = {
                    action.onConfirm()
                    onDismiss()
                }, modifier = Modifier.weight(1f))
                DarkButton(str(Res.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
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
            modifier =
                Modifier
                    .width(320.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(mod.name, color = TextForeground, fontSize = 12.sp)
            Text(mod.path, color = GhostlyText, fontSize = 10.sp, lineHeight = 14.sp)
            Text(
                if (isMissing) str(Res.string.directory_not_found) else str(Res.string.directory_found),
                color = if (isMissing) GhostlyText else TextForeground,
                fontSize = 10.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DarkButton(
                    text = if (mod.enabled) str(Res.string.disable) else str(Res.string.enable),
                    onClick = onToggleEnabled,
                    modifier = Modifier.weight(1f),
                )
                DarkButton(
                    text = if (mod.side == ModSide.CLIENT) str(Res.string.server) else str(Res.string.client),
                    onClick = onSwitchSide,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DarkButton(str(Res.string.open), onClick = onOpenLocation, modifier = Modifier.weight(1f), enabled = !isMissing)
                DarkButton(str(Res.string.delete), onClick = onRemove, modifier = Modifier.weight(1f))
                DarkButton(str(Res.string.close), onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModControlButton(
    label: String? = null,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .background(if (isPressed) SelectColor else ElementsBackground)
                .border(1.dp, SubSelectColor)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Text(label ?: "", color = TextForeground, fontSize = 11.sp, lineHeight = 12.sp)
        }
    }
}

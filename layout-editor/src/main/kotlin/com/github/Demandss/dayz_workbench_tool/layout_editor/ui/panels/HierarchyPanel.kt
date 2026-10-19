package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSession
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSource
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.hasBareCenter
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.widgetIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import java.util.UUID

private fun buildFlatList(
    node: EditorWidgetNode,
    depth: Int,
    expandedIds: Set<UUID>,
    result: MutableList<Pair<EditorWidgetNode, Int>>,
) {
    result.add(node to depth)
    if (expandedIds.contains(node.id)) {
        for (child in node.children) buildFlatList(child, depth + 1, expandedIds, result)
    }
}

@Composable
fun HierarchyPanel(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.activeFile
    val expandedIds = remember { mutableStateListOf<UUID>() }

    LaunchedEffect(doc?.path) {
        expandedIds.clear()
        doc?.root?.id?.let { expandedIds.add(it) }
    }

    Column(
        modifier
            .background(FrameBackground)
            .onGloballyPositioned { coords ->
                state.hierarchyBoundsInWindow = coords.boundsInWindow()
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(ElementsBackground)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hierarchy", color = GhostlyText, fontSize = 10.sp)
        }

        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет открытых файлов", color = GhostlyText, fontSize = 11.sp)
            }
            return@Column
        }

        val flatList =
            remember(doc.root, expandedIds.toSet()) {
                mutableListOf<Pair<EditorWidgetNode, Int>>().also {
                    buildFlatList(doc.root, 0, expandedIds.toHashSet(), it)
                }
            }

        val dragSrc = state.dragSession?.source
        val isDragging = dragSrc != null

        LazyColumn(Modifier.fillMaxSize()) {
            items(flatList, key = { (node, _) -> node.id.toString() }) { (node, depth) ->
                val isExpanded = expandedIds.contains(node.id)
                val isSelected = state.selectedIds.contains(node.id)
                val isDragTarget = isDragging && state.dragHoverNodeId == node.id
                val isDragSource = dragSrc is DragSource.FromHierarchy && dragSrc.nodeId == node.id

                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            state.hierarchyRowBounds[node.id] = coords.boundsInWindow()
                        },
                ) {
                    HierarchyRow(
                        node = node,
                        depth = depth,
                        isExpanded = isExpanded,
                        isSelected = isSelected,
                        isDragTarget = isDragTarget,
                        isDragSource = isDragSource,
                        isHidden = state.hiddenIds.contains(node.id),
                        isLocked = state.lockedIds.contains(node.id),
                        onToggleHidden = { state.toggleHidden(node.id) },
                        onToggleLocked = { state.toggleLocked(node.id) },
                        onToggleExpand = {
                            if (expandedIds.contains(node.id)) {
                                expandedIds.remove(node.id)
                            } else {
                                expandedIds.add(node.id)
                            }
                        },
                        onSelect = { shift ->
                            if (shift) {
                                if (state.selectedIds.contains(node.id)) {
                                    state.selectedIds.remove(node.id)
                                } else {
                                    state.selectedIds.add(node.id)
                                }
                            } else {
                                state.selectedIds.clear()
                                state.selectedIds.add(node.id)
                            }
                        },
                        onDragStart = {
                            state.dragSession = DragSession(DragSource.FromHierarchy(node.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HierarchyRow(
    node: EditorWidgetNode,
    depth: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    isDragTarget: Boolean,
    isDragSource: Boolean,
    isHidden: Boolean,
    isLocked: Boolean,
    onToggleHidden: () -> Unit,
    onToggleLocked: () -> Unit,
    onToggleExpand: () -> Unit,
    onSelect: (shift: Boolean) -> Unit,
    onDragStart: () -> Unit,
) {
    val bg =
        when {
            isDragTarget -> Color(0xFF1E3A5F)
            isSelected -> SelectColor
            else -> Color.Transparent
        }
    val borderMod =
        if (isDragTarget) {
            Modifier.border(1.dp, Color(0xFF4A9EFF))
        } else {
            Modifier
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .then(borderMod)
                .background(bg)
                .padding(start = (depth * 14).dp)
                .then(
                    if (isDragSource) Modifier.background(Color(0x40FFFFFF)) else Modifier,
                ).pointerInput(node.id) {
                    var pressPos = Offset.Zero
                    var pressed = false
                    var lastClickTime = 0L
                    var dragStarted = false

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue

                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (event.buttons.isPrimaryPressed && event.changes.none { it.isConsumed }) {
                                        pressPos = pos
                                        pressed = true
                                        dragStarted = false
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                PointerEventType.Move -> {
                                    if (pressed && !dragStarted) {
                                        val dist = (pos - pressPos).getDistance()
                                        if (dist > 8f) {
                                            dragStarted = true
                                            onDragStart()
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                PointerEventType.Release -> {
                                    if (pressed) {
                                        pressed = false
                                        if (!dragStarted) {
                                            val now = System.currentTimeMillis()
                                            val isDouble = now - lastClickTime < 400
                                            lastClickTime = now
                                            if (isDouble && node.children.isNotEmpty()) {
                                                onToggleExpand()
                                            } else if (!isDouble) {
                                                onSelect(event.keyboardModifiers.isShiftPressed)
                                            }
                                        }
                                        dragStarted = false
                                        event.changes.forEach { it.consume() }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = node.children.isNotEmpty(),
                    onClick = onToggleExpand,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (node.children.isNotEmpty()) {
                AppIconView(if (isExpanded) AppIcon.UI_DOWN else AppIcon.UI_RIGHT, size = 12.dp, tint = GhostlyText)
            }
        }

        AppIconView(widgetIcon(node.node.type), modifier = Modifier.padding(horizontal = 3.dp))

        Text(
            node.node.name.ifEmpty { if (depth == 0) "(root)" else "—" },
            color = if (isSelected) TextForeground else Color(0xCCCCCCCC),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp).weight(1f),
        )
        if (node.hasBareCenter()) AppIconView(AppIcon.UI_WARN, size = 13.dp, modifier = Modifier.padding(end = 2.dp))
        RowToggle(if (isHidden) AppIcon.UI_EYEOFF else AppIcon.UI_EYE, "Скрыть на холсте (только в редакторе)", isHidden, onToggleHidden)
        RowToggle(
            if (isLocked) AppIcon.UI_LOCK else AppIcon.UI_UNLOCK,
            "Заблокировать выбор на холсте (только в редакторе)",
            isLocked,
            onToggleLocked,
        )
    }
}

/** A small icon at the end of a hierarchy row. Shown dimmed until it is switched on. */
@Composable
private fun RowToggle(
    icon: AppIcon,
    hint: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    Tip(hint) {
        Box(
            Modifier
                .size(20.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                onClick()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(icon, size = 13.dp, tint = if (on) Color(0xFF4A9EFF) else GhostlyText)
        }
    }
}

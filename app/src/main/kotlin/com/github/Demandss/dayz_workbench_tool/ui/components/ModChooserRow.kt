package com.github.demandss.dayz_workbench_tool.ui.components

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.Modification
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.ui.pickDirectory
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ModChooserRow(
    side: ModSide,
    state: AppState,
) {
    var path by remember { mutableStateOf("") }
    var showTypeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth().height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = path,
            onValueChange = { path = it },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, SubSelectColor)
                    .background(FrameBackground)
                    .padding(start = 5.dp),
            textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
            singleLine = true,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
        Spacer(Modifier.width(3.dp))
        SmallFolderButton { showTypeDialog = true }
        Spacer(Modifier.width(3.dp))
        SmallIconButton("+") {
            if (path.isNotBlank()) {
                state.addMod(Modification(path = path.trim(), side = side))
                path = ""
            }
        }
    }

    if (showTypeDialog) {
        val localDir = path.takeIf { it.isNotBlank() } ?: state.config.workDrivePath.takeIf { it.isNotBlank() }
        val workshopDir =
            path.takeIf { it.isNotBlank() }
                ?: state.config.gamePath
                    .takeIf { it.isNotBlank() }
                    ?.let { File(it).parent + "\\!Workshop" }
        ModTypeDialog(
            onDismiss = { showTypeDialog = false },
            onLocal = {
                showTypeDialog = false
                scope.launch { pickDirectory(tr("Выберите локальный мод"), localDir)?.let { path = it } }
            },
            onWorkshop = {
                showTypeDialog = false
                scope.launch { pickDirectory(tr("Выберите мод из Workshop"), workshopDir)?.let { path = it } }
            },
        )
    }
}

@Composable
private fun ModTypeDialog(
    onDismiss: () -> Unit,
    onLocal: () -> Unit,
    onWorkshop: () -> Unit,
) {
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
            Text(tr("Выберите тип мода"), color = TextForeground, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(tr("Локальный"), onClick = onLocal, modifier = Modifier.weight(1f))
                DarkButton("Workshop", onClick = onWorkshop, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SmallFolderButton(onClick: () -> Unit) {
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
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextForeground, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SmallIconButton(
    label: String,
    onClick: () -> Unit,
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
        Text(label, color = TextForeground, fontSize = 11.sp, lineHeight = 12.sp)
    }
}

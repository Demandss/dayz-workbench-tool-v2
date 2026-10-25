package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.font.FontImporter
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.pickDirectory
import com.github.demandss.dayz_workbench_tool.ui.pickFile
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The sets of characters a font can be made with, as the importer names them. */
private val CHARSETS = listOf("default", "cyrillic", "latin", "ascii")

/** Makes the bitmap fonts of the game (`.fnt`, `.edds` and the family `.xml`) from a TrueType or OpenType file. */
@Composable
fun FontImportDialog(state: LayoutEditorState) {
    if (!state.fontImportOpen) return
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sizes by remember { mutableStateOf("12, 14, 16, 22, 28, 48") }
    var charset by remember { mutableStateOf("default") }
    var folder by remember { mutableStateOf(state.defaultFontFolder()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val parsedSizes =
        sizes
            .split(',', ';', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 6..200 }
            .distinct()
    val ready = source.isNotBlank() && name.isNotBlank() && folder.isNotBlank() && parsedSizes.isNotEmpty() && !busy

    fun create() {
        if (!ready) return
        busy = true
        message = null
        scope.launch {
            val outcome =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val font = FontImporter.loadFont(File(source))
                        FontImporter.write(font, name.trim(), parsedSizes, File(folder), FontImporter.charset(charset))
                    }
                }
            busy = false
            outcome
                .onSuccess {
                    state.forgetFonts()
                    val path = state.fontPathFor(File(folder), name.trim())
                    state.notify(tr("Шрифт создан: {0}", path))
                    state.fontImportOpen = false
                }.onFailure { message = tr("Не удалось создать шрифт: {0}", it.message.orEmpty()) }
        }
    }

    FlatDialog(
        title = tr("Импорт шрифта"),
        onDismiss = { state.fontImportOpen = false },
        onConfirm = ::create,
        width = 460.dp,
        buttons = {
            DarkButton(tr("Отмена"), onClick = { state.fontImportOpen = false }, muted = true, modifier = Modifier.width(80.dp))
            DarkButton(
                if (busy) tr("Создаю…") else tr("Создать"),
                onClick = ::create,
                enabled = ready,
                modifier = Modifier.width(100.dp),
                tooltip = tr("Сделать .fnt и .edds для каждого размера"),
            )
        },
    ) {
        Text(
            tr("Из .ttf или .otf делаются bitmap-шрифты игры: по файлу .fnt с атласом .edds на каждый размер и описание семейства .xml."),
            color = GhostlyText,
            fontSize = 11.sp,
        )
        PathRow(tr("Файл шрифта"), source, tr("Выбрать…"), { source = it }) {
            scope.launch {
                pickFile(tr("Выбрать файл шрифта"), "ttf;*.otf", "C:\\Windows\\Fonts")?.let {
                    source = it
                    if (name.isBlank()) name = File(it).nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_')
                }
            }
        }
        TextRow(tr("Имя"), name, tr("файлы называются имя + размер, например Roboto22.fnt")) { name = it }
        TextRow(tr("Размеры, пикселей"), sizes, tr("высота строки, от 6 до 200, через запятую")) { sizes = it }
        Text(tr("Символы"), color = GhostlyText, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (set in CHARSETS) {
                DarkToolButton(
                    when (set) {
                        "ascii" -> "ASCII"
                        "latin" -> tr("Латиница")
                        "cyrillic" -> tr("Латиница и кириллица")
                        else -> tr("Всё нужное")
                    },
                    { charset = set },
                    active = charset == set,
                    tooltip =
                        when (set) {
                            "ascii" -> tr("Только символы ASCII")
                            "latin" -> tr("Латиница с диакритикой, знаки и валюты")
                            "cyrillic" -> tr("Латиница, кириллица, ₽ и №")
                            else -> tr("Латиница, кириллица, знаки, валюты и стрелки")
                        },
                )
            }
        }
        PathRow(tr("Папка"), folder, tr("Выбрать…"), { folder = it }) {
            scope.launch { pickDirectory(tr("Папка для шрифтов"), folder.ifBlank { null })?.let { folder = it } }
        }
        message?.let { Text(it, color = Color(0xFFD06A6A), fontSize = 11.sp) }
    }
}

@Composable
private fun TextRow(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = GhostlyText, fontSize = 11.sp)
        InputBox(value, hint, onChange, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PathRow(
    label: String,
    value: String,
    button: String,
    onChange: (String) -> Unit,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = GhostlyText, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            InputBox(value, "", onChange, Modifier.weight(1f))
            DarkButton(button, onClick = onPick, modifier = Modifier.width(96.dp))
        }
    }
}

@Composable
private fun InputBox(
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = TextForeground, fontSize = 12.sp),
        cursorBrush = SolidColor(TextForeground),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = GhostlyText, fontSize = 12.sp, maxLines = 1)
                inner()
            }
        },
        modifier = modifier,
    )
}

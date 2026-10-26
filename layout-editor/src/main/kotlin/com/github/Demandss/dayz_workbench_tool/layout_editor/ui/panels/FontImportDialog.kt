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
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.resources.*
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
import org.jetbrains.compose.resources.stringResource
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
                    state.notify(str(Res.string.font_made_x, path))
                    state.fontImportOpen = false
                }.onFailure { message = str(Res.string.could_not_make_the_font_x, it.message.orEmpty()) }
        }
    }

    FlatDialog(
        title = stringResource(Res.string.import_a_font),
        onDismiss = { state.fontImportOpen = false },
        onConfirm = ::create,
        width = 460.dp,
        buttons = {
            DarkButton(
                stringResource(Res.string.cancel),
                onClick = { state.fontImportOpen = false },
                muted = true,
                modifier = Modifier.width(80.dp),
            )
            DarkButton(
                if (busy) stringResource(Res.string.making) else stringResource(Res.string.create),
                onClick = ::create,
                enabled = ready,
                modifier = Modifier.width(100.dp),
                tooltip = stringResource(Res.string.make_a_fnt_and_an_edds_for_each_size),
            )
        },
    ) {
        Text(
            stringResource(Res.string.a_ttf_or_otf_becomes_the_bitmap_fonts_of),
            color = GhostlyText,
            fontSize = 11.sp,
        )
        PathRow(stringResource(Res.string.font_file), source, stringResource(Res.string.choose), { source = it }) {
            scope.launch {
                pickFile(str(Res.string.choose_a_font_file), "ttf;*.otf", "C:\\Windows\\Fonts")?.let {
                    source = it
                    if (name.isBlank()) name = File(it).nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_')
                }
            }
        }
        TextRow(stringResource(Res.string.name), name, stringResource(Res.string.files_are_named_name_size_for_example)) { name = it }
        TextRow(stringResource(Res.string.sizes_pixels), sizes, stringResource(Res.string.height_of_a_line_6_to_200_separated_by)) {
            sizes =
                it
        }
        Text(stringResource(Res.string.characters), color = GhostlyText, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (set in CHARSETS) {
                DarkToolButton(
                    when (set) {
                        "ascii" -> "ASCII"
                        "latin" -> stringResource(Res.string.latin)
                        "cyrillic" -> stringResource(Res.string.latin_and_cyrillic)
                        else -> stringResource(Res.string.everything_usual)
                    },
                    { charset = set },
                    active = charset == set,
                    tooltip =
                        when (set) {
                            "ascii" -> stringResource(Res.string.ascii_characters_only)
                            "latin" -> stringResource(Res.string.latin_with_diacritics_signs_and)
                            "cyrillic" -> stringResource(Res.string.latin_cyrillic_and)
                            else -> stringResource(Res.string.latin_cyrillic_signs_currencies_and)
                        },
                )
            }
        }
        PathRow(stringResource(Res.string.folder), folder, stringResource(Res.string.choose), { folder = it }) {
            scope.launch { pickDirectory(str(Res.string.folder_for_the_fonts), folder.ifBlank { null })?.let { folder = it } }
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

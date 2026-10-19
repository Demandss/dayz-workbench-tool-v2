package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

private val SHORTCUTS: List<Pair<String, List<Pair<String, String>>>> =
    listOf(
        "Общие" to
            listOf(
                "Ctrl+Shift+P" to "Палитра команд",
                "Ctrl+P" to "Открыть файл по имени",
                "Ctrl+F" to "Найти виджет (в layout) или картинку (в ImageSet)",
                "Ctrl+B" to "Скрыть или показать браузер файлов",
                "Ctrl+Tab / Ctrl+Shift+Tab" to "Следующая / предыдущая вкладка",
                "Ctrl+1…9" to "Перейти на вкладку по номеру (9 — последняя)",
                "Ctrl+S" to "Сохранить",
                "Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z" to "Отменить / повторить",
                "Ctrl+W" to "Закрыть вкладку",
                "F3" to "Счётчик FPS",
                "F1" to "Эта справка",
            ),
        "Layout" to
            listOf(
                "Delete" to "Удалить выбранное",
                "Стрелки (Shift — крупнее)" to "Сдвинуть выбранное",
                "F2" to "Переименовать",
                "Ctrl+C / X / V" to "Копировать, вырезать, вставить",
                "Ctrl+D" to "Дублировать",
                "Ctrl+Alt+C / Ctrl+Alt+V" to "Копировать и вставить оформление",
                "Ctrl+G / Ctrl+Shift+G" to "Сгруппировать / разгруппировать",
                "Shift+A" to "Обернуть в WrapSpacer",
                "] / [" to "На слой вперёд / назад",
                "Ctrl+] / Ctrl+[" to "На передний / задний план",
                "Shift+1 / Shift+2" to "Вписать в окно / приблизить к выбранному",
                "Ctrl+= / Ctrl+- / Ctrl+0" to "Приблизить / отдалить / вписать",
            ),
        "ImageSet" to
            listOf(
                "Delete" to "Удалить область",
                "Стрелки (Shift — на 8 px)" to "Сдвинуть область",
                "Ctrl+D / Ctrl+A" to "Дублировать / выделить всё",
                "F2" to "Переименовать",
                "Shift+1 / Shift+2" to "Вписать в окно / к выбранному",
                "Ctrl+= / Ctrl+- / Ctrl+0" to "Приблизить / отдалить / вписать",
                "Пробел + перетаскивание" to "Двигать холст",
                "Ctrl + перетаскивание" to "Нарисовать новую область",
            ),
    )

/** F1: every shortcut of the editors. */
@Composable
fun ShortcutsDialog(state: LayoutEditorState) {
    if (!state.shortcutsOpen) return
    FlatDialog(
        title = "Горячие клавиши",
        onDismiss = { state.shortcutsOpen = false },
        onConfirm = { state.shortcutsOpen = false },
        width = 560.dp,
        buttons = {
            DarkButton("Закрыть", onClick = { state.shortcutsOpen = false }, modifier = Modifier.width(90.dp))
        },
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((section, rows) in SHORTCUTS) {
                Text(section.uppercase(), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                for ((keys, what) in rows) {
                    Row {
                        Text(keys, color = TextForeground, fontSize = 12.sp, modifier = Modifier.width(240.dp))
                        Text(what, color = GhostlyText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

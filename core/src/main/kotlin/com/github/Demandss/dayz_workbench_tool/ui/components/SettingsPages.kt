package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.strInEveryLanguage
import com.github.demandss.dayz_workbench_tool.resources.Res
import com.github.demandss.dayz_workbench_tool.resources.nothing_found
import com.github.demandss.dayz_workbench_tool.resources.search
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The pages of the settings window: a few groups across the top (Global, App, Layout Editor), under each a list of sections down
 * the left side with a search above it, and the settings of the section on the right. The settings are data ([SettingItem]), so
 * the search can find a single one among all the sections and show it where it is.
 */

/**
 * One setting. The search reads [label], [more] (the names of its section, for instance) and [words] (a name that is the same in
 * every language, like MCP) in every language, so that a word of any language finds it. [content] draws the row itself.
 */
class SettingItem(
    val label: StringResource,
    val more: List<StringResource> = emptyList(),
    val words: List<String> = emptyList(),
    val content: @Composable () -> Unit,
)

/** A list of settings that belong together, with the name the list shows. [id] stays the same in every language. */
class SettingsSection(
    val id: String,
    val title: String,
    val items: List<SettingItem>,
)

/** A tab of the window with its sections. */
class SettingsGroup(
    val title: String,
    val sections: List<SettingsSection>,
)

private val SIDEBAR_WIDTH = 112.dp

/**
 * [group] is the index of the open group and [section] the id of the open section of it (an empty or an unknown id opens the first).
 * The state is kept by the caller, so that something else can open a page, and the search text is kept here.
 */
@Composable
fun SettingsPages(
    groups: List<SettingsGroup>,
    group: Int,
    onGroup: (Int) -> Unit,
    section: String,
    onSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    // The text of a resource in every language, by its name: the settings are made again at every drawing, the texts are found once
    val index = remember { HashMap<String, String>() }
    val openGroup = groups.getOrElse(group) { groups.first() }
    val openSection = openGroup.sections.firstOrNull { it.id == section } ?: openGroup.sections.first()
    val text = query.trim().lowercase()

    Column(modifier) {
        DarkTabRow(
            tabs = groups.map { it.title },
            selectedIndex = groups.indexOf(openGroup),
            onTabSelected = {
                query = ""
                onGroup(it)
                onSection("")
            },
        )
        HorizontalDivider(color = DarkenedBorder)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(FrameBackground)) {
                SearchField(query) { query = it }
                HorizontalDivider(color = DarkenedBorder)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    openGroup.sections.forEach { entry ->
                        SectionButton(entry.title, selected = text.isEmpty() && entry === openSection) {
                            query = ""
                            onSection(entry.id)
                        }
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))
            key(if (text.isEmpty()) openSection.id else "search") {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (text.isEmpty()) {
                        openSection.items.forEach { it.content() }
                    } else {
                        SearchResults(groups, text, index) { groupIndex, sectionId ->
                            query = ""
                            onGroup(groupIndex)
                            onSection(sectionId)
                        }
                    }
                }
            }
        }
    }
}

/** The settings that [text] (lower case) names, each section of them under a line that opens it. */
@Composable
private fun SearchResults(
    groups: List<SettingsGroup>,
    text: String,
    index: MutableMap<String, String>,
    open: (Int, String) -> Unit,
) {
    var found = false
    groups.forEachIndexed { groupIndex, group ->
        group.sections.forEach { entry ->
            val all = group.title.lowercase().contains(text) || entry.title.lowercase().contains(text)
            val hits = if (all) entry.items else entry.items.filter { it.matches(text, index) }
            if (hits.isEmpty()) return@forEach
            found = true
            Text(
                text = "${group.title} › ${entry.title}",
                color = GhostlyText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 5.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            open(groupIndex, entry.id)
                        },
            )
            hits.forEach { it.content() }
        }
    }
    if (!found) Text(stringResource(Res.string.nothing_found), color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(5.dp))
}

private fun SettingItem.matches(
    text: String,
    index: MutableMap<String, String>,
): Boolean {
    val names =
        (listOf(label) + more).map { resource ->
            index.getOrPut(resource.key) { strInEveryLanguage(resource).joinToString("\n").lowercase() }
        }
    return names.any { it.contains(text) } || words.any { it.lowercase().contains(text) }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(20.dp)
                .border(1.dp, SubSelectColor)
                .background(FrameBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
            cursorBrush = SolidColor(TextForeground),
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(stringResource(Res.string.search), color = GhostlyText, fontSize = 11.sp)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Text(
                "×",
                color = GhostlyText,
                fontSize = 13.sp,
                modifier =
                    Modifier
                        .padding(horizontal = 5.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange("") },
            )
        }
    }
}

@Composable
private fun SectionButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    when {
                        selected -> SelectColor
                        hovered -> ElementsBackground
                        else -> Color.Transparent
                    },
                ).hoverable(source)
                .clickable(interactionSource = source, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(if (selected) SubSelectColor else Color.Transparent))
        Text(
            text = title,
            color = TextForeground,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        )
    }
}
